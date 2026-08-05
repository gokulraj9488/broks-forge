"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Brain, Compass, FileText, Network, RotateCcw, Send, Sparkles, X } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { ForgeGraph } from "@/components/platform/forge-graph";
import { InfoButton } from "@/components/platform/info-button";
import { BrokAnswerView } from "@/components/brok/brok-answer";
import { BrokInvestigation } from "@/components/brok/brok-investigation";
import { BrokRefGroup, BrokRefRow } from "@/components/brok/brok-ref";
import {
  useAskBrok,
  useBrokBriefs,
  useBrokContext,
  useBrokSuggestions,
  useRequestBrief,
} from "@/lib/hooks/use-brok";
import type { BrokAnswer, BrokTurn } from "@/lib/api/brok";
import { substrateMeta } from "@/lib/substrate";
import { cn, formatDateTime } from "@/lib/utils";

/**
 * Brok — the Engineering Partner's workspace.
 *
 * This is deliberately not a chat page. The conversation is one panel of a workspace whose other panels —
 * engineering context, evidence, referenced artifacts, knowledge, decisions, evaluations, AI Git revisions,
 * engineering memory and the Forge Graph — stay synchronized with whatever Brok last reasoned about. Focus is
 * what binds them: clicking a referenced record focuses the conversation on it, clicking a graph node focuses
 * the conversation on it, and the next question is asked about it. One screen, one subject, no hunting.
 *
 * The thread lives here rather than on the server, and travels back with each question as `history`. That is
 * what lets an engineer ask "show me the evidence" and then "compare it with v7" without ever restating the
 * subject — while keeping every answer independently reproducible, because the context that mattered is
 * explicit in the request rather than hidden in server-side session state.
 */
interface Turn {
  id: number;
  question: string;
  answer: BrokAnswer | null;
  error: string | null;
  /** The focus the question was asked with, so a retry reproduces it exactly. */
  askedWith: string | null;
  /** Briefs are written, not answered — their in-flight trace says so. */
  brief?: boolean;
}

/** How much of the conversation travels with each question. Enough to carry a subject, not a transcript. */
const HISTORY_DEPTH = 8;

export function BrokWorkspace({
  organizationId,
  projectId,
  initialFocus,
  initialQuestion,
}: {
  organizationId: string;
  projectId: string;
  initialFocus?: string | null;
  initialQuestion?: string | null;
}) {
  const [thread, setThread] = useState<Turn[]>([]);
  const [focus, setFocus] = useState<string | null>(initialFocus ?? null);
  const [input, setInput] = useState("");
  const nextTurnId = useRef(1);
  const bottomRef = useRef<HTMLDivElement>(null);
  const composerRef = useRef<HTMLTextAreaElement>(null);

  const ask = useAskBrok(organizationId);
  const requestBrief = useRequestBrief(organizationId);
  const { data: suggestions } = useBrokSuggestions(organizationId, projectId, focus ?? undefined);
  const { data: briefs } = useBrokBriefs(organizationId, projectId);
  const { data: context } = useBrokContext(organizationId, projectId, focus ?? undefined);

  const latest = useMemo(
    () => [...thread].reverse().find((t) => t.answer)?.answer ?? null,
    [thread],
  );
  const busy = ask.isPending || requestBrief.isPending;

  /** The conversation so far, in the shape Brok needs to inherit a subject. */
  const historyOf = useCallback(
    (turns: Turn[]): BrokTurn[] =>
      turns
        .filter((t) => !!t.answer)
        .slice(-HISTORY_DEPTH)
        .map((t) => ({
          question: t.question,
          intent: t.answer!.intent,
          focus: t.answer!.context.focus?.id ?? t.askedWith,
        })),
    [],
  );

  const runAsk = useCallback(
    async (question: string, withFocus?: string | null) => {
      const trimmed = question.trim();
      if (!trimmed) {
        return;
      }
      const effectiveFocus = withFocus !== undefined ? withFocus : focus;
      const id = nextTurnId.current++;
      let history: BrokTurn[] = [];
      setThread((t) => {
        history = historyOf(t);
        return [...t, { id, question: trimmed, answer: null, error: null, askedWith: effectiveFocus }];
      });
      try {
        const answer = await ask.mutateAsync({
          question: trimmed,
          projectId,
          focus: effectiveFocus,
          history,
        });
        setThread((t) => t.map((x) => (x.id === id ? { ...x, answer } : x)));
        if (answer.context.focus?.id) {
          setFocus(answer.context.focus.id);
        }
      } catch {
        setThread((t) =>
          t.map((x) =>
            x.id === id
              ? { ...x, error: "Brok could not read the engineering record for that question." }
              : x,
          ),
        );
      } finally {
        composerRef.current?.focus();
      }
    },
    [ask, focus, historyOf, projectId],
  );

  const runBrief = useCallback(
    async (kind: string, title: string) => {
      const id = nextTurnId.current++;
      setThread((t) => [
        ...t,
        { id, question: title, answer: null, error: null, askedWith: null, brief: true },
      ]);
      try {
        const answer = await requestBrief.mutateAsync({ kind, projectId });
        setThread((t) => t.map((x) => (x.id === id ? { ...x, answer } : x)));
      } catch {
        setThread((t) =>
          t.map((x) => (x.id === id ? { ...x, error: "That brief could not be written." } : x)),
        );
      }
    },
    [projectId, requestBrief],
  );

  const retry = useCallback(
    (turn: Turn) => {
      setThread((t) => t.filter((x) => x.id !== turn.id));
      void runAsk(turn.question, turn.askedWith);
    },
    [runAsk],
  );

  // A deep link ("ask Brok about this evaluation") runs its question once, on arrival.
  const started = useRef(false);
  useEffect(() => {
    if (!started.current && initialQuestion) {
      started.current = true;
      void runAsk(initialQuestion, initialFocus ?? null);
    }
  }, [initialFocus, initialQuestion, runAsk]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [thread]);

  // Escape drops the subject — the fastest way back to asking about the whole workspace.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && focus) {
        setFocus(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [focus]);

  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_21rem]">
      {/* ---------------- Conversation ---------------- */}
      <div className="min-w-0 space-y-4">
        <FocusStrip context={context ?? null} onClear={() => setFocus(null)} />

        {thread.length === 0 ? (
          <OpeningState
            briefs={briefs ?? []}
            suggestions={suggestions ?? []}
            onBrief={runBrief}
            onAsk={(q, f) => void runAsk(q, f)}
          />
        ) : (
          <div className="space-y-6">
            {thread.map((turn) => (
              <div key={turn.id} className="space-y-3 duration-300 animate-in fade-in slide-in-from-bottom-1">
                <div className="flex justify-end">
                  <p className="max-w-[85%] rounded-2xl rounded-br-sm bg-muted px-3.5 py-2 text-sm text-foreground">
                    {turn.question}
                  </p>
                </div>
                {turn.answer ? (
                  <BrokAnswerView
                    organizationId={organizationId}
                    answer={turn.answer}
                    activeFocusId={focus}
                    onFocus={setFocus}
                    onAsk={(q, f) => void runAsk(q, f)}
                  />
                ) : turn.error ? (
                  <div className="flex flex-wrap items-center gap-3 rounded-lg border border-border bg-muted/30 px-3 py-2.5">
                    <p className="text-sm text-muted-foreground">{turn.error}</p>
                    <button
                      type="button"
                      onClick={() => retry(turn)}
                      className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1 text-xs font-medium text-foreground transition-colors hover:border-primary/50"
                    >
                      <RotateCcw className="h-3 w-3" />
                      Try again
                    </button>
                  </div>
                ) : (
                  <BrokInvestigation
                    question={turn.question}
                    brief={turn.brief}
                    subjectLabel={
                      turn.askedWith && context?.focus?.id === turn.askedWith
                        ? context.focus.label
                        : null
                    }
                  />
                )}
              </div>
            ))}
            <div ref={bottomRef} />
          </div>
        )}

        <Composer
          composerRef={composerRef}
          value={input}
          busy={busy}
          onChange={setInput}
          onSubmit={() => {
            const q = input;
            setInput("");
            void runAsk(q);
          }}
        />
      </div>

      {/* ---------------- Engineering context ---------------- */}
      <aside className="space-y-4 lg:sticky lg:top-4 lg:self-start">
        <Card>
          <CardContent className="space-y-3 p-3.5">
            <div className="flex items-center justify-between gap-2">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <Compass className="h-3 w-3" />
                Engineering context
              </p>
              <InfoButton feature="brok" />
            </div>
            <p className="text-xs text-foreground">{context?.scope ?? "Resolving…"}</p>
            {context?.focus ? (
              <BrokRefRow organizationId={organizationId} refItem={context.focus} active />
            ) : (
              <p className="text-[11px] text-muted-foreground/80">
                Nothing is in focus. Click a record or a graph node and the conversation follows it.
              </p>
            )}
          </CardContent>
        </Card>

        {latest && latest.memory.length > 0 && (
          <Card>
            <CardContent className="space-y-2.5 p-3.5">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                <Brain className="h-3 w-3" />
                Engineering memory
              </p>
              {latest.memory.map((m) => (
                <div key={m.decisionId} className="space-y-0.5 border-l border-border pl-2.5">
                  <p className="text-xs font-medium text-foreground">{m.question}</p>
                  <p className="text-[11px] text-muted-foreground">{m.answer}</p>
                  <p className="text-[10px] text-muted-foreground/70">{formatDateTime(m.at)}</p>
                </div>
              ))}
            </CardContent>
          </Card>
        )}

        {latest && (
          <Card>
            <CardContent className="space-y-4 p-3.5">
              {/* The evidence panel is the constitutional centre of the rail: what the last answer read. */}
              <BrokRefGroup
                organizationId={organizationId}
                title="Evidence"
                refs={latest.evidence}
                onFocus={setFocus}
                activeId={focus}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Artifacts"
                refs={latest.references.artifacts}
                onFocus={setFocus}
                activeId={focus}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Evaluations"
                refs={latest.references.evaluations}
                onFocus={setFocus}
                activeId={focus}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Decisions"
                refs={latest.references.decisions}
                onFocus={setFocus}
                activeId={focus}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="Knowledge"
                refs={latest.references.knowledge}
                onFocus={setFocus}
                activeId={focus}
              />
              <BrokRefGroup
                organizationId={organizationId}
                title="AI Git revisions"
                refs={latest.references.revisions}
                onFocus={setFocus}
                activeId={focus}
              />
              {latest.evidence.length === 0 &&
                latest.references.artifacts.length === 0 &&
                latest.references.evaluations.length === 0 &&
                latest.references.decisions.length === 0 &&
                latest.references.knowledge.length === 0 &&
                latest.references.revisions.length === 0 && (
                  <p className="text-[11px] text-muted-foreground/80">
                    That answer touched no records — which is itself the finding.
                  </p>
                )}
            </CardContent>
          </Card>
        )}

        <div className="space-y-1.5">
          <p className="flex items-center gap-1.5 px-0.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
            <Network className="h-3 w-3" />
            Graph context
          </p>
          <ForgeGraph
            organizationId={organizationId}
            compact
            height={240}
            focusNodeId={latest?.context.graphNodeIds?.[0] ?? focus ?? undefined}
            onNodeSelect={setFocus}
          />
          <p className="px-0.5 text-[11px] text-muted-foreground/80">
            Selecting a node moves the conversation with it.
          </p>
        </div>
      </aside>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

function FocusStrip({
  context,
  onClear,
}: {
  context: { focus: { id: string; label: string; type: string } | null } | null;
  onClear: () => void;
}) {
  if (!context?.focus) {
    return null;
  }
  const meta = substrateMeta(context.focus.type);
  const Icon = meta.icon;
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-muted/30 px-3 py-2">
      <span className="text-[11px] uppercase tracking-wide text-muted-foreground">Asking about</span>
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-foreground">
        <Icon className={cn("h-3.5 w-3.5", meta.color)} />
        {context.focus.label}
      </span>
      <button
        type="button"
        onClick={onClear}
        title="Ask about the whole workspace instead (Esc)"
        className="ml-auto inline-flex items-center gap-1 text-[11px] text-muted-foreground transition-colors hover:text-foreground"
      >
        <X className="h-3 w-3" />
        Clear focus
      </button>
    </div>
  );
}

function OpeningState({
  briefs,
  suggestions,
  onBrief,
  onAsk,
}: {
  briefs: { kind: string; title: string; summary: string; available: boolean }[];
  suggestions: { question: string; rationale: string; focus: string | null }[];
  onBrief: (kind: string, title: string) => void;
  onAsk: (question: string, focus?: string | null) => void;
}) {
  return (
    <div className="space-y-5">
      <div className="rounded-xl border border-border bg-muted/20 p-4">
        <p className="flex items-center gap-2 text-sm font-medium text-foreground">
          <Sparkles className="h-4 w-4 text-primary" />
          Brok — your engineering partner
        </p>
        <p className="mt-1 text-xs text-muted-foreground">
          Ask about your system and the answer is read from your engineering record — evaluations, promotions,
          evidence and the relationships between them. Every statement says how it is known, every
          recommendation carries the evidence behind it, and each answer continues into the workflow it came
          from. When the record cannot answer, Brok says so instead of guessing.
        </p>
      </div>

      {suggestions.length > 0 && (
        <section className="space-y-2">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
            Worth asking now
          </p>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {suggestions.map((s) => (
              <button
                key={s.question}
                type="button"
                onClick={() => onAsk(s.question, s.focus)}
                className="rounded-lg border border-border bg-background p-3 text-left transition-colors hover:border-primary/40"
              >
                <p className="text-xs font-medium text-foreground">{s.question}</p>
                <p className="mt-0.5 text-[11px] text-muted-foreground">{s.rationale}</p>
              </button>
            ))}
          </div>
        </section>
      )}

      {briefs.length > 0 && (
        <section className="space-y-2">
          <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
            <FileText className="h-3 w-3" />
            Brok briefs
          </p>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {briefs.map((b) => (
              <button
                key={b.kind}
                type="button"
                onClick={() => onBrief(b.kind, b.title)}
                className={cn(
                  "rounded-lg border p-3 text-left transition-colors",
                  b.available
                    ? "border-border bg-background hover:border-primary/40"
                    : "border-dashed border-border bg-muted/20",
                )}
              >
                <p className="text-xs font-medium text-foreground">{b.title}</p>
                <p className="mt-0.5 text-[11px] text-muted-foreground">{b.summary}</p>
              </button>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function Composer({
  composerRef,
  value,
  busy,
  onChange,
  onSubmit,
}: {
  composerRef: React.RefObject<HTMLTextAreaElement | null>;
  value: string;
  busy: boolean;
  onChange: (value: string) => void;
  onSubmit: () => void;
}) {
  return (
    <div className="sticky bottom-0 -mx-1 bg-background/95 px-1 pb-1 pt-2 backdrop-blur">
      <div className="flex items-end gap-2 rounded-xl border border-border bg-background p-2 focus-within:border-primary/40">
        <Textarea
          ref={composerRef}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              if (!busy) {
                onSubmit();
              }
            }
          }}
          rows={2}
          maxLength={500}
          aria-label="Ask Brok an engineering question"
          placeholder="Ask Brok — why something failed, what changed, whether to promote it, what to do next…"
          className="min-h-[2.5rem] resize-none border-0 bg-transparent p-1.5 text-sm shadow-none focus-visible:ring-0"
        />
        <button
          type="button"
          onClick={onSubmit}
          disabled={busy || value.trim().length === 0}
          className="inline-flex h-9 shrink-0 items-center gap-1.5 rounded-lg bg-primary px-3 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-40"
        >
          {busy ? <Spinner className="h-3.5 w-3.5" /> : <Send className="h-3.5 w-3.5" />}
          Ask
        </button>
      </div>
      <p className="mt-1 px-1 text-[10px] text-muted-foreground/70">
        Enter to ask · Shift+Enter for a new line · Esc clears the subject
      </p>
    </div>
  );
}
