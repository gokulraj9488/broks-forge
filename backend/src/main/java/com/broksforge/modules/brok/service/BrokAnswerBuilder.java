package com.broksforge.modules.brok.service;

import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAction;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAnswer;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokContext;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokFollowUp;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokImpact;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokMemory;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokRecommendation;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokRef;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokReferences;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokStatement;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokVerdict;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles one answer so that every composer produces the same constitutional shape.
 *
 * <p>The builder enforces the narrative by construction: an answer must have a verdict, reasoning steps each
 * carry their own epistemic status, references are de-duplicated and partitioned into the panels the workspace
 * renders, and the graph-context node ids are collected automatically from whatever the answer actually
 * touched — so the graph beside the conversation is always showing the same subgraph the answer reasoned over,
 * with no composer having to remember to keep them in sync.
 */
final class BrokAnswerBuilder {

    private final String question;
    private final String intentKey;
    private final String kind;

    private BrokVerdict verdict;
    private BrokImpact impact;
    private final List<BrokStatement> reasoning = new ArrayList<>();
    private final Map<String, BrokRef> evidence = new LinkedHashMap<>();
    private final Map<String, BrokRef> artifacts = new LinkedHashMap<>();
    private final Map<String, BrokRef> knowledge = new LinkedHashMap<>();
    private final Map<String, BrokRef> decisions = new LinkedHashMap<>();
    private final Map<String, BrokRef> evaluations = new LinkedHashMap<>();
    private final Map<String, BrokRef> revisions = new LinkedHashMap<>();
    private final Map<String, BrokMemory> memory = new LinkedHashMap<>();
    private final List<BrokRecommendation> recommendations = new ArrayList<>();
    private final List<BrokFollowUp> followUps = new ArrayList<>();

    private BrokAnswerBuilder(String question, String intentKey, String kind) {
        this.question = question;
        this.intentKey = intentKey;
        this.kind = kind;
    }

    static BrokAnswerBuilder answer(String question, BrokIntent intent) {
        return new BrokAnswerBuilder(question, intent.key(), "question");
    }

    /** A brief names itself ("brief.incident") so a client can tell one brief from another. */
    static BrokAnswerBuilder brief(String title, String briefKind) {
        return new BrokAnswerBuilder(title, "brief." + briefKind, "brief");
    }

    // ---- the verdict: what happened, and how sure we are ----

    BrokAnswerBuilder verdict(String state, String headline, String consequence, String status,
                                 String confidence, String basis) {
        this.verdict = new BrokVerdict(state, headline, consequence, status, confidence, basis);
        return this;
    }

    /** A derived verdict — the default voice, used when the answer reads straight off the record. */
    BrokAnswerBuilder derived(String state, String headline, String consequence, String basis) {
        return verdict(state, headline, consequence, BrokNarrative.DERIVED,
                BrokNarrative.NEAR_CERTAIN, basis);
    }

    /** An honest non-answer: the record simply does not contain what was asked. */
    BrokAnswerBuilder unknown(String headline, String consequence, String basis) {
        return verdict(BrokNarrative.UNKNOWN_STATE, headline, consequence, BrokNarrative.UNKNOWN_STATUS,
                BrokNarrative.CONSISTENT_WITH, basis);
    }

    // ---- the reasoning chain ----

    BrokAnswerBuilder because(String text, String status, String basis) {
        reasoning.add(new BrokStatement(text, status, basis));
        return this;
    }

    BrokAnswerBuilder becauseDerived(String text, String basis) {
        return because(text, BrokNarrative.DERIVED, basis);
    }

    BrokAnswerBuilder becauseInferred(String text, String basis) {
        return because(text, BrokNarrative.INFERRED, basis);
    }

    BrokAnswerBuilder impact(String statement, int count) {
        this.impact = new BrokImpact(statement, count);
        return this;
    }

    // ---- references: everything the answer touched ----

    BrokAnswerBuilder evidence(BrokRef ref) {
        return put(evidence, ref);
    }

    BrokAnswerBuilder evidence(List<BrokRef> refs) {
        refs.forEach(this::evidence);
        return this;
    }

    BrokAnswerBuilder artifact(BrokRef ref) {
        return put(artifacts, ref);
    }

    BrokAnswerBuilder knowledge(BrokRef ref) {
        return put(knowledge, ref);
    }

    BrokAnswerBuilder decision(BrokRef ref) {
        return put(decisions, ref);
    }

    BrokAnswerBuilder evaluation(BrokRef ref) {
        return put(evaluations, ref);
    }

    BrokAnswerBuilder revision(BrokRef ref) {
        return put(revisions, ref);
    }

    /** Routes a knowledge object into the panel its kind belongs to, so the workspace never mis-files it. */
    BrokAnswerBuilder reference(BrokRef ref) {
        if (ref == null) {
            return this;
        }
        return switch (ref.type()) {
            case "decision" -> decision(ref);
            case "evaluation" -> evaluation(ref);
            case "revision" -> revision(ref);
            case "observation", "claim", "evidence", "knowledge" -> knowledge(ref);
            default -> artifact(ref);
        };
    }

    // ---- recommendations and follow-ups ----

    BrokAnswerBuilder recommend(String title, String why, String impactText, String confidence,
                                   String status, List<String> evidenceIds, BrokAction action) {
        recommendations.add(new BrokRecommendation(title, why, impactText, confidence, status,
                evidenceIds == null ? List.of() : List.copyOf(evidenceIds), action));
        return this;
    }

    /** Carries a remembered "why" through unchanged — Engineering Memory, not a restatement of it. */
    BrokAnswerBuilder remember(BrokMemory entry) {
        if (entry != null && entry.decisionId() != null) {
            memory.putIfAbsent(entry.decisionId(), entry);
        }
        return this;
    }

    BrokAnswerBuilder followUp(String followUpQuestion, String rationale, String focus) {
        followUps.add(new BrokFollowUp(followUpQuestion, rationale, focus));
        return this;
    }

    boolean hasFollowUps() {
        return !followUps.isEmpty();
    }

    // ---- build ----

    BrokAnswer build(UUID organizationId, UUID projectId, String projectName, BrokRef focus, String scope) {
        List<String> graphNodeIds = new ArrayList<>();
        collectNodeIds(graphNodeIds, artifacts.values());
        collectNodeIds(graphNodeIds, evaluations.values());
        collectNodeIds(graphNodeIds, evidence.values());
        collectNodeIds(graphNodeIds, decisions.values());
        collectNodeIds(graphNodeIds, knowledge.values());
        if (focus != null && !graphNodeIds.contains(focus.id())) {
            graphNodeIds.add(0, focus.id());
        }

        BrokContext context = new BrokContext(organizationId, projectId, projectName, focus, scope,
                List.copyOf(graphNodeIds));

        return new BrokAnswer(
                UUID.randomUUID().toString(),
                question,
                intentKey,
                kind,
                verdict,
                List.copyOf(reasoning),
                impact != null ? impact : new BrokImpact("No downstream consequence was derived.", 0),
                List.copyOf(evidence.values()),
                new BrokReferences(List.copyOf(artifacts.values()), List.copyOf(knowledge.values()),
                        List.copyOf(decisions.values()), List.copyOf(evaluations.values()),
                        List.copyOf(revisions.values())),
                List.copyOf(recommendations),
                List.copyOf(memory.values()),
                List.copyOf(followUps),
                context,
                Instant.now());
    }

    private BrokAnswerBuilder put(Map<String, BrokRef> target, BrokRef ref) {
        if (ref != null && ref.id() != null) {
            target.putIfAbsent(ref.id(), ref);
        }
        return this;
    }

    private static void collectNodeIds(List<String> out, Iterable<BrokRef> refs) {
        for (BrokRef ref : refs) {
            if (ref.id() != null && !out.contains(ref.id())) {
                out.add(ref.id());
            }
        }
    }
}
