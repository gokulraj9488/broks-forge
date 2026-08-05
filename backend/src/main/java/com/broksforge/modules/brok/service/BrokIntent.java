package com.broksforge.modules.brok.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The engineering questions Brok can actually answer from the record — and, just as importantly, the
 * boundary of what it will attempt.
 *
 * <p>Intent resolution is deterministic phrase scoring rather than a language model. That is a deliberate
 * constitutional choice: an intent classifier that can be wrong in unbounded ways would let Brok answer
 * a question the engineer did not ask, which is a subtler and more damaging form of fabrication than an
 * outright wrong fact. Here, either a question maps onto a question the engineering record can answer, or the
 * Brok says so and offers the questions it can answer instead.
 *
 * <p>Weights are explicit rather than derived from phrase length so that a distinctive engineering phrase
 * ("remain unsupported") always beats an incidental one ("engineering knowledge") that happens to appear in
 * the same sentence.
 */
public enum BrokIntent {

    /** "Why did yesterday's deployment fail?", "what broke?" */
    FAILURE_EXPLAIN("failure.explain", Map.ofEntries(
            Map.entry("deployment fail", 50), Map.entry("deployment failed", 50),
            Map.entry("deploy fail", 45), Map.entry("release fail", 45),
            Map.entry("the deployment", 30), Map.entry("what broke", 45),
            Map.entry("what went wrong", 45), Map.entry("why did it fail", 45),
            Map.entry("did it fail", 30), Map.entry("is failing", 30), Map.entry("keeps failing", 40))),

    /** "Explain this execution graph.", "Why is this graph red?" */
    EXECUTION_EXPLAIN("execution.explain", Map.of(
            "execution graph", 55, "failure graph", 50, "graph red", 55,
            "why is this graph", 45, "chain break", 40, "chain broke", 40,
            "where it broke", 40, "explain this graph", 45)),

    /** "Explain this evaluation." */
    EVALUATION_EXPLAIN("evaluation.explain", Map.of(
            "explain this evaluation", 55, "explain the evaluation", 55, "explain this eval", 50,
            "about this evaluation", 35, "how did this evaluation", 40, "evaluation result", 35)),

    /**
     * "Has this happened before?" — the question that turns a diagnosis into a lookup. A failure with a
     * precedent arrives with its own resolution attached: what broke last time, what the team decided,
     * and what the record says that decision was worth.
     */
    HISTORY("history.similar", Map.ofEntries(
            Map.entry("has this happened before", 60), Map.entry("happened before", 55),
            Map.entry("have we seen this", 55), Map.entry("seen this before", 55),
            Map.entry("is this new", 45), Map.entry("precedent", 50),
            Map.entry("similar failure", 50), Map.entry("similar issue", 45),
            Map.entry("happened again", 50), Map.entry("recurring", 45),
            Map.entry("failed like this before", 60), Map.entry("first time this", 40))),

    /** "Why was Prompt v7 promoted?" */
    PROMOTION_RATIONALE("promotion.rationale", Map.of(
            "was promoted", 50, "promoted", 35, "promotion", 30, "why promote", 45,
            "why did we promote", 50, "reason for promoting", 50)),

    /** "Should I promote this?" — the decision Brok is most often asked to stand behind. */
    PROMOTION_ADVICE("promotion.advice", Map.of(
            "should i promote", 60, "should we promote", 60, "ready to promote", 55,
            "can i promote", 55, "promote this", 50, "promote it", 50,
            "worth promoting", 50, "safe to promote", 55)),

    /** "Show me the evidence." — the natural second turn of almost every investigation. */
    EVIDENCE_SHOW("evidence.show", Map.of(
            "show me the evidence", 55, "show the evidence", 55, "inspect the evidence", 55,
            "inspect evidence", 50, "what evidence", 45, "the evidence", 40,
            "see the evidence", 50)),

    /** "Open the graph." — a navigation turn that still answers before it moves you. */
    GRAPH_VIEW("graph.view", Map.of(
            "open the graph", 55, "open the forge graph", 60, "show the graph", 55,
            "view the graph", 55, "see it in the graph", 55, "in the forge graph", 50,
            "the graph", 20)),

    /** "What was the reasoning?" — Engineering Memory, the durable why. */
    MEMORY_WHY("memory.why", Map.of(
            "what was the reasoning", 60, "why did we change", 55, "why is it like this", 55,
            "engineering memory", 55, "what do we remember", 55, "the reasoning behind", 50,
            "why was it changed", 55)),

    /** "Should I rollback Prompt v8?" */
    ROLLBACK_ADVICE("rollback.advice", Map.of(
            "rollback", 50, "roll back", 50, "should i revert", 50, "revert to", 40,
            "go back to the previous", 45)),

    /** "What changed between these revisions?" */
    REVISION_DIFF("revision.diff", Map.of(
            "changed between", 55, "difference between", 55, "diff between", 55,
            "between these revisions", 55, "between the revisions", 55, "compare revisions", 55,
            "compare", 30, "diff", 30)),

    /** "Which evaluations support this decision?" */
    DECISION_EVIDENCE("decision.evidence", Map.of(
            "evaluations support", 55, "support this decision", 55, "supports this decision", 55,
            "evidence for this", 45, "evidence behind", 45, "what supports", 40, "backs this", 40)),

    /** "Show every artifact affected by this dataset." */
    IMPACT("impact.of", Map.of(
            "affected by", 50, "artifacts affected", 50, "affected artifacts", 50, "what does this affect", 50,
            "blast radius", 50, "downstream", 40, "impact of", 40, "depends on", 35, "affects", 30)),

    /** "Which prompt has the highest engineering risk?", "What is the biggest engineering risk today?" */
    RISK_RANKING("risk.ranking", Map.of(
            "highest risk", 55, "biggest risk", 55, "highest engineering risk", 60,
            "biggest engineering risk", 60, "riskiest", 50, "most risk", 45, "engineering risk", 35,
            "most dangerous", 40)),

    /** "What engineering knowledge exists about hallucinations?" */
    KNOWLEDGE_TOPIC("knowledge.topic", Map.of(
            "knowledge about", 45, "knowledge exists", 45, "what do we know", 45,
            "what we know about", 45, "engineering knowledge", 20, "anything about", 35)),

    /** "Summarize everything that happened this week.", "What changed overnight?" */
    PERIOD_SUMMARY("period.summary", Map.ofEntries(
            Map.entry("changed overnight", 55), Map.entry("overnight", 45), Map.entry("summarize", 45),
            Map.entry("summary of", 40), Map.entry("this week", 45), Map.entry("last week", 45),
            Map.entry("this month", 40), Map.entry("what happened", 40), Map.entry("happened today", 45),
            Map.entry("since yesterday", 45), Map.entry("recent activity", 40), Map.entry("summarize yesterday", 55),
            Map.entry("what changed", 35))),

    /** "Which provider causes the most failures?" */
    PROVIDER_FAILURES("provider.failures", Map.of(
            "provider causes", 55, "provider fail", 50, "providers fail", 50,
            "which provider", 45, "provider is failing", 50, "provider responsible", 50)),

    /** "What should my team work on next?" */
    NEXT_WORK("next.work", Map.of(
            "work on next", 55, "should my team", 50, "recommend next", 50, "next actions", 45, "what should i work", 50, "what next", 40,
            "focus on next", 50, "prioriti", 40, "where should we start", 50)),

    /** "Why did latency increase?" */
    LATENCY("latency.change", Map.of(
            "latency", 50, "slower", 40, "response time", 45, "took longer", 40,
            "speed", 30, "p95", 35)),

    /** Quality is never reported without its price (L-41); spend is a first-class engineering question. */
    COST("cost.change", Map.of(
            "cost", 45, "spend", 40, "expensive", 40, "how much are we paying", 55,
            "token usage", 40, "budget", 30)),

    /** "What engineering decisions remain unsupported?" */
    UNSUPPORTED_DECISIONS("decisions.unsupported", Map.of(
            "remain unsupported", 60, "unsupported", 50, "without evidence", 45,
            "no evidence", 40, "unproven decision", 50, "unsupported decisions", 60)),

    /** "Show contradictions in our engineering knowledge." */
    CONTRADICTIONS("knowledge.contradictions", Map.of(
            "contradiction", 60, "contradictions", 60, "conflicting", 45, "disagree", 40,
            "inconsistent", 40, "in tension", 40, "does not add up", 45)),

    /** "What investigations are still incomplete?" */
    INCOMPLETE_INVESTIGATIONS("investigations.incomplete", Map.of(
            "investigation", 50, "investigations", 50, "incomplete", 45, "still running", 45,
            "unfinished", 45, "never finished", 45, "left open", 45)),

    /** "How is my system doing?" — the same reading the Engineering Brief opens with. */
    SYSTEM_STATE("system.state", Map.of(
            "how is my", 40, "how are we doing", 45, "system health", 50, "how healthy", 45,
            "overall health", 50, "state of", 35, "how is the system", 50)),

    /** "Explain this prompt.", "What is X?" — the universal artifact question. */
    ARTIFACT_EXPLAIN("artifact.explain", Map.of(
            "tell me about", 40, "explain", 25, "what is", 25, "describe", 25, "who uses", 35)),

    /** Nothing in the record answers this. Brok says so rather than improvising. */
    UNKNOWN("unknown", Map.of());

    /** Below this score the question is not confidently about anything the record can answer. */
    private static final int THRESHOLD = 25;

    private final String key;
    private final Map<String, Integer> phrases;

    BrokIntent(String key, Map<String, Integer> phrases) {
        this.key = key;
        this.phrases = phrases;
    }

    /** The stable wire value, e.g. {@code "rollback.advice"}. */
    public String key() {
        return key;
    }

    /**
     * Resolves a question to the single intent the record can best answer. Ties break toward the intent
     * declared first, which orders the most specific engineering questions ahead of the general ones.
     */
    public static BrokIntent resolve(String question) {
        if (question == null || question.isBlank()) {
            return UNKNOWN;
        }
        String text = question.toLowerCase(Locale.ROOT);
        BrokIntent best = UNKNOWN;
        int bestScore = 0;
        for (BrokIntent intent : values()) {
            int score = intent.score(text);
            if (score > bestScore) {
                bestScore = score;
                best = intent;
            }
        }
        return bestScore >= THRESHOLD ? best : UNKNOWN;
    }

    private int score(String text) {
        int score = 0;
        for (Map.Entry<String, Integer> phrase : phrases.entrySet()) {
            if (text.contains(phrase.getKey())) {
                score += phrase.getValue();
            }
        }
        return score;
    }

    /** The questions offered when Brok cannot answer — real questions, not a generic apology. */
    public static List<String> answerable() {
        return List.of(
                "What is the biggest engineering risk right now?",
                "What should my team work on next?",
                "Has this happened before?",
                "Summarize what happened this week.",
                "Which provider causes the most failures?",
                "What engineering decisions remain unsupported?",
                "Show contradictions in our engineering knowledge.",
                "What investigations are still incomplete?");
    }
}
