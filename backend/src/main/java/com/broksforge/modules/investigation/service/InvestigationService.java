package com.broksforge.modules.investigation.service;

import com.broksforge.config.properties.AdvisorProperties;
import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Severity;
import com.broksforge.modules.brok.service.BrokActions;
import com.broksforge.modules.brok.service.BrokRecord;
import com.broksforge.modules.brok.service.BrokRecordReader;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAction;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokContext;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokFollowUp;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokImpact;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokMemory;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokRecommendation;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokRef;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokVerdict;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.service.EvaluationService;
import com.broksforge.modules.evaluation.service.MetricExecutionFailureTally;
import com.broksforge.modules.evaluation.service.MetricFailureTally;
import com.broksforge.modules.evaluation.web.dto.EvaluationJobResponse;
import com.broksforge.modules.evaluation.web.dto.EvaluationRunResponse;
import com.broksforge.modules.investigation.web.dto.InvestigationDtos.Investigation;
import com.broksforge.modules.investigation.web.dto.InvestigationDtos.InvestigationAnswer;
import com.broksforge.modules.investigation.web.dto.InvestigationDtos.InvestigationCause;
import com.broksforge.modules.investigation.web.dto.InvestigationDtos.InvestigationEvent;
import com.broksforge.modules.investigation.web.dto.InvestigationDtos.InvestigationReferences;
import com.broksforge.modules.platform.service.PlatformEvolutionService;
import com.broksforge.modules.platform.service.PlatformIntelligenceService;
import com.broksforge.modules.platform.web.dto.ArtifactEvolutionResponse;
import com.broksforge.modules.platform.web.dto.EngineeringRevision;
import com.broksforge.modules.platform.web.dto.KnowledgeObject;
import com.broksforge.modules.platform.web.dto.MemoryEntry;
import com.broksforge.modules.rootcause.service.RootCauseEngine;
import com.broksforge.modules.rootcause.service.RootCauseFinding;
import com.broksforge.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.broksforge.modules.brok.service.BrokNarrative.ATTENTION;
import static com.broksforge.modules.brok.service.BrokNarrative.CONSISTENT_WITH;
import static com.broksforge.modules.brok.service.BrokNarrative.DERIVED;
import static com.broksforge.modules.brok.service.BrokNarrative.FAILED;
import static com.broksforge.modules.brok.service.BrokNarrative.HEALTHY;
import static com.broksforge.modules.brok.service.BrokNarrative.INFERRED;
import static com.broksforge.modules.brok.service.BrokNarrative.LIKELY;
import static com.broksforge.modules.brok.service.BrokNarrative.NEAR_CERTAIN;
import static com.broksforge.modules.brok.service.BrokNarrative.RISK;
import static com.broksforge.modules.brok.service.BrokNarrative.SUGGESTED;
import static com.broksforge.modules.brok.service.BrokNarrative.UNKNOWN_STATE;
import static com.broksforge.modules.brok.service.BrokNarrative.UNKNOWN_STATUS;
import static com.broksforge.modules.brok.service.BrokNarrative.agoWord;
import static com.broksforge.modules.brok.service.BrokNarrative.humanize;
import static com.broksforge.modules.brok.service.BrokNarrative.list;
import static com.broksforge.modules.brok.service.BrokNarrative.plural;

/**
 * The Root Cause Explorer (P13) — the Engineering Investigation Workspace.
 *
 * <p>When an engineer asks "why?", this assembles an investigation instead of returning a paragraph. One
 * request gathers the evaluation, its failed runs, the artifacts it ran against, their AI Git revisions,
 * the engineering knowledge and decisions recorded about them, the engineering memory behind those
 * decisions, earlier failures on the same ground, and related evaluations — then arranges all of it into
 * a chronology, a causal chain of four depths, and the engineering story.
 *
 * <p><b>This service owns nothing.</b> It has no repository, no table and no second derivation. Every
 * component is read through an existing published service:
 * <ul>
 *   <li>{@link RootCauseEngine} — the platform's existing failure classifier, which supplies the
 *       <em>immediate</em> cause. P13 does not re-diagnose what P4 already diagnoses well; it surrounds
 *       that diagnosis with the depth it was missing.</li>
 *   <li>{@link BrokRecordReader} — one snapshot of the engineering record, and with it Brok's own
 *       precedent reading, so the Explorer and Brok can never disagree about whether a failure has
 *       happened before.</li>
 *   <li>{@link PlatformIntelligenceService} — AI Git revisions, knowledge, decisions and Engineering Memory.</li>
 *   <li>{@link PlatformEvolutionService} — blast radius.</li>
 *   <li>{@link EvaluationService} — the job, its failed runs and its metric tallies.</li>
 * </ul>
 *
 * <p>Because the output speaks Brok's vocabulary, an investigation is also a conversation the engineer can
 * continue: every cause carries an action into a real surface, and every follow-up is a question Brok can
 * answer about the same subject.
 */
@Service
public class InvestigationService {

    /** A revision promoted within this window before a run started is reported as a related change. */
    private static final Duration RELATED_CHANGE_WINDOW = Duration.ofDays(14);

    /** Enough of the chronology to reason over; beyond this a timeline stops being readable. */
    private static final int MAX_TIMELINE = 40;
    private static final int MAX_LIST = 8;

    private final EvaluationService evaluationService;
    private final RootCauseEngine engine;
    private final BrokRecordReader recordReader;
    private final PlatformIntelligenceService intelligence;
    private final PlatformEvolutionService evolution;
    private final AdvisorProperties properties;

    public InvestigationService(EvaluationService evaluationService,
                                RootCauseEngine engine,
                                BrokRecordReader recordReader,
                                PlatformIntelligenceService intelligence,
                                PlatformEvolutionService evolution,
                                AdvisorProperties properties) {
        this.evaluationService = evaluationService;
        this.engine = engine;
        this.recordReader = recordReader;
        this.intelligence = intelligence;
        this.evolution = evolution;
        this.properties = properties;
    }

    /**
     * Assembles the investigation for one evaluation.
     *
     * <p>The evaluation is loaded through {@link EvaluationService}, which enforces tenant scoping — so an
     * evaluation in another organization is a 404 here for the same reason it is everywhere else.
     */
    @Transactional(readOnly = true)
    public Investigation investigate(UUID actorId, UUID organizationId, UUID projectId, UUID evaluationId) {
        BrokRecord record = recordReader.read(organizationId, projectId);
        EvaluationJob subject = record.job(evaluationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evaluation", evaluationId));

        // The project is a narrowing parameter, not a requirement: an investigation opened from the graph or
        // from Brok knows the evaluation but not necessarily which project holds it. Resolving it from the
        // record keeps the evaluation module's own scoping check authoritative either way.
        UUID owningProjectId = subject.getProjectId();
        EvaluationJobResponse job = evaluationService.get(actorId, organizationId, owningProjectId, evaluationId);

        List<EvaluationRunResponse> failedRuns = evaluationService.sampleFailedRuns(
                actorId, organizationId, owningProjectId, evaluationId, properties.failureSampleSize());
        List<MetricFailureTally> tallies =
                evaluationService.metricFailureBreakdown(actorId, organizationId, owningProjectId, evaluationId);
        List<MetricExecutionFailureTally> executionFailures = evaluationService
                .metricExecutionFailureBreakdown(actorId, organizationId, owningProjectId, evaluationId);

        // The platform's existing classifier supplies the immediate reading. P13 adds the depth around it.
        List<RootCauseFinding> findings = engine.analyzeJob(job, tallies, executionFailures, failedRuns);

        Ground ground = groundOf(record, subject);
        List<EvaluationJob> precedents = record.precedentsOf(subject);
        List<EvaluationJob> related = relatedEvaluations(record, subject);
        Map<String, List<EngineeringRevision>> revisions = revisionsOf(organizationId, ground);
        List<KnowledgeObject> knowledge = knowledgeOf(record, subject, ground);
        List<BrokMemory> memory = memoryOf(organizationId, ground);

        boolean troubled = BrokRecord.troubled(subject);
        String state = stateOf(subject);

        Assembly a = new Assembly();
        a.subject = record.refOf(subject);
        a.verdict = verdictOf(subject, findings, state, troubled);
        a.timeline = timeline(record, subject, ground, revisions, knowledge, precedents, failedRuns);
        a.causes = causes(record, subject, ground, findings, precedents, revisions, troubled);
        a.impact = impactOf(record, subject);
        a.memory = memory;
        a.references = references(record, subject, ground, knowledge, revisions, precedents, related, failedRuns);
        a.recommendations = recommendations(subject, findings, ground, precedents, troubled);
        a.followUps = followUps(subject, precedents, ground);
        a.story = story(record, subject, ground, findings, precedents, revisions, knowledge, memory, troubled);

        return build(record, a, projectId);
    }

    // ================================================================================================
    // The engineering chronology
    // ================================================================================================

    /**
     * The investigation as a timeline: promotions, dataset changes, the run itself, the moment it broke,
     * the knowledge it produced and the decisions taken afterwards — all on one axis, oldest first.
     */
    private List<InvestigationEvent> timeline(BrokRecord record, EvaluationJob job, Ground ground,
                                              Map<String, List<EngineeringRevision>> revisions,
                                              List<KnowledgeObject> knowledge,
                                              List<EvaluationJob> precedents,
                                              List<EvaluationRunResponse> failedRuns) {
        List<InvestigationEvent> events = new ArrayList<>();
        Instant started = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();

        // Every revision of every artifact this evaluation ran against — the AI Git chain, in time.
        for (Subject artifact : ground.all()) {
            for (EngineeringRevision revision : revisions.getOrDefault(artifact.nodeId(), List.of())) {
                boolean before = revision.at() != null && started != null && revision.at().isBefore(started);
                String verb = revision.active() ? "promoted" : "created";
                events.add(new InvestigationEvent(
                        "revision:" + revision.id(),
                        revision.at(),
                        "prompt".equals(artifact.type()) || "agent".equals(artifact.type())
                                ? (revision.active() ? "promotion" : "revision")
                                : "dataset",
                        artifact.name() + " " + revision.label() + " " + verb,
                        revision.rationale() != null && !revision.rationale().isBlank()
                                ? revision.rationale()
                                : (revision.detail() != null ? revision.detail()
                                        : "No rationale was recorded for this revision."),
                        // A change made before a failing run is worth a second look; it is not itself a fault.
                        before && revision.active() ? ATTENTION : HEALTHY,
                        new BrokRef(revision.id(), "revision", artifact.name() + " " + revision.label(),
                                revision.detail(), revision.active() ? "ACTIVE" : null,
                                artifact.entityId(), artifact.projectId(), revision.at())));
            }
        }

        // Earlier failures on the same ground belong on this axis: they are why "again" is a finding.
        for (EvaluationJob precedent : precedents.stream().limit(4).toList()) {
            events.add(new InvestigationEvent(
                    "precedent:" + precedent.getId(),
                    BrokRecord.atOf(precedent),
                    "precedent",
                    precedent.getName() + " failed on the same ground",
                    "The record has been here before — "
                            + agoWord(BrokRecord.atOf(precedent), record.now()) + ".",
                    FAILED,
                    record.refOf(precedent)));
        }

        events.add(new InvestigationEvent("evaluation:created:" + job.getId(), job.getCreatedAt(), "evaluation",
                job.getName() + " created",
                "The evaluation was defined against "
                        + (ground.all().isEmpty() ? "its configured artifacts"
                                : list(ground.all().stream().map(Subject::name).toList())) + ".",
                HEALTHY, record.refOf(job)));

        if (job.getStartedAt() != null) {
            events.add(new InvestigationEvent("evaluation:started:" + job.getId(), job.getStartedAt(),
                    "evaluation", job.getName() + " started",
                    plural(job.getTotalItems(), "item") + " queued for measurement.",
                    HEALTHY, record.refOf(job)));
        }

        // The exact moments the chain broke, sampled from the real runs.
        for (EvaluationRunResponse run : failedRuns.stream().limit(4).toList()) {
            Instant at = run.completedAt();
            events.add(new InvestigationEvent(
                    "run:" + run.id(), at, "run",
                    "Run #" + run.sequence() + " failed",
                    run.error() != null && !run.error().isBlank()
                            ? shorten(run.error())
                            : "The run produced no result and recorded no error text.",
                    FAILED,
                    new BrokRef("run:" + run.id(), "run", "Run #" + run.sequence() + " of " + job.getName(),
                            run.error() != null ? shorten(run.error()) : null,
                            run.httpStatus() != null ? "HTTP " + run.httpStatus() : "FAILED",
                            job.getId(), job.getProjectId(), at)));
        }

        if (job.getCompletedAt() != null) {
            boolean hard = job.getStatus() == EvaluationStatus.FAILED;
            boolean partial = job.getFailedItems() > 0;
            events.add(new InvestigationEvent("evaluation:finished:" + job.getId(), job.getCompletedAt(),
                    "evaluation",
                    hard ? job.getName() + " failed"
                            : partial ? job.getName() + " completed with unmeasured items"
                                    : job.getName() + " completed",
                    hard ? (job.getErrorMessage() != null && !job.getErrorMessage().isBlank()
                                    ? shorten(job.getErrorMessage())
                                    : "The evaluation stopped before producing a result.")
                            : job.getCompletedItems() + " of " + job.getTotalItems() + " items measured"
                                    + (partial ? ", " + plural(job.getFailedItems(), "item") + " failed" : "") + ".",
                    hard ? FAILED : partial ? ATTENTION : HEALTHY,
                    record.refOf(job)));
        }

        // What the platform concluded off the back of it — knowledge is an engineering event too.
        for (KnowledgeObject object : knowledge.stream().limit(6).toList()) {
            events.add(new InvestigationEvent(
                    "knowledge:" + object.id(), object.at(),
                    "decision".equals(object.type()) ? "decision" : "knowledge",
                    "decision".equals(object.type())
                            ? "Decision recorded: " + object.title()
                            : humanize(object.type()) + " derived: " + object.title(),
                    object.summary() != null ? object.summary() : "Derived from the engineering record.",
                    HEALTHY,
                    record.refOf(object)));
        }

        events.removeIf(e -> e.at() == null);
        events.sort(Comparator.comparing(InvestigationEvent::at));
        return events.size() <= MAX_TIMELINE ? events : events.subList(events.size() - MAX_TIMELINE, events.size());
    }

    // ================================================================================================
    // The causal chain — four depths, never one
    // ================================================================================================

    private List<InvestigationCause> causes(BrokRecord record, EvaluationJob job, Ground ground,
                                            List<RootCauseFinding> findings, List<EvaluationJob> precedents,
                                            Map<String, List<EngineeringRevision>> revisions, boolean troubled) {
        List<InvestigationCause> causes = new ArrayList<>();
        String evaluationId = "evaluation:" + job.getId();

        // ---- Immediate: what actually broke, from the platform's own classifier -------------------
        List<RootCauseFinding> ranked = new ArrayList<>(findings);
        ranked.sort(Comparator.comparingInt((RootCauseFinding f) -> f.severity().ordinal()).reversed());

        if (ranked.isEmpty() || !troubled) {
            causes.add(new InvestigationCause("immediate",
                    troubled ? "The failure has no classified cause"
                            : "Nothing failed in this evaluation",
                    troubled
                            ? "The runs failed but matched no known failure pattern, so the cause has to be read "
                                    + "from the runs themselves rather than asserted here."
                            : "Every item this evaluation measured produced a result. There is no failure to "
                                    + "explain — this investigation is a record of a healthy run.",
                    troubled ? UNKNOWN_STATUS : DERIVED,
                    troubled ? CONSISTENT_WITH : NEAR_CERTAIN,
                    List.of(evaluationId),
                    troubled
                            ? BrokActions.openFailureGraph(job.getId(), job.getProjectId(),
                                    "View the failure graph")
                            : BrokActions.openExecutionGraph(job.getId(), job.getProjectId(),
                                    "View execution graph")));
        } else {
            RootCauseFinding immediate = ranked.get(0);
            causes.add(new InvestigationCause("immediate", immediate.rootCause(),
                    joinEvidence(immediate) + " " + immediate.recommendation(),
                    DERIVED, confidenceOf(immediate.confidence()),
                    List.of(evaluationId),
                    BrokActions.openFailureGraph(job.getId(), job.getProjectId(), "View the failure graph")));

            // ---- Contributing: the other classified modes ----------------------------------------
            for (RootCauseFinding finding : ranked.stream().skip(1).limit(4).toList()) {
                if (finding.severity() == Severity.INFO) {
                    continue;
                }
                causes.add(new InvestigationCause("contributing", finding.rootCause(),
                        joinEvidence(finding) + " " + finding.recommendation(),
                        DERIVED, confidenceOf(finding.confidence()),
                        List.of(evaluationId),
                        BrokActions.openEvaluation(job.getId(), job.getProjectId(), "Open the evaluation")));
            }
        }

        // ---- Contributing: the provider this actually reached ----------------------------------------
        String provider = record.providerNameOf(job);
        if (troubled && provider != null) {
            causes.add(new InvestigationCause("contributing",
                    "Every failing run in this evaluation reached " + provider,
                    "Attribution runs through the agent's configured provider, so this does not prove the provider "
                            + "is at fault — but a prompt or dataset change cannot fix a transport problem, and "
                            + "this is the cheapest hypothesis to eliminate first.",
                    INFERRED, LIKELY, List.of(evaluationId),
                    BrokActions.openRegistry("Open the registry")));
        }

        // ---- Historical: the record has been here before ---------------------------------------------
        if (!precedents.isEmpty()) {
            EvaluationJob first = precedents.get(0);
            causes.add(new InvestigationCause("historical",
                    "This has happened before — " + first.getName() + " failed "
                            + agoWord(BrokRecord.atOf(first), record.now()),
                    plural(precedents.size(), "earlier evaluation") + " on the same ground already failed"
                            + (precedents.size() > 1
                                    ? ": " + list(precedents.stream().limit(3).map(EvaluationJob::getName).toList())
                                    : "")
                            + ". A recurrence is a different engineering problem from a novelty: the question is "
                            + "not only what broke, but why the last fix did not hold.",
                    DERIVED, confidenceFromCount(precedents.size()),
                    precedents.stream().limit(3).map(p -> "evaluation:" + p.getId()).toList(),
                    BrokActions.startInvestigation("evaluation:" + first.getId(),
                            "Investigate the precedent", "Why did " + first.getName() + " fail?")));
        } else if (troubled) {
            causes.add(new InvestigationCause("historical",
                    "No precedent — this is the first failure on this ground",
                    "No earlier evaluation sharing this agent, prompt or dataset has failed, so history cannot "
                            + "shorten the diagnosis. That also makes this failure worth recording properly.",
                    DERIVED, NEAR_CERTAIN, List.of(evaluationId),
                    BrokActions.openIntelligence("evaluation", job.getId(), job.getProjectId(),
                            "Open intelligence")));
        }

        // ---- Historical: decisions carried on faith --------------------------------------------------
        List<KnowledgeObject> unsupported = record.unsupportedDecisions().stream()
                .filter(d -> ground.contains(d.artifactType(), d.artifactEntityId()))
                .limit(3).toList();
        for (KnowledgeObject decision : unsupported) {
            causes.add(new InvestigationCause("historical",
                    "\"" + decision.title() + "\" was decided without evidence",
                    "A promotion with no evaluation behind it means the current state of this artifact was never "
                            + "measured. That does not cause a failure by itself, but it is why the failure is "
                            + "harder to reason about than it should be.",
                    INFERRED, LIKELY, List.of(decision.id()),
                    BrokActions.openKnowledge(decision.id(), "Open the decision")));
        }

        // ---- Related changes: what moved just before this ran ----------------------------------------
        Instant started = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
        for (Subject artifact : ground.all()) {
            for (EngineeringRevision revision : revisions.getOrDefault(artifact.nodeId(), List.of())) {
                if (revision.at() == null || started == null
                        || !revision.at().isBefore(started)
                        || revision.at().isBefore(started.minus(RELATED_CHANGE_WINDOW))
                        || !revision.active()) {
                    continue;
                }
                causes.add(new InvestigationCause("related-change",
                        artifact.name() + " " + revision.label() + " was promoted "
                                + agoWord(revision.at(), started) + " before this ran",
                        (revision.rationale() != null && !revision.rationale().isBlank()
                                ? "The recorded reason: \"" + revision.rationale() + "\". "
                                : "No reason was recorded for that promotion. ")
                                + "Proximity is not causation — but a change this close to a failure is the first "
                                + "thing to compare against the revision that preceded it.",
                        INFERRED, revisions.getOrDefault(artifact.nodeId(), List.of()).size() > 1
                                ? LIKELY : CONSISTENT_WITH,
                        List.of(revision.id()),
                        BrokActions.compareRevisions(artifact.type(), artifact.entityId(), artifact.projectId(),
                                "Compare revisions")));
                break; // the most recent promotion per artifact is the one worth surfacing
            }
        }

        return causes;
    }

    // ================================================================================================
    // The engineering story — the questions every investigation must answer
    // ================================================================================================

    private List<InvestigationAnswer> story(BrokRecord record, EvaluationJob job, Ground ground,
                                            List<RootCauseFinding> findings, List<EvaluationJob> precedents,
                                            Map<String, List<EngineeringRevision>> revisions,
                                            List<KnowledgeObject> knowledge, List<BrokMemory> memory,
                                            boolean troubled) {
        List<InvestigationAnswer> story = new ArrayList<>();
        RootCauseFinding top = findings.stream()
                .max(Comparator.comparingInt(f -> f.severity().ordinal())).orElse(null);

        story.add(new InvestigationAnswer("What happened?",
                troubled
                        ? job.getName() + " "
                                + (job.getStatus() == EvaluationStatus.FAILED
                                        ? "failed" : "completed with " + plural(job.getFailedItems(), "unmeasured item"))
                                + ", " + job.getCompletedItems() + " of " + job.getTotalItems()
                                + " items measured."
                        : job.getName() + " completed cleanly — " + job.getCompletedItems() + " of "
                                + job.getTotalItems() + " items measured with no failures.",
                DERIVED, "the evaluation's status and item counters"));

        story.add(new InvestigationAnswer("Why?",
                top != null && troubled
                        ? top.rootCause() + ". " + joinEvidence(top)
                        : troubled
                                ? "The record does not classify this failure. Its cause has to be read from the "
                                        + "failed runs directly."
                                : "Nothing failed, so there is no cause to explain.",
                top != null && troubled ? DERIVED : troubled ? UNKNOWN_STATUS : DERIVED,
                "the classified failure modes of the sampled runs"));

        List<String> changes = new ArrayList<>();
        Instant started = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
        for (Subject artifact : ground.all()) {
            revisions.getOrDefault(artifact.nodeId(), List.of()).stream()
                    .filter(r -> r.active() && r.at() != null && started != null && r.at().isBefore(started)
                            && !r.at().isBefore(started.minus(RELATED_CHANGE_WINDOW)))
                    .findFirst()
                    .ifPresent(r -> changes.add(artifact.name() + " " + r.label() + " ("
                            + agoWord(r.at(), started) + " before the run)"));
        }
        story.add(new InvestigationAnswer("What changed?",
                changes.isEmpty()
                        ? "Nothing on this ground was promoted in the two weeks before the run, so the failure is "
                                + "unlikely to be explained by a recent change."
                        : "Promoted shortly before this ran: " + list(changes) + ".",
                changes.isEmpty() ? DERIVED : INFERRED,
                "the AI Git revision timeline of every artifact this evaluation used"));

        story.add(new InvestigationAnswer("Has this happened before?",
                precedents.isEmpty()
                        ? (troubled
                                ? "No. No earlier evaluation sharing this agent, prompt or dataset has failed."
                                : "No failure has ever been recorded on this ground.")
                        : "Yes — " + plural(precedents.size(), "earlier evaluation") + " on the same ground failed, "
                                + "most recently " + precedents.get(0).getName() + " "
                                + agoWord(BrokRecord.atOf(precedents.get(0)), record.now()) + ".",
                DERIVED, "every earlier evaluation sharing an artifact with this one"));

        int affected = impactCount(record, job);
        story.add(new InvestigationAnswer("Who or what was affected?",
                (ground.all().isEmpty()
                        ? "This evaluation's own conclusions"
                        : list(ground.all().stream().map(Subject::name).toList()))
                        + (affected > 0
                                ? ", and " + plural(affected, "downstream artifact") + " that depend on this "
                                        + "evaluation's conclusion."
                                : ". Nothing downstream depends on this evaluation yet."),
                DERIVED, "the evaluation's pinned configuration and the Forge Graph"));

        int evidenceCount = 1 + precedents.size() + knowledge.size();
        story.add(new InvestigationAnswer("How confident are we?",
                top != null && troubled
                        ? "The immediate cause is " + verbal(confidenceOf(top.confidence()))
                                + ", read from the sampled failed runs. The deeper causes are inferences and are "
                                + "labelled as such."
                        : "There is no cause to be confident about; this reading is derived directly from the "
                                + "evaluation's own record.",
                DERIVED, plural(evidenceCount, "record") + " read for this investigation"));

        story.add(new InvestigationAnswer("What evidence supports this?",
                plural(1 + precedents.size(), "evaluation") + ", "
                        + plural(knowledge.size(), "derived knowledge record") + " and "
                        + plural(memory.size(), "engineering memory entry")
                        + ". Every one of them is linked in this investigation.",
                DERIVED, "the records this investigation actually read"));

        story.add(new InvestigationAnswer("What should we do next?",
                troubled
                        ? (top != null ? top.recommendation()
                                : "Open the failure graph and read the broken stage directly.")
                        : "Nothing is required. This evaluation stands as evidence for the artifacts it measured.",
                SUGGESTED, "the classified failure modes and the state of the record"));

        return story;
    }

    // ================================================================================================
    // Verdict, impact, references, recommendations, follow-ups
    // ================================================================================================

    private BrokVerdict verdictOf(EvaluationJob job, List<RootCauseFinding> findings, String state,
                                  boolean troubled) {
        RootCauseFinding top = findings.stream()
                .filter(f -> f.severity() != Severity.INFO)
                .max(Comparator.comparingInt(f -> f.severity().ordinal())).orElse(null);
        if (!troubled) {
            return new BrokVerdict(state, job.getName() + " did not fail.",
                    "There is no root cause to find. This investigation records a healthy run and the evidence "
                            + "it produced.",
                    DERIVED, NEAR_CERTAIN, "the evaluation's own record");
        }
        return new BrokVerdict(state,
                top != null ? top.rootCause() : job.getName() + " failed without a classified cause.",
                top != null
                        ? "Until this is resolved, everything this evaluation measures stays unproven."
                        : "The runs failed but match no known pattern, so the cause must be read from the runs "
                                + "themselves.",
                top != null ? DERIVED : UNKNOWN_STATUS,
                top != null ? confidenceOf(top.confidence()) : CONSISTENT_WITH,
                "the evaluation's failed runs, classified against the platform's known failure modes");
    }

    private BrokImpact impactOf(BrokRecord record, EvaluationJob job) {
        int affected = impactCount(record, job);
        boolean troubled = BrokRecord.troubled(job);
        return new BrokImpact(
                !troubled
                        ? "Nothing is blocked by this evaluation."
                        : affected > 0
                                ? "This failure leaves " + plural(affected, "downstream artifact")
                                        + " without the evidence it was meant to provide."
                                : "This failure holds its own conclusion open; nothing downstream depends on it yet.",
                affected);
    }

    private InvestigationReferences references(BrokRecord record, EvaluationJob job, Ground ground,
                                               List<KnowledgeObject> knowledge,
                                               Map<String, List<EngineeringRevision>> revisions,
                                               List<EvaluationJob> precedents, List<EvaluationJob> related,
                                               List<EvaluationRunResponse> failedRuns) {
        List<BrokRef> artifacts = new ArrayList<>();
        for (Subject s : ground.all()) {
            artifacts.add(subjectRef(record, s));
        }
        record.provider(providerIdOf(record, job)).ifPresent(p -> artifacts.add(record.refOf(p)));

        List<BrokRef> evidence = new ArrayList<>();
        evidence.add(record.refOf(job));
        for (EvaluationRunResponse run : failedRuns.stream().limit(MAX_LIST).toList()) {
            evidence.add(new BrokRef("run:" + run.id(), "run", "Run #" + run.sequence() + " of " + job.getName(),
                    run.error() != null ? shorten(run.error()) : "No error text recorded",
                    run.httpStatus() != null ? "HTTP " + run.httpStatus() : "FAILED",
                    job.getId(), job.getProjectId(), run.completedAt()));
        }

        List<BrokRef> knowledgeRefs = new ArrayList<>();
        List<BrokRef> decisionRefs = new ArrayList<>();
        for (KnowledgeObject object : knowledge) {
            if ("decision".equals(object.type())) {
                decisionRefs.add(record.refOf(object));
            } else {
                knowledgeRefs.add(record.refOf(object));
            }
        }

        List<BrokRef> revisionRefs = new ArrayList<>();
        for (Subject artifact : ground.all()) {
            for (EngineeringRevision revision : revisions.getOrDefault(artifact.nodeId(), List.of())
                    .stream().limit(4).toList()) {
                revisionRefs.add(new BrokRef(revision.id(), "revision",
                        artifact.name() + " " + revision.label(),
                        revision.detail() != null ? revision.detail() : revision.rationale(),
                        revision.active() ? "ACTIVE" : null,
                        artifact.entityId(), artifact.projectId(), revision.at()));
            }
        }

        return new InvestigationReferences(
                capped(artifacts), capped(evidence), capped(knowledgeRefs), capped(decisionRefs),
                capped(revisionRefs),
                precedents.stream().limit(MAX_LIST).map(record::refOf).toList(),
                related.stream().limit(MAX_LIST).map(record::refOf).toList());
    }

    private List<BrokRecommendation> recommendations(EvaluationJob job, List<RootCauseFinding> findings,
                                                     Ground ground, List<EvaluationJob> precedents,
                                                     boolean troubled) {
        List<BrokRecommendation> out = new ArrayList<>();
        String evaluationId = "evaluation:" + job.getId();

        if (!troubled) {
            out.add(new BrokRecommendation("Use this run as evidence",
                    "A clean evaluation is the only thing that can turn a promotion from a judgement call into a "
                            + "measured one.",
                    "Closes an evidence gap rather than opening an investigation.",
                    NEAR_CERTAIN, SUGGESTED, List.of(evaluationId),
                    BrokActions.openIntelligence("evaluation", job.getId(), job.getProjectId(),
                            "Open intelligence")));
            return out;
        }

        for (RootCauseFinding finding : findings.stream()
                .filter(f -> f.severity() != Severity.INFO)
                .sorted(Comparator.comparingInt((RootCauseFinding f) -> f.severity().ordinal()).reversed())
                .limit(3).toList()) {
            out.add(new BrokRecommendation(finding.recommendation(),
                    finding.rootCause() + " — " + joinEvidence(finding),
                    finding.expectedImprovement(),
                    confidenceOf(finding.confidence()), DERIVED, List.of(evaluationId),
                    BrokActions.openFailureGraph(job.getId(), job.getProjectId(), "View the failure graph")));
        }

        if (!precedents.isEmpty()) {
            EvaluationJob first = precedents.get(0);
            out.add(new BrokRecommendation("Find out why the last fix did not hold",
                    "This ground has failed before. Repeating the previous fix without knowing why it lapsed "
                            + "risks a third occurrence.",
                    "Turns a recurring failure into a resolved one.",
                    LIKELY, INFERRED,
                    List.of(evaluationId, "evaluation:" + first.getId()),
                    BrokActions.startInvestigation("evaluation:" + first.getId(),
                            "Investigate the precedent", "Why did " + first.getName() + " fail?")));
        }

        Subject versioned = ground.versioned();
        if (versioned != null) {
            out.add(new BrokRecommendation("Compare the revisions of " + versioned.name(),
                    "If the artifact moved before this run, the diff is the shortest path to the cause; if it did "
                            + "not, the cause lies outside it. Either answer narrows the search.",
                    "Eliminates or confirms a change as the cause.",
                    LIKELY, SUGGESTED, List.of(versioned.nodeId()),
                    BrokActions.compareRevisions(versioned.type(), versioned.entityId(), versioned.projectId(),
                            "Compare revisions")));
        }
        return out;
    }

    /** The investigation stays a conversation: every follow-up is a question Brok answers about this subject. */
    private List<BrokFollowUp> followUps(EvaluationJob job, List<EvaluationJob> precedents, Ground ground) {
        String focus = "evaluation:" + job.getId();
        List<BrokFollowUp> out = new ArrayList<>();
        out.add(new BrokFollowUp("Why did " + job.getName() + " fail?",
                "Brok's own reading of the same runs.", focus));
        out.add(new BrokFollowUp("Has this happened before?",
                precedents.isEmpty() ? "Confirm this failure is genuinely new."
                        : "The precedent, with what the team did about it.", focus));
        out.add(new BrokFollowUp("Explain this execution graph.",
                "Walk the chain stage by stage.", focus));
        Subject versioned = ground.versioned();
        if (versioned != null) {
            out.add(new BrokFollowUp("What changed between these revisions?",
                    "The diff for " + versioned.name() + ".", versioned.nodeId()));
            out.add(new BrokFollowUp("Should I rollback " + versioned.name() + "?",
                    "Weighed against the evidence that covers it.", versioned.nodeId()));
        }
        out.add(new BrokFollowUp("Show every artifact affected by " + job.getName() + ".",
                "The blast radius of this failure.", focus));
        return out;
    }

    // ================================================================================================
    // Assembly helpers
    // ================================================================================================

    /** Mutable scratch for one assembly — keeps {@link #investigate} readable without a 15-arg constructor. */
    private static final class Assembly {
        BrokRef subject;
        BrokVerdict verdict;
        List<InvestigationEvent> timeline;
        List<InvestigationCause> causes;
        List<InvestigationAnswer> story;
        BrokImpact impact;
        InvestigationReferences references;
        List<BrokMemory> memory;
        List<BrokRecommendation> recommendations;
        List<BrokFollowUp> followUps;
    }

    private Investigation build(BrokRecord record, Assembly a, UUID projectId) {
        List<String> graphNodeIds = new ArrayList<>();
        graphNodeIds.add(a.subject.id());
        for (BrokRef ref : a.references.artifacts()) {
            if (!graphNodeIds.contains(ref.id())) {
                graphNodeIds.add(ref.id());
            }
        }
        for (BrokRef ref : a.references.knowledge()) {
            if (!graphNodeIds.contains(ref.id())) {
                graphNodeIds.add(ref.id());
            }
        }
        BrokContext context = new BrokContext(record.organizationId(), projectId, record.projectName(),
                a.subject,
                projectId != null && record.projectName() != null
                        ? "The project " + record.projectName()
                        : "Every project in this organization",
                List.copyOf(graphNodeIds));

        return new Investigation(UUID.randomUUID().toString(), a.subject, a.verdict, a.timeline, a.causes,
                a.story, a.impact, a.references, a.memory, a.recommendations, a.followUps, context,
                Instant.now());
    }

    /** The artifacts an evaluation ran against — the ground a precedent must share and a change can move. */
    private Ground groundOf(BrokRecord record, EvaluationJob job) {
        List<Subject> out = new ArrayList<>();
        if (job.getAgentId() != null) {
            record.agent(job.getAgentId()).ifPresent(x ->
                    out.add(new Subject("agent", x.getId(), x.getName(), x.getProjectId())));
        }
        if (job.getPromptId() != null) {
            record.prompt(job.getPromptId()).ifPresent(x ->
                    out.add(new Subject("prompt", x.getId(), x.getName(), x.getProjectId())));
        }
        if (job.getDatasetId() != null) {
            record.dataset(job.getDatasetId()).ifPresent(x ->
                    out.add(new Subject("dataset", x.getId(), x.getName(), x.getProjectId())));
        }
        return new Ground(out);
    }

    /** Other evaluations that measured the same artifacts — context for whether this one is an outlier. */
    private List<EvaluationJob> relatedEvaluations(BrokRecord record, EvaluationJob job) {
        return record.jobs().stream()
                .filter(j -> !j.getId().equals(job.getId()))
                .filter(j -> (job.getAgentId() != null && job.getAgentId().equals(j.getAgentId()))
                        || (job.getPromptId() != null && job.getPromptId().equals(j.getPromptId()))
                        || (job.getDatasetId() != null && job.getDatasetId().equals(j.getDatasetId())))
                .sorted(Comparator.comparing(BrokRecord::atOf,
                        Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed())
                .limit(MAX_LIST)
                .toList();
    }

    private Map<String, List<EngineeringRevision>> revisionsOf(UUID organizationId, Ground ground) {
        Map<String, List<EngineeringRevision>> out = new LinkedHashMap<>();
        for (Subject subject : ground.all()) {
            out.put(subject.nodeId(), safeRevisions(organizationId, subject));
        }
        return out;
    }

    private List<EngineeringRevision> safeRevisions(UUID organizationId, Subject subject) {
        try {
            return intelligence.revisions(organizationId, subject.type(), subject.entityId()).revisions();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<KnowledgeObject> knowledgeOf(BrokRecord record, EvaluationJob job, Ground ground) {
        List<KnowledgeObject> out = new ArrayList<>(record.knowledgeAbout("evaluation", job.getId()));
        for (Subject subject : ground.all()) {
            for (KnowledgeObject object : record.knowledgeAbout(subject.type(), subject.entityId())) {
                if (out.stream().noneMatch(k -> k.id().equals(object.id()))) {
                    out.add(object);
                }
            }
        }
        out.sort(Comparator.comparing(KnowledgeObject::at,
                Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed());
        return out;
    }

    /**
     * Engineering Memory, read from the same place the Intelligence tab and Brok read it — never re-derived,
     * and carried through verbatim so the recorded "why" cannot drift between surfaces.
     */
    private List<BrokMemory> memoryOf(UUID organizationId, Ground ground) {
        List<BrokMemory> out = new ArrayList<>();
        for (Subject subject : ground.all()) {
            try {
                for (MemoryEntry entry : intelligence
                        .intelligenceOf(organizationId, subject.type(), subject.entityId()).memory()) {
                    if (entry.answer() != null && !entry.answer().isBlank()
                            && out.stream().noneMatch(m -> m.decisionId().equals(entry.decisionId()))) {
                        out.add(new BrokMemory(entry.decisionId(), entry.question(), entry.answer(), entry.at()));
                    }
                }
            } catch (RuntimeException ignored) {
                // An artifact without an intelligence view simply contributes no memory.
            }
        }
        return out;
    }

    private int impactCount(BrokRecord record, EvaluationJob job) {
        try {
            ArtifactEvolutionResponse ev =
                    evolution.evolutionOf(record.organizationId(), "evaluation", job.getId());
            return ev != null ? ev.impactCount() : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private UUID providerIdOf(BrokRecord record, EvaluationJob job) {
        return job.getAgentId() == null ? null
                : record.agent(job.getAgentId()).map(a -> a.getProviderId()).orElse(null);
    }

    private BrokRef subjectRef(BrokRecord record, Subject subject) {
        return switch (subject.type()) {
            case "agent" -> record.agent(subject.entityId()).map(record::refOf).orElse(fallback(subject));
            case "prompt" -> record.prompt(subject.entityId()).map(record::refOf).orElse(fallback(subject));
            case "dataset" -> record.dataset(subject.entityId()).map(record::refOf).orElse(fallback(subject));
            default -> fallback(subject);
        };
    }

    private static BrokRef fallback(Subject subject) {
        return new BrokRef(subject.nodeId(), subject.type(), subject.name(), null, null,
                subject.entityId(), subject.projectId(), null);
    }

    private static String stateOf(EvaluationJob job) {
        if (job.getStatus() == EvaluationStatus.FAILED) {
            return FAILED;
        }
        if (job.getFailedItems() > 0) {
            return ATTENTION;
        }
        if (job.getStatus() == EvaluationStatus.COMPLETED) {
            return HEALTHY;
        }
        return UNKNOWN_STATE;
    }

    private static String confidenceOf(Confidence confidence) {
        return switch (confidence) {
            case HIGH -> NEAR_CERTAIN;
            case MEDIUM -> LIKELY;
            case LOW -> CONSISTENT_WITH;
        };
    }

    private static String confidenceFromCount(int count) {
        return count >= 3 ? NEAR_CERTAIN : count >= 2 ? LIKELY : CONSISTENT_WITH;
    }

    private static String verbal(String confidence) {
        return switch (confidence) {
            case NEAR_CERTAIN -> "near-certain";
            case LIKELY -> "likely";
            default -> "consistent with the evidence, no stronger";
        };
    }

    private static String joinEvidence(RootCauseFinding finding) {
        return finding.evidence().isEmpty() ? "" : String.join(". ", finding.evidence()) + ".";
    }

    private static List<BrokRef> capped(List<BrokRef> refs) {
        return refs.size() <= MAX_LIST ? List.copyOf(refs) : List.copyOf(refs.subList(0, MAX_LIST));
    }

    private static String shorten(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 180 ? cleaned : cleaned.substring(0, 177) + "…";
    }

    /** One artifact an evaluation ran against. */
    private record Subject(String type, UUID entityId, String name, UUID projectId) {
        String nodeId() {
            return type + ":" + entityId;
        }
    }

    /** The artifacts an evaluation ran against, together. */
    private record Ground(List<Subject> all) {
        boolean contains(String type, UUID entityId) {
            return all.stream().anyMatch(s -> s.type().equals(type) && s.entityId().equals(entityId));
        }

        /** The first artifact that carries revisions — what "compare the revisions" can act on. */
        Subject versioned() {
            return all.stream()
                    .filter(s -> List.of("prompt", "agent", "dataset").contains(s.type()))
                    .findFirst().orElse(null);
        }
    }

    /** Kept for symmetry with the rest of the platform's locale-safe lowercasing. */
    @SuppressWarnings("unused")
    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
