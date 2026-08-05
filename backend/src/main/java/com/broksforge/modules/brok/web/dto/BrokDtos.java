package com.broksforge.modules.brok.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The wire contract of Brok (P12) — the Engineering Partner.
 *
 * <p>Every shape here exists to enforce the constitutional <b>epistemic contract</b> rather than to carry
 * chat messages. An answer is not prose: it is a verdict, a reasoning chain in which every step declares how
 * it is known, the grounded records it was read from, the artifacts/knowledge/decisions/evaluations/revisions
 * it touched, recommendations that each carry evidence + confidence + impact + a next action, and the
 * follow-up investigations that naturally continue the work.
 *
 * <p>Nothing in this contract can express an ungrounded statement: a {@link BrokStatement} without a
 * {@code basis} is a bug, and a {@link BrokVerdict} always declares its epistemic {@code status}. The
 * Brok answers "this conclusion is derived from …", never "I think …".
 */
public final class BrokDtos {

    private BrokDtos() {
    }

    // ============================================================================================
    // Request
    // ============================================================================================

    @Schema(name = "BrokAskRequest",
            description = "An engineering question, asked inside a resolved engineering context")
    public record BrokAskRequest(
            @NotBlank @Size(max = 500)
            @Schema(description = "The engineering question, in plain English",
                    example = "Why did the checkout evaluation fail?") String question,
            @Schema(description = "The project the question is scoped to, when the workspace has one")
            UUID projectId,
            @Schema(description = "The artifact or knowledge object currently in focus, e.g. \"prompt:<uuid>\"")
            String focus,
            @Size(max = 20)
            @Schema(description = "The conversation so far, oldest first. Lets a follow-up like \"show me the "
                    + "evidence\" inherit the subject of the question before it, so an engineer never has to "
                    + "repeat context. Carried by the client because the conversation is the client's, not "
                    + "the engineering record's.")
            List<BrokTurn> history
    ) {
    }

    /** One earlier turn of the conversation — only what is needed to carry the subject forward. */
    @Schema(name = "BrokTurn", description = "A previous question in the same investigation")
    public record BrokTurn(
            @Size(max = 500) @Schema(description = "What was asked") String question,
            @Schema(description = "The intent it resolved to") String intent,
            @Schema(description = "The object it was about, as a node id") String focus
    ) {
    }

    // ============================================================================================
    // Grounded references — every one of these points at a record that really exists
    // ============================================================================================

    /**
     * A pointer to something real in the engineering record: an artifact, a knowledge object, a decision, an
     * evaluation or an AI Git revision. The {@code id} is the same stable node id the Forge Graph, the
     * Registry and Engineering Intelligence already use, so the workspace can deep-link without inventing
     * routes.
     */
    @Schema(name = "BrokRef", description = "A traceable pointer to a real engineering record")
    public record BrokRef(
            @Schema(description = "Stable node id, e.g. \"evaluation:<uuid>\" or \"decision:prompt-version:<uuid>\"")
            String id,
            @Schema(description = "Object kind (agent, prompt, dataset, evaluation, decision, revision, …)")
            String type,
            @Schema(description = "Display label") String label,
            @Schema(description = "One-line detail, derived from the record itself") String detail,
            @Schema(description = "Outcome/status where the record has one, else null") String outcome,
            @Schema(description = "Underlying entity id, when the reference maps to one") UUID entityId,
            @Schema(description = "Owning project id, when applicable") UUID projectId,
            @Schema(description = "When the underlying engineering event happened") Instant at
    ) {
    }

    /**
     * One step of reasoning. Each step declares its own epistemic status, because a single answer routinely
     * mixes derived facts with an inference drawn from them — collapsing them into one confidence level would
     * be dishonest.
     */
    @Schema(name = "BrokStatement", description = "One reasoning step, with how it is known")
    public record BrokStatement(
            @Schema(description = "The statement itself") String text,
            @Schema(description = "derived | inferred | suggested | unknown") String status,
            @Schema(description = "What this was read from, in plain English") String basis
    ) {
    }

    /**
     * A clickable continuation into an existing engineering workflow. Brok never ends at an answer;
     * {@code kind} names an existing surface (never a new one) and the client resolves it to that surface's
     * real route.
     */
    @Schema(name = "BrokAction", description = "A next step that opens an existing engineering workflow")
    public record BrokAction(
            @Schema(description = "openGraph | openExecutionGraph | openIntelligence | openEvolution | "
                    + "openRevisions | compareRevisions | openKnowledge | openRegistry | openEvaluation | "
                    + "openAnalytics | openInsights") String kind,
            @Schema(description = "Button label, phrased as an engineering act") String label,
            @Schema(description = "Target artifact kind, when the action opens an artifact") String targetType,
            @Schema(description = "Target entity id, when the action opens an artifact") UUID entityId,
            @Schema(description = "Target project id, when the action opens an artifact") UUID projectId,
            @Schema(description = "Target node/object id for graph focus or knowledge objects") String targetId,
            @Schema(description = "For startInvestigation: the question the new investigation opens with")
            String question
    ) {
    }

    /**
     * A recommendation. The constitution requires all five parts — evidence, reasoning, confidence, impact and
     * a next action — so all five are fields rather than prose, and an incomplete recommendation cannot be
     * represented.
     */
    @Schema(name = "BrokRecommendation",
            description = "An engineering recommendation carrying evidence, reasoning, confidence, impact and a next action")
    public record BrokRecommendation(
            @Schema(description = "What to do") String title,
            @Schema(description = "The reasoning behind it") String why,
            @Schema(description = "What it changes if acted on (or not)") String impact,
            @Schema(description = "consistent-with | likely | near-certain") String confidence,
            @Schema(description = "derived | inferred | suggested | unknown") String status,
            @Schema(description = "Ids of the evidence records this rests on") List<String> evidenceIds,
            @Schema(description = "The workflow this recommendation continues into") BrokAction action
    ) {
    }

    @Schema(name = "BrokFollowUp", description = "An engineering-specific next question")
    public record BrokFollowUp(
            @Schema(description = "The question to ask next") String question,
            @Schema(description = "Why this is worth asking now") String rationale,
            @Schema(description = "The focus this question should carry, when it is about a specific object")
            String focus
    ) {
    }

    /** The headline judgement, in the product's single verdict vocabulary. */
    @Schema(name = "BrokVerdict", description = "The answer's verdict, epistemic status and provenance")
    public record BrokVerdict(
            @Schema(description = "healthy | attention | risk | failed | unknown") String state,
            @Schema(description = "The one-sentence answer") String headline,
            @Schema(description = "Why the reader should care") String consequence,
            @Schema(description = "derived | inferred | suggested | unknown") String status,
            @Schema(description = "consistent-with | likely | near-certain") String confidence,
            @Schema(description = "What the answer was derived from, in plain English") String basis
    ) {
    }

    @Schema(name = "BrokImpact", description = "The engineering consequence, stated before the detail")
    public record BrokImpact(
            @Schema(description = "The consequence as a sentence") String statement,
            @Schema(description = "How many artifacts are involved") int count
    ) {
    }

    /**
     * The resolved engineering context. This is what makes the workspace feel like it knows where you are:
     * which organization, which project, which artifact is in focus, and which graph nodes the current answer
     * concerns.
     */
    @Schema(name = "BrokContext", description = "The engineering context an answer was produced in")
    public record BrokContext(
            UUID organizationId,
            UUID projectId,
            String projectName,
            @Schema(description = "The artifact or knowledge object in focus, when one is resolved")
            BrokRef focus,
            @Schema(description = "Human description of what the answer covers") String scope,
            @Schema(description = "Graph node ids the answer concerns, for the graph context panel")
            List<String> graphNodeIds
    ) {
    }

    /** Everything the answer touched, partitioned exactly as the workspace panels present it. */
    @Schema(name = "BrokReferences", description = "Artifacts, knowledge, decisions, evaluations and revisions referenced")
    public record BrokReferences(
            List<BrokRef> artifacts,
            List<BrokRef> knowledge,
            List<BrokRef> decisions,
            List<BrokRef> evaluations,
            @Schema(description = "AI Git revisions referenced by the answer") List<BrokRef> revisions
    ) {
        public static BrokReferences empty() {
            return new BrokReferences(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    // ============================================================================================
    // Answer
    // ============================================================================================

    /**
     * One grounded engineering answer. The field order is the constitutional narrative itself: what happened
     * ({@code verdict}) → why ({@code reasoning}) → evidence → engineering impact → recommendation → next
     * action (carried inside each recommendation) → follow-up investigations.
     */
    @Schema(name = "BrokAnswer", description = "A grounded engineering answer from the Brok")
    public record BrokAnswer(
            @Schema(description = "Stable id for this answer") String id,
            @Schema(description = "The question as it was understood") String question,
            @Schema(description = "The resolved engineering intent") String intent,
            @Schema(description = "question | brief") String kind,
            BrokVerdict verdict,
            @Schema(description = "The reasoning chain, each step declaring how it is known")
            List<BrokStatement> reasoning,
            BrokImpact impact,
            @Schema(description = "The records the answer was read from") List<BrokRef> evidence,
            BrokReferences references,
            List<BrokRecommendation> recommendations,
            @Schema(description = "The remembered reasoning behind the decisions this answer touches")
            List<BrokMemory> memory,
            List<BrokFollowUp> followUps,
            BrokContext context,
            Instant at
    ) {
    }

    /**
     * A remembered "why" — the reasoning behind a real engineering decision. Brok surfaces these unchanged
     * from Engineering Memory rather than restating them, so the answer and the artifact's own Intelligence
     * tab always tell the same story.
     */
    @Schema(name = "BrokMemory", description = "A remembered engineering 'why', backed by a real decision")
    public record BrokMemory(
            @Schema(description = "The decision this memory is derived from") String decisionId,
            @Schema(description = "The engineering question, e.g. \"Why was X changed?\"") String question,
            @Schema(description = "The recorded answer") String answer,
            @Schema(description = "When the decision was made") Instant at
    ) {
    }

    @Schema(name = "BrokBriefRef", description = "An Engineering Brief Brok can produce right now")
    public record BrokBriefRef(
            @Schema(description = "daily | deployment | incident | prompt-review | evaluation | dataset | "
                    + "knowledge | architecture") String kind,
            @Schema(description = "Display title") String title,
            @Schema(description = "What this brief would tell you, derived from the current record") String summary,
            @Schema(description = "False when the record holds nothing for this brief to report on")
            boolean available
    ) {
    }
}
