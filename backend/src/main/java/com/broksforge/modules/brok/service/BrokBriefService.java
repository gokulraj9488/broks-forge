package com.broksforge.modules.brok.service;

import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAnswer;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokBriefRef;
import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.service.EvaluationAnalyticsService;
import com.broksforge.modules.evaluation.service.EvaluationAnalyticsSummary;
import com.broksforge.modules.platform.web.dto.KnowledgeObject;
import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.provider.domain.Provider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.broksforge.modules.brok.service.BrokNarrative.ATTENTION;
import static com.broksforge.modules.brok.service.BrokNarrative.CONSISTENT_WITH;
import static com.broksforge.modules.brok.service.BrokNarrative.agoWord;
import static com.broksforge.modules.brok.service.BrokNarrative.DERIVED;
import static com.broksforge.modules.brok.service.BrokNarrative.FAILED;
import static com.broksforge.modules.brok.service.BrokNarrative.HEALTHY;
import static com.broksforge.modules.brok.service.BrokNarrative.INFERRED;
import static com.broksforge.modules.brok.service.BrokNarrative.LIKELY;
import static com.broksforge.modules.brok.service.BrokNarrative.NEAR_CERTAIN;
import static com.broksforge.modules.brok.service.BrokNarrative.RISK;
import static com.broksforge.modules.brok.service.BrokNarrative.SUGGESTED;
import static com.broksforge.modules.brok.service.BrokNarrative.UNKNOWN_STATE;
import static com.broksforge.modules.brok.service.BrokNarrative.humanize;
import static com.broksforge.modules.brok.service.BrokNarrative.percent;
import static com.broksforge.modules.brok.service.BrokNarrative.plural;

/**
 * The Engineering Briefs — Brok writing rather than answering.
 *
 * <p>A brief is not a digest of activity. It follows the constitutional narrative exactly once: what happened,
 * why, on what evidence, what it means for the engineering, what to do, and the workflow that does it. Each of
 * the eight briefs applies that narrative to a different question an engineering organization actually asks —
 * how are we doing today, what did we ship, what broke, are our prompts defensible, what did we measure, is our
 * ground truth sound, what do we know, and what shape is the system.
 *
 * <p>Briefs share the answer contract with questions deliberately: the workspace renders one component, the
 * evidence panel behaves identically, and a brief's recommendation opens the same workflows a question's does.
 */
@Service
public class BrokBriefService {

    /** The briefs this service can produce, in the order the workspace offers them. */
    public static final List<String> KINDS = List.of(
            "daily", "deployment", "incident", "prompt", "evaluation", "dataset", "knowledge",
            "architecture");

    private static final int MAX_LIST = 8;

    private final BrokRecordReader reader;
    private final EvaluationAnalyticsService analytics;

    public BrokBriefService(BrokRecordReader reader, EvaluationAnalyticsService analytics) {
        this.reader = reader;
        this.analytics = analytics;
    }

    /** Which briefs are worth opening right now, each with the headline it would lead with. */
    @Transactional(readOnly = true)
    public List<BrokBriefRef> available(UUID organizationId, UUID projectId) {
        BrokRecord record = reader.read(organizationId, projectId);
        List<BrokBriefRef> out = new ArrayList<>();
        out.add(new BrokBriefRef("daily", "Daily Brief",
                record.jobs().isEmpty() ? "Nothing has been evaluated yet."
                        : plural(record.since(record.now().minus(Duration.ofDays(1))).size(), "evaluation")
                                + " in the last 24 hours.",
                !record.isEmpty()));
        out.add(new BrokBriefRef("deployment", "Deployment Brief",
                promotions(record).isEmpty() ? "No revision has been promoted."
                        : plural(promotions(record).size(), "promotion") + " on record.",
                !promotions(record).isEmpty()));
        out.add(new BrokBriefRef("incident", "Incident Brief",
                record.failing().isEmpty() ? "Nothing is failing."
                        : plural(record.failing().size(), "evaluation") + " failing.",
                !record.failing().isEmpty()));
        out.add(new BrokBriefRef("prompt", "Prompt Brief",
                record.prompts().isEmpty() ? "No prompts registered."
                        : plural(record.prompts().size(), "prompt") + " to review.",
                !record.prompts().isEmpty()));
        out.add(new BrokBriefRef("evaluation", "Evaluation Brief",
                record.hasEvidence() ? plural(record.jobs().size(), "evaluation") + " on record."
                        : "Nothing has produced a result yet.",
                !record.jobs().isEmpty()));
        out.add(new BrokBriefRef("dataset", "Dataset Brief",
                record.datasets().isEmpty() ? "No datasets registered."
                        : plural(record.datasets().size(), "dataset") + " of ground truth.",
                !record.datasets().isEmpty()));
        out.add(new BrokBriefRef("knowledge", "Knowledge Brief",
                record.knowledge().isEmpty() ? "No knowledge has been derived yet."
                        : plural(record.knowledge().size(), "knowledge object") + " derived.",
                !record.knowledge().isEmpty()));
        out.add(new BrokBriefRef("architecture", "Architecture Brief",
                record.isEmpty() ? "Nothing has been registered yet."
                        : plural(record.agents().size(), "agent") + " across "
                                + plural(record.providers().size(), "provider") + ".",
                !record.isEmpty()));
        return out;
    }

    /** Produces one brief. Unknown kinds are a 404 rather than an empty document. */
    @Transactional(readOnly = true)
    public BrokAnswer brief(UUID actorId, UUID organizationId, UUID projectId, String kind) {
        if (kind == null || !KINDS.contains(kind)) {
            throw ResourceNotFoundException.of("Brief", kind);
        }
        BrokRecord record = reader.read(organizationId, projectId);
        BrokAnswerBuilder b = BrokAnswerBuilder.brief(titleOf(kind), kind);

        switch (kind) {
            case "daily" -> daily(b, record, actorId);
            case "deployment" -> deployment(b, record);
            case "incident" -> incident(b, record);
            case "prompt" -> promptReview(b, record);
            case "evaluation" -> evaluationBrief(b, record, actorId);
            case "dataset" -> datasetBrief(b, record);
            case "knowledge" -> knowledgeBrief(b, record);
            default -> architecture(b, record);
        }

        return b.build(record.organizationId(), record.projectId(), record.projectName(), null,
                record.projectId() != null && record.projectName() != null
                        ? "The project " + record.projectName()
                        : "Every project in this organization");
    }

    // ================================================================================================
    // The eight briefs
    // ================================================================================================

    /** How the engineering is doing today, and what needs a human first. */
    private void daily(BrokAnswerBuilder b, BrokRecord record, UUID actorId) {
        Instant from = record.now().minus(Duration.ofDays(1));
        List<EvaluationJob> today = record.since(from);
        List<EvaluationJob> failing = record.failing();
        List<KnowledgeObject> promoted = promotions(record).stream()
                .filter(d -> d.at() != null && d.at().isAfter(from)).toList();

        if (record.isEmpty()) {
            b.unknown("There is no engineering to brief on yet.",
                    "Register an agent, a prompt or a dataset and this brief will start writing itself.",
                    "an empty engineering record");
            return;
        }

        String state = failing.isEmpty() ? (record.hasEvidence() ? HEALTHY : UNKNOWN_STATE) : FAILED;
        b.derived(state,
                failing.isEmpty()
                        ? (record.hasEvidence()
                                ? "Nothing is failing this morning."
                                : "Nothing is failing — but nothing has been measured either.")
                        : plural(failing.size(), "evaluation") + " is failing this morning.",
                today.isEmpty()
                        ? "Nothing ran in the last 24 hours, so the record is unchanged since yesterday."
                        : plural(today.size(), "evaluation") + " ran in the last 24 hours"
                                + (promoted.isEmpty() ? "" : " and " + plural(promoted.size(), "revision")
                                        + " was promoted") + ".",
                "evaluations and promotions in the last 24 hours");

        // Why — the drivers behind that verdict.
        for (EvaluationJob job : failing.stream().limit(4).toList()) {
            b.becauseDerived(job.getName() + " failed"
                            + (job.getFailedItems() > 0 ? " with " + plural(job.getFailedItems(), "item")
                                    + " unmeasured" : "") + ".", "the evaluation record");
            b.evaluation(record.refOf(job));
            b.evidence(record.refOf(job));
        }
        for (KnowledgeObject decision : promoted.stream().limit(4).toList()) {
            b.becauseDerived(decision.title() + " — " + decision.summary(), "a derived promotion decision");
            b.decision(record.refOf(decision));
        }
        if (failing.isEmpty() && promoted.isEmpty() && !today.isEmpty()) {
            b.becauseDerived(plural(today.size(), "evaluation") + " completed without failing.",
                    "the evaluation record");
        }

        // L-41 — the price beside the quality.
        EvaluationAnalyticsSummary summary = summary(actorId, record, Duration.ofDays(7));
        if (summary != null && summary.runCount() > 0) {
            b.becauseDerived(percent(summary.passRate()) + " of "
                            + plural(summary.runCount(), "run") + " passed over the last week, at "
                            + (summary.avgLatencyMs() != null
                                    ? Math.round(summary.avgLatencyMs()) + " ms average latency" : "unrecorded latency")
                            + " and " + cost(summary.totalCost()) + " total cost.",
                    "seven days of recorded run telemetry");
        }

        int unsupported = record.unsupportedDecisions().size();
        if (unsupported > 0) {
            b.becauseDerived(plural(unsupported, "decision") + " still has no evidence behind it.",
                    "the derived decision record");
        }

        b.impact(failing.isEmpty()
                ? "Nothing is blocking a conclusion today."
                : plural(failing.size(), "evaluation") + " is leaving conclusions unproven.", failing.size());

        if (!failing.isEmpty()) {
            EvaluationJob first = failing.get(0);
            b.recommend("Start with " + first.getName(),
                    "It is the most recent failure, so its cause is the freshest and the cheapest to find.",
                    "Restores confidence in everything that evaluation measures.", NEAR_CERTAIN, DERIVED,
                    List.of("evaluation:" + first.getId()),
                    BrokActions.openFailureGraph(first.getId(), first.getProjectId(),
                            "View the failure graph"));
            b.recommend("Investigate " + first.getName() + " with Brok",
                    "An investigation keeps the evidence, the graph and the reasoning together while you work.",
                    "Turns this morning's failure into a tracked line of enquiry.", NEAR_CERTAIN, SUGGESTED,
                    List.of("evaluation:" + first.getId()),
                    BrokActions.startInvestigation("evaluation:" + first.getId(), "Start investigation",
                            "Why did " + first.getName() + " fail?"));
        } else if (unsupported > 0) {
            KnowledgeObject decision = record.unsupportedDecisions().get(0);
            b.recommend("Evidence \"" + decision.title() + "\"",
                    "Nothing is broken, which makes this the right morning to close the evidence gap.",
                    "Turns an unbacked promotion into a measured one.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openKnowledge(decision.id(), "Open the decision"));
        } else if (!record.hasEvidence()) {
            b.recommend("Run your first evaluation",
                    "The brief can only report on what has been measured.",
                    "Gives tomorrow's brief something real to say.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
        } else {
            b.recommend("Review this week in AI Git",
                    "A quiet day is the right time to check that what shipped was what was intended.",
                    "Confirms the record matches your intent.", NEAR_CERTAIN, DERIVED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
        }

        b.followUp("What should my team work on next?", "Turn this brief into an ordered queue.", null);
        b.followUp("What is the biggest engineering risk right now?", "Rank by consequence.", null);
    }

    /** What was promoted, and whether the evidence justifies it. */
    private void deployment(BrokAnswerBuilder b, BrokRecord record) {
        List<KnowledgeObject> promotions = promotions(record);
        if (promotions.isEmpty()) {
            b.unknown("Nothing has been promoted.",
                    "No revision has been made canonical, so nothing has changed what production runs.",
                    "the derived promotion record");
            return;
        }
        List<KnowledgeObject> unsupported = promotions.stream()
                .filter(d -> d.links().stream().noneMatch(l -> "evidence".equals(l.type()))).toList();

        b.derived(unsupported.isEmpty() ? HEALTHY : RISK,
                plural(promotions.size(), "revision") + " has been promoted"
                        + (unsupported.isEmpty() ? ", each with evidence behind it."
                                : ", of which " + unsupported.size() + " has none."),
                unsupported.isEmpty()
                        ? "Every deployment on record can be defended by pointing at an evaluation."
                        : "The unevidenced promotions cannot be defended later, and cannot be safely reversed.",
                "promotions and their evidence links");

        for (KnowledgeObject decision : promotions.stream().limit(MAX_LIST).toList()) {
            int evidence = BrokRecord.evidenceCount(decision);
            b.becauseDerived(decision.title() + " — "
                            + (evidence == 0 ? "no evidence" : plural(evidence, "supporting evaluation")) + ".",
                    "the promotion's evidence links");
            b.decision(record.refOf(decision));
            record.evidenceRefs(decision).forEach(b::evidence);
        }
        b.impact(unsupported.isEmpty()
                ? "Every deployment on record is traceable to evidence."
                : plural(unsupported.size(), "deployment") + " is running on judgement alone.",
                unsupported.size());

        if (unsupported.isEmpty()) {
            KnowledgeObject latest = promotions.get(0);
            b.recommend("Review the latest promotion in AI Git",
                    "Reading the diff alongside the reason is how a promotion stays reviewable.",
                    "Confirms what actually shipped.", NEAR_CERTAIN, DERIVED, List.of(),
                    BrokActions.openKnowledge(latest.id(), "Open the decision"));
        } else {
            KnowledgeObject first = unsupported.get(0);
            b.recommend("Evidence \"" + first.title() + "\"",
                    "It is a promotion nothing has measured.",
                    "Removes the largest unbacked change on record.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openKnowledge(first.id(), "Open the decision"));
        }
        b.followUp("What engineering decisions remain unsupported?", "See the full evidence gap.", null);
    }

    /** What broke, where the chain stopped, and what is still unanswered. */
    private void incident(BrokAnswerBuilder b, BrokRecord record) {
        List<EvaluationJob> failing = record.failing();
        List<EvaluationJob> partial = record.completedWithFailures();
        if (failing.isEmpty() && partial.isEmpty()) {
            b.derived(record.hasEvidence() ? HEALTHY : UNKNOWN_STATE,
                    record.hasEvidence() ? "There is no incident to report." : "There is nothing to report on.",
                    record.hasEvidence()
                            ? "No evaluation is failing and none completed with unmeasured items."
                            : "Nothing has been evaluated, so no failure could have been recorded.",
                    "the outcome of every evaluation in scope");
            return;
        }

        b.derived(failing.isEmpty() ? ATTENTION : FAILED,
                failing.isEmpty()
                        ? plural(partial.size(), "evaluation") + " completed with unmeasured items."
                        : plural(failing.size(), "evaluation") + " is failing.",
                "Every artifact those evaluations touch is currently unproven.",
                "failing evaluations and their recorded errors");

        Map<String, Long> byProvider = new LinkedHashMap<>();
        for (EvaluationJob job : failing) {
            String provider = record.providerNameOf(job);
            byProvider.merge(provider != null ? provider : "Unattributed", 1L, Long::sum);
            b.becauseDerived(job.getName() + " — "
                            + (job.getErrorMessage() != null && !job.getErrorMessage().isBlank()
                                    ? job.getErrorMessage()
                                    : plural(job.getFailedItems(), "item") + " did not complete") + ".",
                    "the evaluation's recorded outcome");
            b.evaluation(record.refOf(job));
            b.evidence(record.refOf(job));
            record.agent(job.getAgentId()).ifPresent(a -> b.artifact(record.refOf(a)));
            record.dataset(job.getDatasetId()).ifPresent(d -> b.artifact(record.refOf(d)));
        }
        for (EvaluationJob job : partial.stream().limit(4).toList()) {
            b.becauseDerived(job.getName() + " completed but left " + plural(job.getFailedItems(), "item")
                    + " unmeasured.", "the evaluation's item counters");
            b.evaluation(record.refOf(job));
        }
        if (byProvider.size() == 1 && !byProvider.containsKey("Unattributed") && failing.size() > 1) {
            String provider = byProvider.keySet().iterator().next();
            b.becauseInferred("Every failure reached " + provider
                            + ", which is consistent with one provider-level cause rather than several "
                            + "unrelated ones.",
                    "the provider configured on each failing evaluation's agent");
        }

        b.impact(plural(failing.size() + partial.size(), "evaluation")
                + " is holding a conclusion open.", failing.size() + partial.size());

        EvaluationJob first = failing.isEmpty() ? partial.get(0) : failing.get(0);

        // Precedent is part of an incident's shape: a recurrence is a different morning from a novelty,
        // and the record already knows which one this is.
        List<EvaluationJob> precedents = record.precedentsOf(first);
        if (!precedents.isEmpty()) {
            EvaluationJob precedent = precedents.get(0);
            b.becauseDerived("This has happened before — " + precedent.getName()
                            + " failed on the same ground "
                            + agoWord(BrokRecord.atOf(precedent), record.now()) + ".",
                    "earlier evaluations sharing an artifact with the failing one");
            b.evaluation(record.refOf(precedent));
            b.followUp("Has this happened before?",
                    "The precedent, with what the team did about it.", "evaluation:" + first.getId());
        }

        b.recommend("Open the execution graph for " + first.getName(),
                "The graph names the stage the chain stopped at, which is what turns an incident into a fix.",
                "Distinguishes an infrastructure outage from a quality regression.", NEAR_CERTAIN, DERIVED,
                List.of("evaluation:" + first.getId()),
                BrokActions.openFailureGraph(first.getId(), first.getProjectId(),
                        "View the failure graph"));
        b.recommend("Investigate " + first.getName() + " with Brok",
                "An incident is a line of enquiry, not a notification.",
                "Keeps the evidence, the graph and the reasoning in one place.", NEAR_CERTAIN, SUGGESTED,
                List.of("evaluation:" + first.getId()),
                BrokActions.startInvestigation("evaluation:" + first.getId(), "Start investigation",
                        "Explain this execution graph."));
        if (byProvider.size() == 1 && !byProvider.containsKey("Unattributed")) {
            b.recommend("Check " + byProvider.keySet().iterator().next() + " before changing anything",
                    "A single shared provider across every failure is the cheapest hypothesis to test.",
                    "Avoids rewriting a prompt to fix a connection.", LIKELY, INFERRED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
        }
        b.followUp("Which provider causes the most failures?", "Check whether this is a pattern.", null);
        b.followUp("What investigations are still incomplete?", "See what else is unanswered.", null);
    }

    /** Are the prompts defensible: promoted, evidenced, and measured? */
    private void promptReview(BrokAnswerBuilder b, BrokRecord record) {
        List<Prompt> prompts = record.prompts();
        if (prompts.isEmpty()) {
            b.unknown("There are no prompts to review.",
                    "A prompt is versioned intent; without one there is no recorded instruction to review.",
                    "the prompt registry");
            return;
        }
        List<Prompt> unpromoted = prompts.stream()
                .filter(p -> p.getCurrentActiveVersionId() == null).toList();
        List<Prompt> unmeasured = prompts.stream()
                .filter(p -> record.evaluationsFor("prompt", p.getId()).isEmpty()).toList();
        List<Prompt> failing = prompts.stream()
                .filter(p -> record.evaluationsFor("prompt", p.getId()).stream()
                        .anyMatch(j -> j.getStatus() == EvaluationStatus.FAILED))
                .toList();

        String state = !failing.isEmpty() ? FAILED
                : (!unpromoted.isEmpty() || !unmeasured.isEmpty()) ? ATTENTION : HEALTHY;
        b.derived(state,
                !failing.isEmpty()
                        ? plural(failing.size(), "prompt") + " has failing evidence against it."
                        : !unpromoted.isEmpty()
                                ? plural(unpromoted.size(), "prompt") + " has no promoted version."
                                : plural(prompts.size(), "prompt") + " is promoted and measured.",
                !unpromoted.isEmpty()
                        ? "Without a promoted version there is no production truth for those prompts."
                        : !unmeasured.isEmpty()
                                ? plural(unmeasured.size(), "prompt") + " has never been evaluated."
                                : "Every prompt has a canonical revision with evidence behind it.",
                "prompt versions and the evaluations referencing them");

        for (Prompt prompt : prompts.stream().limit(MAX_LIST).toList()) {
            List<EvaluationJob> evidence = record.evaluationsFor("prompt", prompt.getId());
            long failures = evidence.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).count();
            String note = prompt.getCurrentActiveVersionId() == null
                    ? "no promoted version"
                    : "v" + prompt.getLatestVersionNumber() + " is the latest of "
                            + plural(prompt.getLatestVersionNumber(), "revision");
            b.becauseDerived(prompt.getName() + " — " + note + ", "
                            + (evidence.isEmpty() ? "never evaluated"
                                    : plural(evidence.size(), "evaluation")
                                            + (failures > 0 ? " (" + failures + " failing)" : ""))
                            + ".", "the prompt's versions and evaluations");
            b.artifact(record.refOf(prompt));
            evidence.stream().limit(2).forEach(j -> {
                b.evaluation(record.refOf(j));
                b.evidence(record.refOf(j));
            });
            record.knowledgeAbout("prompt", prompt.getId()).stream()
                    .filter(k -> "decision".equals(k.type())).limit(1)
                    .forEach(k -> b.decision(record.refOf(k)));
        }

        b.impact(unmeasured.isEmpty()
                ? "Every prompt's behaviour rests on recorded evidence."
                : plural(unmeasured.size(), "prompt") + " is running without evidence.", unmeasured.size());

        Prompt focus = !failing.isEmpty() ? failing.get(0)
                : !unpromoted.isEmpty() ? unpromoted.get(0)
                        : !unmeasured.isEmpty() ? unmeasured.get(0) : prompts.get(0);
        b.recommend("Review " + focus.getName(),
                !failing.isEmpty() ? "It has failing evidence against it."
                        : !unpromoted.isEmpty() ? "It has no promoted version, so nothing is canonical."
                                : !unmeasured.isEmpty() ? "It has never been evaluated."
                                        : "It is the most recently changed prompt on record.",
                "Closes the largest gap in your prompt record.", NEAR_CERTAIN, DERIVED, List.of(),
                BrokActions.openIntelligence("prompt", focus.getId(), focus.getProjectId(),
                        "Open intelligence"));
        b.followUp("Which prompt has the highest engineering risk?", "Rank them by consequence.", null);
    }

    /** What has been measured, at what cost, and with what result. */
    private void evaluationBrief(BrokAnswerBuilder b, BrokRecord record, UUID actorId) {
        List<EvaluationJob> jobs = record.jobs();
        if (jobs.isEmpty()) {
            b.unknown("Nothing has been evaluated.",
                    "Without evaluations there is no evidence, and without evidence nothing here is known "
                            + "to work.",
                    "the evaluation record");
            return;
        }
        long failed = jobs.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).count();
        long completed = jobs.stream().filter(j -> j.getStatus() == EvaluationStatus.COMPLETED).count();
        EvaluationAnalyticsSummary summary = summary(actorId, record, Duration.ofDays(30));

        b.derived(failed > 0 ? FAILED : (completed > 0 ? HEALTHY : UNKNOWN_STATE),
                failed > 0 ? plural(failed, "evaluation") + " of " + jobs.size() + " failed."
                        : plural(completed, "evaluation") + " completed with no failures.",
                summary != null && summary.runCount() > 0
                        ? percent(summary.passRate()) + " of " + plural(summary.runCount(), "measured item")
                                + " passed over the last 30 days."
                        : "No item has produced a measured result yet.",
                "evaluation outcomes and 30 days of run telemetry");

        if (summary != null && summary.runCount() > 0) {
            b.becauseDerived("Quality: " + percent(summary.passRate()) + " pass rate across "
                    + plural(summary.runCount(), "run") + ".", "30 days of run telemetry");
            // L-41: quality is never reported without its price.
            b.becauseDerived("Price: " + (summary.avgLatencyMs() != null
                            ? Math.round(summary.avgLatencyMs()) + " ms average latency" : "latency unrecorded")
                            + ", " + cost(summary.totalCost()) + " total cost, "
                            + summary.totalTokens() + " tokens.",
                    "30 days of run telemetry");
        }
        for (EvaluationJob job : jobs.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived(job.getName() + " — "
                            + humanize(job.getStatus() != null ? job.getStatus().name() : "PENDING")
                            + ", " + job.getCompletedItems() + "/" + job.getTotalItems() + " items"
                            + (job.getFailedItems() > 0 ? ", " + job.getFailedItems() + " failed" : "") + ".",
                    "the evaluation record");
            b.evaluation(record.refOf(job));
            b.evidence(record.refOf(job));
        }
        b.impact(failed > 0
                ? plural(failed, "evaluation") + " left its conclusions unproven."
                : "Every evaluation produced a usable result.", (int) failed);

        EvaluationJob focus = jobs.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED)
                .findFirst().orElse(jobs.get(0));
        b.recommend(failed > 0 ? "Investigate " + focus.getName() : "Review " + focus.getName(),
                failed > 0 ? "A failing evaluation invalidates every conclusion drawn from it."
                        : "The most recent evaluation is the one your current decisions rest on.",
                failed > 0 ? "Restores the evidence base." : "Confirms the evidence base is sound.",
                NEAR_CERTAIN, DERIVED, List.of("evaluation:" + focus.getId()),
                failed > 0
                        ? BrokActions.openFailureGraph(focus.getId(), focus.getProjectId(),
                                "View the failure graph")
                        : BrokActions.openIntelligence("evaluation", focus.getId(), focus.getProjectId(),
                                "Open intelligence"));
        b.followUp("Why did latency increase?", "Read the price beside the quality.", null);
    }

    /** Is the ground truth sound, and is it being used? */
    private void datasetBrief(BrokAnswerBuilder b, BrokRecord record) {
        List<Dataset> datasets = record.datasets();
        if (datasets.isEmpty()) {
            b.unknown("There is no ground truth registered.",
                    "A dataset is what an evaluation measures against; without one, quality can be asserted "
                            + "but not evidenced.",
                    "the dataset registry");
            return;
        }
        List<Dataset> unused = datasets.stream()
                .filter(d -> record.evaluationsFor("dataset", d.getId()).isEmpty()).toList();
        List<Dataset> empty = datasets.stream().filter(d -> d.getCurrentItemCount() == 0).toList();
        int items = datasets.stream().mapToInt(Dataset::getCurrentItemCount).sum();

        b.derived(!empty.isEmpty() ? ATTENTION : (unused.isEmpty() ? HEALTHY : ATTENTION),
                !empty.isEmpty()
                        ? plural(empty.size(), "dataset") + " holds no items."
                        : unused.isEmpty()
                                ? plural(datasets.size(), "dataset") + " is in use as ground truth."
                                : plural(unused.size(), "dataset") + " has never been used.",
                "Your evaluations currently measure against " + plural(items, "recorded item") + ".",
                "dataset versions and the evaluations referencing them");

        for (Dataset dataset : datasets.stream().limit(MAX_LIST).toList()) {
            List<EvaluationJob> uses = record.evaluationsFor("dataset", dataset.getId());
            b.becauseDerived(dataset.getName() + " — " + plural(dataset.getCurrentItemCount(), "item")
                            + ", " + (uses.isEmpty() ? "never used in an evaluation"
                                    : "used by " + plural(uses.size(), "evaluation")) + ".",
                    "the dataset's current version and its evaluations");
            b.artifact(record.refOf(dataset));
            uses.stream().limit(2).forEach(j -> {
                b.evaluation(record.refOf(j));
                b.evidence(record.refOf(j));
            });
        }
        b.impact(unused.isEmpty()
                ? "Every dataset on record is doing work."
                : plural(unused.size(), "dataset") + " is holding ground truth nothing measures against.",
                unused.size());

        Dataset focus = !empty.isEmpty() ? empty.get(0) : (!unused.isEmpty() ? unused.get(0) : datasets.get(0));
        b.recommend(!empty.isEmpty() ? "Populate " + focus.getName()
                        : !unused.isEmpty() ? "Evaluate against " + focus.getName()
                                : "Review " + focus.getName(),
                !empty.isEmpty() ? "An empty dataset cannot evidence anything."
                        : !unused.isEmpty() ? "Ground truth nobody measures against proves nothing."
                                : "It is the ground truth most of your evidence rests on.",
                "Strengthens the evidence base every quality claim depends on.", NEAR_CERTAIN, SUGGESTED,
                List.of(),
                BrokActions.openIntelligence("dataset", focus.getId(), focus.getProjectId(),
                        "Open intelligence"));
        b.followUp("Show every artifact affected by " + focus.getName() + ".",
                "See what depends on this ground truth.", "dataset:" + focus.getId());
    }

    /** What the organization knows, and where that knowledge is weak. */
    private void knowledgeBrief(BrokAnswerBuilder b, BrokRecord record) {
        List<KnowledgeObject> knowledge = record.knowledge();
        if (knowledge.isEmpty()) {
            b.unknown("Your engineering has not produced knowledge yet.",
                    "Knowledge here is derived, never written: it appears when artifacts are promoted, "
                            + "evaluated and evidenced.",
                    "the derived knowledge catalog");
            return;
        }
        Map<String, Long> byKind = new LinkedHashMap<>();
        knowledge.forEach(k -> byKind.merge(k.type(), 1L, Long::sum));
        List<KnowledgeObject> unsupported = record.unsupportedDecisions();
        List<BrokRecord.Tension> tensions = record.tensions();

        b.derived(tensions.isEmpty() && unsupported.isEmpty() ? HEALTHY : RISK,
                plural(knowledge.size(), "knowledge object") + " has been derived from your engineering.",
                tensions.isEmpty() && unsupported.isEmpty()
                        ? "None of it contradicts itself and every decision has evidence."
                        : plural(unsupported.size(), "unsupported decision") + " and "
                                + plural(tensions.size(), "contradiction") + " weaken it.",
                "the derived knowledge catalog");

        byKind.forEach((kind, count) ->
                b.becauseDerived(plural(count, humanize(kind).toLowerCase(Locale.ROOT)) + " on record.",
                        "the derived knowledge catalog"));
        for (KnowledgeObject object : knowledge.stream().limit(MAX_LIST).toList()) {
            b.reference(record.refOf(object));
        }
        for (KnowledgeObject decision : unsupported.stream().limit(4).toList()) {
            b.becauseDerived("\"" + decision.title() + "\" has no evidence behind it.",
                    "the decision's evidence links");
            b.decision(record.refOf(decision));
        }
        for (BrokRecord.Tension tension : tensions.stream().limit(4).toList()) {
            b.becauseInferred("\"" + tension.claim().title() + "\" sits beside "
                            + plural(tension.failures().size(), "failing evaluation") + " of the same artifact.",
                    "claims compared against evidence about the same artifact");
            b.knowledge(record.refOf(tension.claim()));
        }
        b.impact(unsupported.size() + tensions.size() == 0
                ? "Everything the organization knows can be traced and checked."
                : plural(unsupported.size() + tensions.size(), "piece") + " of knowledge cannot be relied on.",
                unsupported.size() + tensions.size());

        b.recommend(unsupported.isEmpty() && tensions.isEmpty()
                        ? "Browse the knowledge catalog" : "Reconcile the weakest knowledge first",
                unsupported.isEmpty() && tensions.isEmpty()
                        ? "The catalog shows how each object was derived and what it links to."
                        : "Unsupported decisions and contradictions are what make a knowledge base stop "
                                + "being trusted.",
                "Keeps the record worth reading.", NEAR_CERTAIN, DERIVED, List.of(),
                unsupported.isEmpty()
                        ? BrokActions.openRegistry("Open the registry")
                        : BrokActions.openKnowledge(unsupported.get(0).id(), "Open the decision"));
        b.followUp("Show contradictions in our engineering knowledge.", "Read them in full.", null);
    }

    /** The shape of the system, and where it is concentrated. */
    private void architecture(BrokAnswerBuilder b, BrokRecord record) {
        if (record.isEmpty()) {
            b.unknown("Nothing has been registered yet, so there is no architecture to describe.",
                    "The shape of the system is read from real artifacts and their relationships.",
                    "the engineering registry");
            return;
        }
        Map<String, Long> agentsByProvider = new LinkedHashMap<>();
        long unassigned = 0;
        for (Agent agent : record.agents()) {
            if (agent.getProviderId() == null) {
                unassigned++;
                continue;
            }
            String name = record.provider(agent.getProviderId()).map(Provider::getName).orElse("Unknown");
            agentsByProvider.merge(name, 1L, Long::sum);
        }
        Map.Entry<String, Long> concentrated = agentsByProvider.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);
        boolean risky = concentrated != null && record.agents().size() > 1
                && concentrated.getValue() == record.agents().size();

        b.derived(risky ? ATTENTION : HEALTHY,
                plural(record.agents().size(), "agent") + " across "
                        + plural(record.providers().size(), "provider") + ", measured by "
                        + plural(record.jobs().size(), "evaluation") + ".",
                risky
                        ? "Every agent depends on " + concentrated.getKey()
                                + " — a single provider outage would stop all evaluation."
                        : "The system's dependencies are spread across more than one provider.",
                "the engineering graph's real relationships");

        b.becauseDerived(plural(record.providers().size(), "provider") + ", "
                        + plural(record.agents().size(), "agent") + ", "
                        + plural(record.prompts().size(), "prompt") + ", "
                        + plural(record.datasets().size(), "dataset") + " and "
                        + plural(record.jobs().size(), "evaluation") + " are registered.",
                "the engineering registry");
        agentsByProvider.forEach((provider, count) ->
                b.becauseDerived(provider + " serves " + plural(count, "agent") + ".",
                        "each agent's configured provider"));
        if (unassigned > 0) {
            b.becauseDerived(plural(unassigned, "agent") + " has no provider configured, so its calls "
                    + "cannot be attributed.", "the agent registry");
        }
        if (risky) {
            b.becauseInferred("Concentration on one provider is a structural risk rather than a fault — "
                            + "nothing is failing because of it today.",
                    "the distribution of agents across providers");
        }

        record.providers().stream().limit(MAX_LIST).forEach(p -> b.artifact(record.refOf(p)));
        record.agents().stream().limit(MAX_LIST).forEach(a -> b.artifact(record.refOf(a)));
        b.impact(risky
                ? "A single provider outage would stop " + plural(record.agents().size(), "agent") + "."
                : "No single dependency can stop the whole system.", record.agents().size());

        b.recommend("Open the Forge Graph",
                "The graph is the fastest way to see the shape of the system rather than count its parts.",
                "Makes concentration and orphans visible at a glance.", NEAR_CERTAIN, DERIVED, List.of(),
                BrokActions.openGraph(null, "Open the Forge Graph"));
        if (unassigned > 0) {
            b.recommend("Assign providers to the unattributed agents",
                    "Without a provider, a failure cannot be attributed to anything.",
                    "Makes failure attribution possible.", CONSISTENT_WITH, SUGGESTED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
        }
        b.followUp("Which provider causes the most failures?", "Check the concentration against reality.",
                null);
    }

    // ================================================================================================
    // Helpers
    // ================================================================================================

    private static List<KnowledgeObject> promotions(BrokRecord record) {
        return record.knowledgeOfType("decision").stream()
                .filter(d -> !d.id().startsWith("decision:archive:"))
                .toList();
    }

    private EvaluationAnalyticsSummary summary(UUID actorId, BrokRecord record, Duration window) {
        Instant from = record.now().minus(window);
        long runs = 0;
        long passed = 0;
        long tokens = 0;
        double latencyWeighted = 0;
        long latencyRuns = 0;
        BigDecimal cost = BigDecimal.ZERO;
        long jobs = 0;
        for (UUID projectId : record.projectIds()) {
            EvaluationAnalyticsSummary part =
                    analytics.summary(actorId, record.organizationId(), projectId, from);
            runs += part.runCount();
            passed += part.passedCount();
            tokens += part.totalTokens();
            jobs += part.jobCount();
            cost = cost.add(part.totalCost() == null ? BigDecimal.ZERO : part.totalCost());
            if (part.avgLatencyMs() != null && part.runCount() > 0) {
                latencyWeighted += part.avgLatencyMs() * part.runCount();
                latencyRuns += part.runCount();
            }
        }
        if (runs == 0) {
            return new EvaluationAnalyticsSummary(jobs, 0, 0, 0d, null, 0, BigDecimal.ZERO);
        }
        Double latency = latencyRuns > 0 ? latencyWeighted / latencyRuns : null;
        return new EvaluationAnalyticsSummary(jobs, runs, passed, (double) passed / runs, latency, tokens, cost);
    }

    private static String cost(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "no recorded";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String titleOf(String kind) {
        return switch (kind) {
            case "daily" -> "Daily Brief";
            case "deployment" -> "Deployment Brief";
            case "incident" -> "Incident Brief";
            case "prompt" -> "Prompt Brief";
            case "evaluation" -> "Evaluation Brief";
            case "dataset" -> "Dataset Brief";
            case "knowledge" -> "Knowledge Brief";
            default -> "Architecture Brief";
        };
    }
}
