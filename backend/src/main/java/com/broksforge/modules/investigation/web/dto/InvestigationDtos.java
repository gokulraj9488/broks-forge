package com.broksforge.modules.investigation.web.dto;

import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAction;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokContext;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokImpact;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokMemory;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokRecommendation;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokRef;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokVerdict;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokFollowUp;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The wire contract of the Root Cause Explorer (P13) — one assembled engineering investigation.
 *
 * <p>These records deliberately reuse Brok's vocabulary rather than inventing a parallel one:
 * {@link BrokRef} for every referenced record, {@link BrokAction} for every workflow an investigation
 * can continue into, {@link BrokVerdict}/{@link BrokImpact}/{@link BrokRecommendation}/{@link BrokMemory}
 * for the epistemic contract. Two consequences follow, and both are the point: the Explorer inherits the
 * platform's design language for free (the same components render both surfaces), and an investigation can
 * never disagree with Brok about what a verdict, a confidence or a next action means.
 *
 * <p>Everything here is <em>derived</em>. The Explorer owns no storage and no second data model; it reads
 * the evaluation record, the classified failure findings, AI Git, Engineering Intelligence, Engineering
 * Memory and the Forge Graph, and arranges what it finds into a chronology and a causal chain.
 */
public final class InvestigationDtos {

    private InvestigationDtos() {
    }

    /**
     * One moment in the engineering chronology.
     *
     * <p>Time is part of the reasoning, not decoration: a prompt promoted an hour before a run started is
     * evidence, and the only way an engineer can see that is if both appear on the same axis.
     */
    @Schema(name = "InvestigationEvent", description = "One dated engineering event on the investigation timeline")
    public record InvestigationEvent(
            @Schema(description = "Stable id for this event within the investigation") String id,
            @Schema(description = "When it happened, read from the underlying record") Instant at,
            @Schema(description = "promotion | revision | dataset | evaluation | run | knowledge | decision "
                    + "| precedent") String kind,
            @Schema(description = "What happened, in one line") String title,
            @Schema(description = "The detail that makes it engineering rather than logging") String detail,
            @Schema(description = "healthy | attention | risk | failed | unknown") String state,
            @Schema(description = "The record this event can be opened as, when one exists") BrokRef ref
    ) {
    }

    /**
     * One cause, at one causal depth.
     *
     * <p>Stopping at the immediate cause is what makes an error viewer an error viewer. A real investigation
     * separates what broke (immediate) from what made breaking likely (contributing), what the record has
     * already lived through (historical) and what moved just before it (related change).
     */
    @Schema(name = "InvestigationCause", description = "A diagnosed cause at one causal layer")
    public record InvestigationCause(
            @Schema(description = "immediate | contributing | historical | related-change") String layer,
            @Schema(description = "The cause, stated plainly") String title,
            @Schema(description = "Why the record supports this reading") String explanation,
            @Schema(description = "derived | inferred | suggested | unknown") String status,
            @Schema(description = "consistent-with | likely | near-certain") String confidence,
            @Schema(description = "Ids of the records this rests on") List<String> evidenceIds,
            @Schema(description = "The workflow that tests or resolves it") BrokAction action
    ) {
    }

    /** One of the constitutional questions every investigation must answer, and its grounded answer. */
    @Schema(name = "InvestigationAnswer", description = "A question the investigation answers from the record")
    public record InvestigationAnswer(
            @Schema(description = "The engineering question") String question,
            @Schema(description = "The answer, composed from real records") String answer,
            @Schema(description = "derived | inferred | suggested | unknown") String status,
            @Schema(description = "What the answer was read from") String basis
    ) {
    }

    /** Everything the investigation gathered, partitioned into the chains the workspace renders. */
    @Schema(name = "InvestigationReferences", description = "Every record the investigation assembled")
    public record InvestigationReferences(
            @Schema(description = "Agent, prompt, dataset, provider — the ground this ran on") List<BrokRef> artifacts,
            @Schema(description = "The evidence chain: evaluations and failed runs actually read") List<BrokRef> evidence,
            @Schema(description = "The knowledge chain") List<BrokRef> knowledge,
            @Schema(description = "Engineering decisions recorded about this ground") List<BrokRef> decisions,
            @Schema(description = "The AI Git chain: revisions of the artifacts involved") List<BrokRef> revisions,
            @Schema(description = "Earlier failures on the same ground") List<BrokRef> precedents,
            @Schema(description = "Other evaluations of the same artifacts") List<BrokRef> relatedEvaluations
    ) {
    }

    /**
     * One complete investigation.
     *
     * <p>The field order is the reading order of the workspace, and it is the constitutional narrative:
     * what happened (verdict), when (timeline), why (causes), what it costs (impact), what it rests on
     * (references, memory), what to do (recommendations) and what to ask next (followUps).
     */
    @Schema(name = "Investigation", description = "An assembled engineering investigation")
    public record Investigation(
            String id,
            @Schema(description = "What is being investigated") BrokRef subject,
            @Schema(description = "The one-line finding and its epistemic footing") BrokVerdict verdict,
            @Schema(description = "The engineering chronology, oldest first") List<InvestigationEvent> timeline,
            @Schema(description = "Causes, deepest-first within each layer") List<InvestigationCause> causes,
            @Schema(description = "The engineering story: the questions every investigation must answer")
            List<InvestigationAnswer> story,
            @Schema(description = "What this failure holds open") BrokImpact impact,
            InvestigationReferences references,
            @Schema(description = "Why things are the way they are, recorded verbatim") List<BrokMemory> memory,
            @Schema(description = "What to do, each continuing into a real workflow") List<BrokRecommendation> recommendations,
            @Schema(description = "Questions to continue with in Brok, carrying this subject") List<BrokFollowUp> followUps,
            @Schema(description = "Scope and graph focus, shared with Brok") BrokContext context,
            Instant at
    ) {
    }
}
