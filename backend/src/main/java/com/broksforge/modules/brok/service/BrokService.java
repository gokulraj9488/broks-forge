package com.broksforge.modules.brok.service;

import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAnswer;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokAskRequest;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokContext;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokFollowUp;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokMemory;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokRef;
import com.broksforge.modules.brok.service.BrokQuestion.Subject;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationRun;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.repository.EvaluationRunAggregate;
import com.broksforge.modules.evaluation.repository.EvaluationRunRepository;
import com.broksforge.modules.evaluation.service.EvaluationAnalyticsService;
import com.broksforge.modules.evaluation.service.EvaluationTrendPoint;
import com.broksforge.modules.platform.service.PlatformEvolutionService;
import com.broksforge.modules.platform.service.PlatformIntelligenceService;
import com.broksforge.modules.platform.web.dto.ArtifactEvolutionResponse;
import com.broksforge.modules.platform.web.dto.EngineeringRevision;
import com.broksforge.modules.platform.web.dto.EvolutionRef;
import com.broksforge.modules.platform.web.dto.KnowledgeLink;
import com.broksforge.modules.platform.web.dto.KnowledgeObject;
import com.broksforge.modules.platform.web.dto.MemoryEntry;
import com.broksforge.modules.platform.web.dto.RevisionComparison;
import com.broksforge.modules.platform.web.dto.RevisionDiff;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.broksforge.modules.brok.service.BrokNarrative.ATTENTION;
import static com.broksforge.modules.brok.service.BrokNarrative.agoWord;
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
import static com.broksforge.modules.brok.service.BrokNarrative.confidenceFor;
import static com.broksforge.modules.brok.service.BrokNarrative.humanize;
import static com.broksforge.modules.brok.service.BrokNarrative.list;
import static com.broksforge.modules.brok.service.BrokNarrative.percent;
import static com.broksforge.modules.brok.service.BrokNarrative.plural;
import static com.broksforge.modules.brok.service.BrokNarrative.worseOf;

/**
 * Brok (P12) — the Engineering Partner.
 *
 * <p>This service answers engineering questions by <b>reasoning over the engineering record</b>, not by
 * generating text. Every sentence it produces is composed from rows that exist: evaluations and their runs,
 * version promotions, the derived Observation/Claim/Decision/Evidence/Knowledge objects, the Forge Graph's
 * real relationships and the platform's own analytics. That is the constitutional guarantee — the Brok
 * cannot fabricate, because it has no channel through which a fabricated statement could enter an answer.
 *
 * <p>It sits <em>above</em> the platform and reuses it wholesale: {@link PlatformIntelligenceService} for
 * knowledge and AI Git, {@link PlatformEvolutionService} for lineage and impact, and the evaluation module's
 * published analytics for quality, latency and spend. It adds no storage, no second data model and no
 * duplicate derivation.
 *
 * <p>Every answer obeys the epistemic contract: a verdict, a reasoning chain in which each step declares
 * whether it is derived or inferred, the evidence it rests on, the engineering impact, recommendations that
 * each carry a next action into an existing workflow, and follow-up investigations. When the record cannot
 * answer, Brok says exactly that and offers what it can answer instead.
 */
@Service
public class BrokService {

    private static final int MAX_FAILED_RUN_SAMPLE = 8;
    private static final int MAX_LIST = 8;

    private final BrokRecordReader reader;
    private final PlatformEvolutionService evolution;
    private final PlatformIntelligenceService intelligence;
    private final EvaluationAnalyticsService analytics;
    private final EvaluationRunRepository runRepository;

    public BrokService(BrokRecordReader reader,
                          PlatformEvolutionService evolution,
                          PlatformIntelligenceService intelligence,
                          EvaluationAnalyticsService analytics,
                          EvaluationRunRepository runRepository) {
        this.reader = reader;
        this.evolution = evolution;
        this.intelligence = intelligence;
        this.analytics = analytics;
        this.runRepository = runRepository;
    }

    // ================================================================================================
    // Public API
    // ================================================================================================

    /** Answers one engineering question, grounded in the organization's current engineering record. */
    @Transactional(readOnly = true)
    public BrokAnswer ask(UUID actorId, UUID organizationId, BrokAskRequest request) {
        BrokRecord record = reader.read(organizationId, request.projectId());
        BrokQuestion question = BrokQuestion.parse(request.question(), request.focus(), record,
                request.history() == null ? List.of() : request.history());

        // "Explain X" only means anything once X resolves to something real. When it does not, the honest
        // intent is UNKNOWN — reporting artifact.explain for a question about nothing would overstate what
        // Brok understood.
        BrokIntent intent = question.intent() == BrokIntent.ARTIFACT_EXPLAIN
                && !question.hasSubject() && question.ambiguous().isEmpty()
                ? BrokIntent.UNKNOWN : question.intent();
        BrokAnswerBuilder b = BrokAnswerBuilder.answer(request.question(), intent);

        if (record.isEmpty()) {
            return finish(emptyRecord(b), record, question);
        }
        if (!question.ambiguous().isEmpty() && needsSubject(intent) && !question.hasSubject()) {
            return finish(ambiguous(b, question), record, question);
        }

        // Memory, declared rather than assumed: when a follow-up does not restate its subject, Brok says
        // which earlier question it is continuing before it answers.
        if (question.carriedFrom() != null && question.hasSubject()) {
            b.becauseDerived("Read as a question about " + question.subject().name()
                            + ", carried from \"" + question.carriedFrom() + "\".",
                    "the subject of your previous question");
        }

        switch (intent) {
            case FAILURE_EXPLAIN -> failureExplain(b, record, question);
            case PROMOTION_ADVICE -> promotionAdvice(b, record, question);
            case EVIDENCE_SHOW -> evidenceShow(b, record, question);
            case GRAPH_VIEW -> graphView(b, record, question);
            case MEMORY_WHY -> memoryWhy(b, record, question);
            case EXECUTION_EXPLAIN -> executionExplain(b, record, question);
            case EVALUATION_EXPLAIN -> evaluationExplain(b, record, question);
            case HISTORY -> historyPrecedent(b, record, question);
            case PROMOTION_RATIONALE -> promotionRationale(b, record, question);
            case ROLLBACK_ADVICE -> rollbackAdvice(b, record, question);
            case REVISION_DIFF -> revisionDiff(b, record, question);
            case DECISION_EVIDENCE -> decisionEvidence(b, record, question);
            case IMPACT -> impact(b, record, question);
            case RISK_RANKING -> riskRanking(b, record, question);
            case KNOWLEDGE_TOPIC -> knowledgeTopic(b, record, question);
            case PERIOD_SUMMARY -> periodSummary(b, record, question);
            case PROVIDER_FAILURES -> providerFailures(b, record);
            case NEXT_WORK -> nextWork(b, record);
            case LATENCY -> performance(b, record, question, actorId, true);
            case COST -> performance(b, record, question, actorId, false);
            case UNSUPPORTED_DECISIONS -> unsupportedDecisions(b, record);
            case CONTRADICTIONS -> contradictions(b, record);
            case INCOMPLETE_INVESTIGATIONS -> incompleteInvestigations(b, record);
            case SYSTEM_STATE -> systemState(b, record);
            case ARTIFACT_EXPLAIN -> artifactExplain(b, record, question);
            case UNKNOWN -> {
                if (question.hasSubject()) {
                    artifactExplain(b, record, question);
                } else {
                    cannotAnswer(b, question);
                }
            }
            default -> cannotAnswer(b, question);
        }

        if (!b.hasFollowUps()) {
            defaultFollowUps(b, record, question);
        }
        return finish(b, record, question);
    }

    /**
     * The questions worth asking right now — derived from what the record actually contains, so the workspace
     * opens with engineering, not with a blank prompt box.
     */
    @Transactional(readOnly = true)
    public List<BrokFollowUp> suggestions(UUID organizationId, UUID projectId, String focus) {
        BrokRecord record = reader.read(organizationId, projectId);
        BrokQuestion question = BrokQuestion.parse("", focus, record);
        List<BrokFollowUp> out = new ArrayList<>();

        if (record.isEmpty()) {
            out.add(follow("What is the biggest engineering risk right now?",
                    "Ask once you have registered an agent, a prompt or a dataset.", null));
            return out;
        }

        Subject subject = question.subject();
        if (subject != null) {
            out.add(follow("Explain " + subject.name() + ".",
                    "Everything the record holds about the object you have in focus.", subject.nodeId()));
            out.add(follow("Show me the evidence.",
                    "What actually stands behind " + subject.name() + ".", subject.nodeId()));
            out.add(follow("Should I promote it?",
                    "Weighed against the evidence that covers the newest revision.", subject.nodeId()));
            out.add(follow("Show every artifact affected by " + subject.name() + ".",
                    "The blast radius of a change here.", subject.nodeId()));
        }

        List<EvaluationJob> failing = record.failing();
        if (!failing.isEmpty()) {
            EvaluationJob job = failing.get(0);
            out.add(follow("Why did " + job.getName() + " fail?",
                    plural(failing.size(), "evaluation") + " currently failing.",
                    "evaluation:" + job.getId()));
            out.add(follow("Has this happened before?",
                    "Precedent turns a diagnosis into a lookup.", "evaluation:" + job.getId()));
        }
        if (!record.unsupportedDecisions().isEmpty()) {
            out.add(follow("What engineering decisions remain unsupported?",
                    plural(record.unsupportedDecisions().size(), "decision")
                            + " with no evaluation standing behind them.", null));
        }
        if (!record.tensions().isEmpty()) {
            out.add(follow("Show contradictions in our engineering knowledge.",
                    "Some claims sit uneasily beside failing evidence.", null));
        }
        if (!record.inFlight().isEmpty()) {
            out.add(follow("What investigations are still incomplete?",
                    plural(record.inFlight().size(), "evaluation") + " has not finished.", null));
        }
        out.add(follow("What should my team work on next?",
                "The attention queue, ordered by consequence.", null));
        out.add(follow("Summarize what happened this week.",
                "Everything the record recorded over the last seven days.", null));
        if (record.hasEvidence()) {
            out.add(follow("Why did latency increase?",
                    "Quality is never worth reading without its price.", null));
        }
        return out.stream().limit(7).toList();
    }

    /** The resolved engineering context — what Brok understands about where you are. */
    @Transactional(readOnly = true)
    public BrokContext context(UUID organizationId, UUID projectId, String focus) {
        BrokRecord record = reader.read(organizationId, projectId);
        BrokQuestion question = BrokQuestion.parse("", focus, record);
        BrokRef focusRef = focusRef(record, question);
        return new BrokContext(organizationId, projectId, record.projectName(), focusRef,
                scopeOf(record), focusRef != null ? List.of(focusRef.id()) : List.of());
    }

    // ================================================================================================
    // Intent handlers — each one reads the record and states what it found
    // ================================================================================================

    private void failureExplain(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        EvaluationJob job = failingJobFor(record, q);
        if (job == null) {
            nothingFailed(b, record);
            return;
        }
        FailureReading reading = read(job);
        boolean hard = job.getStatus() == EvaluationStatus.FAILED;

        b.derived(hard ? FAILED : ATTENTION,
                hard ? job.getName() + " failed." : job.getName() + " completed, but part of it broke.",
                hard ? "Until it passes, the quality of everything it measures is unproven."
                        : plural(job.getFailedItems(), "item") + " did not produce a result, so the score "
                                + "describes only the items that did.",
                "the evaluation's recorded runs");

        if (job.getErrorMessage() != null && !job.getErrorMessage().isBlank()) {
            b.becauseDerived("The evaluation recorded: " + job.getErrorMessage(),
                    "the evaluation's error field");
        }
        b.becauseDerived(job.getCompletedItems() + " of " + job.getTotalItems() + " items completed"
                        + (job.getFailedItems() > 0 ? ", " + plural(job.getFailedItems(), "item") + " failed" : "")
                        + ".", "the evaluation's item counters");
        if (reading.dominantError() != null) {
            b.becauseInferred("Most failures share one cause: " + reading.dominantError()
                            + " (" + plural(reading.dominantCount(), "run") + ").",
                    plural(reading.sampled(), "sampled failed run"));
        }

        String provider = record.providerNameOf(job);
        if (provider != null && reading.looksLikeInfrastructure()) {
            b.becauseInferred("The failures read as infrastructure rather than quality, and this evaluation "
                            + "reached " + provider + ".",
                    "the failed runs' transport errors and the agent's configured provider");
        }

        b.evaluation(record.refOf(job));
        b.evidence(record.refOf(job));
        reading.samples().forEach(b::evidence);
        linkJobArtifacts(b, record, job);

        ArtifactEvolutionResponse impact = safeEvolution(record, "evaluation", job.getId());
        int affected = impact != null ? impact.impactCount() : 0;
        b.impact(affected > 0
                ? "A change here affects " + plural(affected, "downstream artifact") + "."
                : "Nothing downstream depends on this evaluation.", affected);

        // The Failure Graph is the Execution Graph in its red state — one model, one route, one truth.
        // Only the label changes, because "view the failure graph" is what the engineer is actually doing.
        b.recommend("Open the failure graph for " + job.getName(),
                "The graph shows the exact stage the chain broke at, rather than the fact that it broke.",
                "You will know whether this is a quality problem or an infrastructure one.",
                NEAR_CERTAIN, DERIVED, List.of("evaluation:" + job.getId()),
                BrokActions.openFailureGraph(job.getId(), job.getProjectId(), "View the failure graph"));
        b.recommend("Investigate " + job.getName() + " with Brok",
                "An investigation keeps the evidence, the graph and the reasoning in one place while you work.",
                "Turns a failure into a tracked line of enquiry rather than a tab you lose.",
                NEAR_CERTAIN, SUGGESTED, List.of("evaluation:" + job.getId()),
                BrokActions.startInvestigation("evaluation:" + job.getId(), "Start investigation",
                        "Explain this execution graph."));

        if (provider != null && reading.looksLikeInfrastructure()) {
            b.recommend("Check " + provider + " before changing anything",
                    "The recorded failures are transport-level, so a prompt or dataset change would be "
                            + "measuring a broken connection.",
                    "Rules out the cheapest cause first.", LIKELY, INFERRED,
                    List.of("evaluation:" + job.getId()),
                    BrokActions.openRegistry("Open the registry"));
        }

        b.followUp("Has this happened before?",
                "A precedent arrives with its resolution attached.", "evaluation:" + job.getId());
        b.followUp("Explain this execution graph.", "See where the chain broke.", "evaluation:" + job.getId());
        b.followUp("Show every artifact affected by " + job.getName() + ".",
                "Understand what this failure puts in question.", "evaluation:" + job.getId());
    }

    /**
     * "Should I promote this?" — the decision an engineer most wants a partner to stand behind. Brok answers
     * from the evidence that exists for the candidate revision, and refuses to bless an unmeasured one.
     */
    private void promotionAdvice(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Subject subject = versionedSubject(record, q);
        if (subject == null) {
            b.unknown("No artifact was named, so there is nothing to weigh a promotion against.",
                    "Name the prompt or agent you are considering promoting.",
                    "the absence of a resolvable subject");
            listCandidates(b, record, "prompt");
            return;
        }
        List<EngineeringRevision> revisions = safeRevisions(record, subject);
        b.artifact(subjectRef(record, subject));
        rememberWhy(b, record, subject);

        if (revisions.isEmpty()) {
            b.unknown(subject.name() + " has no revisions, so there is nothing to promote.",
                    "A promotion makes one recorded revision canonical; none exist yet.",
                    "the artifact's revision history");
            return;
        }
        EngineeringRevision active = revisions.stream().filter(EngineeringRevision::active).findFirst()
                .orElse(null);
        EngineeringRevision candidate = namedRevision(revisions, q);
        if (candidate == null) {
            candidate = revisions.get(0);
        }
        b.revision(revisionRef(subject, candidate));
        if (active != null && !active.id().equals(candidate.id())) {
            b.revision(revisionRef(subject, active));
        }

        if (candidate.active()) {
            b.derived(HEALTHY, candidate.label() + " is already the promoted revision of "
                            + subject.name() + ".",
                    "There is nothing waiting for a promotion decision here.",
                    "the artifact's revision timeline");
            b.becauseDerived(candidate.label() + " is marked active.", "the revision record");
            b.recommend("Review " + subject.name() + " in AI Git",
                    "The timeline shows whether the promoted revision is still the right one.",
                    "Confirms the canonical revision is intentional.", NEAR_CERTAIN, DERIVED,
                    List.of(subject.nodeId()),
                    BrokActions.openRevisions(subject.type(), subject.entityId(), subject.projectId(),
                            "Open AI Git"));
            b.followUp("Should I rollback " + subject.name() + "?",
                    "The opposite question, weighed against the same evidence.", subject.nodeId());
            return;
        }

        Instant createdAt = candidate.at();
        List<EvaluationJob> evidence = record.evaluationsFor(subject.type(), subject.entityId()).stream()
                .filter(j -> createdAt == null || (BrokRecord.atOf(j) != null
                        && !BrokRecord.atOf(j).isBefore(createdAt)))
                .toList();
        List<EvaluationJob> failures = evidence.stream()
                .filter(j -> j.getStatus() == EvaluationStatus.FAILED).toList();
        evidence.stream().limit(MAX_LIST).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });
        int affected = impactCount(record, subject);
        b.impact(affected > 0
                ? "Promoting it changes what " + plural(affected, "downstream artifact") + " depends on."
                : "Nothing downstream depends on this artifact, so the promotion is contained.", affected);

        if (evidence.isEmpty()) {
            b.verdict(UNKNOWN_STATE, "Nothing has measured " + candidate.label()
                            + ", so promoting it would be an act of faith.",
                    "A promotion with no evidence behind it cannot be defended later and cannot be safely "
                            + "reversed either.",
                    DERIVED, NEAR_CERTAIN, "the absence of evaluations covering this revision");
            b.becauseDerived("No evaluation has run against " + subject.name() + " since "
                    + candidate.label() + " was created.", "the evaluation record");
            b.recommend("Evaluate " + candidate.label() + " before promoting it",
                    "Evidence first is the whole difference between an engineering decision and a guess.",
                    "Converts the promotion into one you can defend.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openIntelligence(subject.type(), subject.entityId(), subject.projectId(),
                            "Open intelligence"));
        } else if (!failures.isEmpty()) {
            b.verdict(RISK, "The evidence argues against promoting " + candidate.label() + ".",
                    plural(failures.size(), "evaluation") + " covering it failed.",
                    DERIVED, confidenceFor(evidence.size()), "evaluations covering this revision");
            for (EvaluationJob job : failures.stream().limit(4).toList()) {
                b.becauseDerived(job.getName() + " failed.", "the evaluation record");
            }
            b.recommend("Fix the failures before promoting",
                    "Promoting over a failing evaluation makes the failure production behaviour.",
                    "Protects everything downstream of this artifact.", confidenceFor(evidence.size()),
                    DERIVED, failures.stream().limit(3).map(j -> "evaluation:" + j.getId()).toList(),
                    BrokActions.openFailureGraph(failures.get(0).getId(), failures.get(0).getProjectId(),
                            "View the failure graph"));
        } else {
            b.verdict(HEALTHY, "The evidence supports promoting " + candidate.label() + ".",
                    plural(evidence.size(), "evaluation") + " covering it passed and none failed.",
                    DERIVED, confidenceFor(evidence.size()), "evaluations covering this revision");
            b.becauseDerived(plural(evidence.size(), "evaluation") + " ran against " + subject.name()
                    + " since " + candidate.label() + " was created, with no failures.",
                    "the evaluation record");
            if (active != null) {
                b.becauseDerived("It would supersede " + active.label() + ".",
                        "the artifact's revision timeline");
            }
            b.recommend("Compare " + candidate.label()
                            + (active != null ? " against " + active.label() : "") + " before promoting",
                    "Reading the diff is what makes a promotion reviewable rather than merely approved.",
                    "You promote a change you have actually seen.", confidenceFor(evidence.size()), DERIVED,
                    evidence.stream().limit(3).map(j -> "evaluation:" + j.getId()).toList(),
                    BrokActions.compareRevisions(subject.type(), subject.entityId(), subject.projectId(),
                            "Compare revisions"));
        }
        b.followUp("What changed between these revisions?", "See exactly what a promotion would ship.",
                subject.nodeId());
        b.followUp("Show every artifact affected by " + subject.name() + ".",
                "Know the blast radius before you promote.", subject.nodeId());
    }

    /** "Show me the evidence." — the natural second turn, answered about whatever is currently in focus. */
    private void evidenceShow(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        KnowledgeObject focus = q.focusKnowledge();
        if (focus != null) {
            List<BrokRef> refs = record.evidenceRefs(focus);
            b.reference(record.refOf(focus));
            if (refs.isEmpty()) {
                b.verdict(RISK, "Nothing stands behind " + quoted(focus.title()) + ".",
                        "It is on record, but no evaluation supports it.",
                        DERIVED, NEAR_CERTAIN, "the object's evidence links");
                b.becauseDerived("The object carries no evidence link.", "the derived knowledge record");
            } else {
                b.derived(HEALTHY, plural(refs.size(), "piece") + " of evidence stands behind "
                                + quoted(focus.title()) + ".",
                        "Each one can be opened and checked.", "the object's evidence links");
                for (BrokRef r : refs.stream().limit(MAX_LIST).toList()) {
                    b.becauseDerived(r.label() + (r.outcome() != null
                                    ? " - " + humanize(r.outcome()).toLowerCase(Locale.ROOT) : "") + ".",
                            "an evaluation linked to this object");
                    b.evidence(r);
                }
            }
            b.impact("This is the whole basis for that statement.", refs.size());
            b.recommend("Inspect the evidence",
                    "Evidence you can open is the difference between a record and an assertion.",
                    "Makes the statement checkable.", NEAR_CERTAIN, DERIVED, List.of(),
                    BrokActions.openKnowledge(focus.id(), "Open the object"));
            return;
        }

        Subject subject = q.subject();
        if (subject == null) {
            b.unknown("There is nothing in focus, so there is no evidence to show.",
                    "Ask about an artifact, or click one in the graph, and Brok will show what supports it.",
                    "the absence of a subject in this conversation");
            return;
        }
        List<EvaluationJob> evidence = record.evaluationsFor(subject.type(), subject.entityId());
        b.artifact(subjectRef(record, subject));
        if (evidence.isEmpty()) {
            b.unknown("Nothing has measured " + subject.name() + ".",
                    "There is no evidence about it at all - which is a finding, not a clean bill of health.",
                    "the absence of any evaluation referencing it");
            b.recommend("Evaluate " + subject.name(),
                    "An artifact with no evidence is one nobody can vouch for.",
                    "Removes an unknown from the workspace.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openIntelligence(subject.type(), subject.entityId(), subject.projectId(),
                            "Open intelligence"));
            return;
        }
        long failures = evidence.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).count();
        b.derived(failures > 0 ? FAILED : HEALTHY,
                plural(evidence.size(), "evaluation") + " bear" + (evidence.size() == 1 ? "s" : "")
                        + " on " + subject.name() + (failures > 0 ? ", and " + failures + " failed." : "."),
                failures > 0
                        ? "Statements about this artifact rest on evidence that is partly broken."
                        : "Every statement about this artifact can be traced to one of them.",
                "evaluations referencing this artifact");
        for (EvaluationJob job : evidence.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived(job.getName() + " - "
                            + humanize(job.getStatus() != null ? job.getStatus().name() : "PENDING")
                            + ", " + job.getCompletedItems() + "/" + job.getTotalItems() + " items"
                            + (job.getFailedItems() > 0 ? ", " + job.getFailedItems() + " failed" : "") + ".",
                    "the evaluation record");
            b.evaluation(record.refOf(job));
            b.evidence(record.refOf(job));
        }
        record.knowledgeAbout(subject.type(), subject.entityId()).stream()
                .filter(k -> "evidence".equals(k.type())).limit(MAX_LIST)
                .forEach(k -> b.knowledge(record.refOf(k)));
        rememberWhy(b, record, subject);
        b.impact("This is everything the record can offer about " + subject.name() + ".", evidence.size());
        b.recommend("Open " + subject.name() + "'s engineering intelligence",
                "Intelligence shows the evidence beside the claims and decisions it supports.",
                "Connects the measurements to what was concluded from them.", NEAR_CERTAIN, DERIVED,
                List.of(subject.nodeId()),
                BrokActions.openIntelligence(subject.type(), subject.entityId(), subject.projectId(),
                        "Open intelligence"));
        b.followUp("Show every artifact affected by " + subject.name() + ".",
                "See how far these conclusions reach.", subject.nodeId());
    }

    /** "Open the graph." - Brok still answers before it moves you; navigation is never a blank hand-off. */
    private void graphView(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Subject subject = q.subject();
        if (subject == null) {
            b.derived(HEALTHY, "Here is your engineering system as one connected graph.",
                    plural(record.agents().size(), "agent") + ", "
                            + plural(record.prompts().size(), "prompt") + ", "
                            + plural(record.datasets().size(), "dataset") + " and "
                            + plural(record.jobs().size(), "evaluation")
                            + " with the real relationships between them.",
                    "the engineering graph");
            b.impact("The graph is where impact and lineage become visible at a glance.",
                    record.agents().size() + record.prompts().size() + record.datasets().size());
            b.recommend("Open the Forge Graph",
                    "Seeing the shape of the system is faster than reading a list of its parts.",
                    "Makes concentration, orphans and blast radius visible.", NEAR_CERTAIN, DERIVED,
                    List.of(), BrokActions.openGraph(null, "Open the Forge Graph"));
            return;
        }
        ArtifactEvolutionResponse ev = safeEvolution(record, subject.type(), subject.entityId());
        int dependents = ev != null ? ev.dependents().size() : 0;
        int dependencies = ev != null ? ev.dependencies().size() : 0;
        b.artifact(subjectRef(record, subject));
        b.derived(HEALTHY, "Here is " + subject.name() + " in the Forge Graph.",
                dependents + dependencies == 0
                        ? "It has no recorded relationships yet, so it sits alone on the canvas."
                        : plural(dependencies, "thing") + " upstream of it and "
                                + plural(dependents, "thing") + " downstream.",
                "the engineering graph's relationships");
        if (ev != null) {
            for (EvolutionRef d : ev.dependencies().stream().limit(MAX_LIST).toList()) {
                b.becauseDerived(subject.name() + " depends on " + d.name() + ".",
                        "a real graph relationship");
                b.artifact(new BrokRef(d.id(), d.type(), d.name(), null, null, d.entityId(),
                        d.projectId(), null));
            }
            for (EvolutionRef d : ev.dependents().stream().limit(MAX_LIST).toList()) {
                b.becauseDerived(d.name() + " depends on " + subject.name() + ".",
                        "a real graph relationship");
                b.artifact(new BrokRef(d.id(), d.type(), d.name(), null, null, d.entityId(),
                        d.projectId(), null));
            }
        }
        int affected = ev != null ? ev.impactCount() : 0;
        b.impact(affected > 0
                ? "A change here transitively affects " + plural(affected, "artifact") + "."
                : "Nothing is transitively affected by a change here.", affected);
        b.recommend("Open the Forge Graph on " + subject.name(),
                "The canvas selects it and shows its neighbourhood, so you start where you were looking.",
                "Shows the whole neighbourhood a change would touch.", NEAR_CERTAIN, DERIVED,
                List.of(subject.nodeId()), BrokActions.openGraph(subject.nodeId(), "Open in Forge Graph"));
        b.followUp("Show every artifact affected by " + subject.name() + ".",
                "Read the blast radius as a list.", subject.nodeId());
    }

    /** "What was the reasoning?" - Engineering Memory, surfaced unchanged rather than restated. */
    private void memoryWhy(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Subject subject = q.subject();
        if (subject == null) {
            b.unknown("There is nothing in focus, so there is no reasoning to recall.",
                    "Engineering memory belongs to an artifact - ask about one, or click it in the graph.",
                    "the absence of a subject in this conversation");
            return;
        }
        List<MemoryEntry> memory = safeMemory(record, subject);
        b.artifact(subjectRef(record, subject));
        for (MemoryEntry m : memory) {
            b.remember(new BrokMemory(m.decisionId(), m.question(), m.answer(), m.at()));
        }

        List<MemoryEntry> recorded = memory.stream()
                .filter(m -> m.answer() != null && !m.answer().isBlank()).toList();
        if (recorded.isEmpty()) {
            b.verdict(ATTENTION, "Nothing was recorded about why " + subject.name() + " is the way it is.",
                    "The changes happened; the reasoning behind them was never written down, so it cannot "
                            + "be reconstructed later.",
                    DERIVED, NEAR_CERTAIN, "the decisions recorded for this artifact");
            b.becauseDerived(memory.isEmpty()
                            ? "No decision has been recorded for it at all."
                            : plural(memory.size(), "decision") + " exists, none carrying a rationale.",
                    "the derived decision record");
            b.recommend("Record the reasoning on the next revision",
                    "A revision's notes are what turn a version number into a story.",
                    "Stops the next engineer having to guess.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openRevisions(subject.type(), subject.entityId(), subject.projectId(),
                            "Open AI Git"));
        } else {
            b.derived(HEALTHY, "Here is what was recorded about why " + subject.name()
                            + " is the way it is.",
                    plural(recorded.size(), "engineering decision") + " carries its reasoning.",
                    "the rationale recorded on each decision");
            for (MemoryEntry entry : recorded.stream().limit(MAX_LIST).toList()) {
                b.becauseDerived(entry.question() + " " + entry.answer(), "engineering memory");
            }
            b.recommend("Open " + subject.name() + "'s engineering intelligence",
                    "Memory sits beside the decisions and evidence it came from.",
                    "Puts the reasoning back in its context.", NEAR_CERTAIN, DERIVED,
                    List.of(subject.nodeId()),
                    BrokActions.openIntelligence(subject.type(), subject.entityId(), subject.projectId(),
                            "Open intelligence"));
        }
        record.knowledgeAbout(subject.type(), subject.entityId()).stream()
                .filter(k -> "decision".equals(k.type())).limit(MAX_LIST)
                .forEach(k -> b.decision(record.refOf(k)));
        b.impact("This is the recorded reasoning behind the artifact's current state.", recorded.size());
        b.followUp("Why was " + subject.name() + " promoted?", "The decision behind the current revision.",
                subject.nodeId());
    }

    private void executionExplain(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        EvaluationJob job = evaluationFor(record, q, true);
        if (job == null) {
            b.unknown("There is no execution to explain in this workspace yet.",
                    "An execution graph is drawn from an evaluation's recorded runs, and none exist here.",
                    "the absence of any evaluation runs");
            b.recommend("Run an evaluation", "The execution graph is a reading of a real run.",
                    "Gives the platform something real to draw.", CONSISTENT_WITH, SUGGESTED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
            return;
        }
        FailureReading reading = read(job);
        boolean red = reading.failedRuns() > 0 || job.getStatus() == EvaluationStatus.FAILED;

        if (!red) {
            b.derived(HEALTHY, "The execution graph for " + job.getName() + " is not red.",
                    "Every stage of the chain — prompt, provider, model, metrics — produced a result.",
                    "the evaluation's recorded runs");
            b.becauseDerived(job.getCompletedItems() + " of " + job.getTotalItems()
                    + " items ran to completion with no failed run.", "the evaluation's runs");
        } else {
            b.derived(FAILED, "The graph is red because " + plural(reading.failedRuns(), "run")
                            + " broke before producing a result.",
                    "A red stage means the chain stopped there — the score below it describes fewer items "
                            + "than you think.",
                    "the evaluation's failed runs");
            if (reading.dominantError() != null) {
                b.becauseDerived("The recorded error is: " + reading.dominantError(),
                        plural(reading.sampled(), "sampled failed run"));
            }
            b.becauseInferred("The break is at the " + reading.stage() + " stage of the chain.",
                    "the transport status and error text on the failed runs");
        }

        b.evaluation(record.refOf(job));
        b.evidence(record.refOf(job));
        reading.samples().forEach(b::evidence);
        linkJobArtifacts(b, record, job);
        b.impact(red ? "The items behind the red stage were never measured." : "Nothing is blocked.",
                red ? reading.failedRuns() : 0);

        b.recommend(red ? "Trace the broken stage in the execution graph"
                        : "Review the execution graph for " + job.getName(),
                red ? "Reading the chain tells you which component to fix, not merely that something failed."
                        : "The graph shows what each stage cost as well as what it produced.",
                red ? "Turns a failed evaluation into a specific fix."
                        : "Confirms the run behaved the way you intended.",
                NEAR_CERTAIN, DERIVED, List.of("evaluation:" + job.getId()),
                red
                        ? BrokActions.openFailureGraph(job.getId(), job.getProjectId(),
                                "View the failure graph")
                        : BrokActions.openExecutionGraph(job.getId(), job.getProjectId(),
                                "View execution graph"));

        if (red) {
            b.followUp("Has this happened before?",
                    "A precedent arrives with its resolution attached.", "evaluation:" + job.getId());
        }
        b.followUp("Which provider causes the most failures?",
                "Check whether this is a pattern rather than an incident.", null);
    }

    private void evaluationExplain(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        EvaluationJob job = evaluationFor(record, q, false);
        if (job == null) {
            b.unknown("There is no evaluation here to explain.",
                    "Nothing in this workspace has been evaluated yet, so there is no outcome to read.",
                    "the absence of any evaluation");
            return;
        }
        String status = job.getStatus() != null ? job.getStatus().name() : "PENDING";
        EvaluationRunAggregate aggregate = runRepository.aggregateForJob(job.getId(), EvaluationRunStatus.SUCCEEDED);
        long runs = aggregate != null && aggregate.runCount() != null ? aggregate.runCount() : 0L;
        long passed = aggregate != null && aggregate.passedCount() != null ? aggregate.passedCount() : 0L;

        String state = switch (status) {
            case "FAILED" -> FAILED;
            case "COMPLETED" -> job.getFailedItems() > 0 ? ATTENTION : HEALTHY;
            case "CANCELLED" -> ATTENTION;
            default -> UNKNOWN_STATE;
        };
        b.derived(state, job.getName() + " is " + humanize(status).toLowerCase(Locale.ROOT) + ".",
                runs > 0
                        ? percent(runs == 0 ? 0 : (double) passed / runs) + " of measured items passed."
                        : "No item has produced a measured result yet.",
                "the evaluation record and its runs");

        b.becauseDerived(job.getCompletedItems() + " of " + job.getTotalItems() + " items completed"
                        + (job.getFailedItems() > 0 ? ", " + plural(job.getFailedItems(), "item") + " failed" : "")
                        + ".", "the evaluation's item counters");

        List<String> inputs = new ArrayList<>();
        record.agent(job.getAgentId()).ifPresent(a -> inputs.add("the agent " + a.getName()));
        record.prompt(job.getPromptId()).ifPresent(p -> inputs.add("the prompt " + p.getName()));
        record.dataset(job.getDatasetId()).ifPresent(d -> inputs.add("the dataset " + d.getName()));
        if (!inputs.isEmpty()) {
            b.becauseDerived("It measured " + list(inputs) + ".", "the evaluation's pinned configuration");
        }
        String provider = record.providerNameOf(job);
        if (provider != null) {
            b.becauseDerived("It ran against " + provider
                            + (job.getModel() != null ? " using " + job.getModel() : "") + ".",
                    "the evaluation's recorded provider and model");
        }

        // L-41: quality is never reported without its price.
        if (runs > 0) {
            String latency = aggregate.avgLatencyMs() != null
                    ? Math.round(aggregate.avgLatencyMs()) + " ms average latency" : "latency not recorded";
            String cost = aggregate.totalCost() != null && aggregate.totalCost().compareTo(BigDecimal.ZERO) > 0
                    ? aggregate.totalCost().toPlainString() + " total cost" : "no recorded cost";
            b.becauseDerived("That quality came at " + latency + " and " + cost + ".",
                    "the evaluation's run telemetry");
        }

        b.evaluation(record.refOf(job));
        b.evidence(record.refOf(job));
        linkJobArtifacts(b, record, job);
        record.knowledgeAbout("evaluation", job.getId()).forEach(k -> b.reference(record.refOf(k)));

        ArtifactEvolutionResponse ev = safeEvolution(record, "evaluation", job.getId());
        int affected = ev != null ? ev.impactCount() : 0;
        b.impact(affected > 0
                ? "What this evaluation concludes bears on " + plural(affected, "downstream artifact") + "."
                : "Nothing downstream depends on this evaluation yet.", affected);

        b.recommend("Open the engineering intelligence for " + job.getName(),
                "The intelligence view shows what was observed, claimed and decided off the back of this run.",
                "Connects the number to the decision it supports.", NEAR_CERTAIN, DERIVED,
                List.of("evaluation:" + job.getId()),
                BrokActions.openIntelligence("evaluation", job.getId(), job.getProjectId(),
                        "Open intelligence"));

        b.followUp("Explain this execution graph.", "See the chain this evaluation actually ran.",
                "evaluation:" + job.getId());
        if (job.getPromptId() != null) {
            b.followUp("Which evaluations support this decision?",
                    "Check what the promoted revision rests on.", "prompt:" + job.getPromptId());
        }
    }

    /**
     * "Has this happened before?" — part of what makes a partner different from a search box: Brok goes and
     * looks. It inspects the evaluation record for earlier failures that share an agent, prompt or dataset
     * with the one in question, reads the failed runs of both to compare their recorded causes, then pulls in
     * what the team decided afterwards — the decision, the engineering memory it was recorded with, the
     * revision timeline — so a precedent arrives with its resolution attached. Every statement is read from a
     * real row; when there is no precedent, Brok says this failure is the first of its kind rather than
     * inventing a pattern.
     */
    private void historyPrecedent(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        EvaluationJob current = failingJobFor(record, q);
        if (current == null) {
            noPrecedentToLookFor(b, record, q);
            return;
        }

        boolean troubledNow = BrokRecord.troubled(current);
        List<Subject> ground = groundOf(record, current);
        List<EvaluationJob> precedents = record.precedentsOf(current);

        b.evaluation(record.refOf(current));
        b.evidence(record.refOf(current));
        ground.forEach(s -> b.artifact(subjectRef(record, s)));

        if (precedents.isEmpty()) {
            Instant currentAt = BrokRecord.atOf(current);
            long unrelated = record.jobs().stream()
                    .filter(j -> !j.getId().equals(current.getId()))
                    .filter(BrokRecord::troubled)
                    .filter(j -> BrokRecord.atOf(j) != null && currentAt != null
                            && BrokRecord.atOf(j).isBefore(currentAt))
                    .count();
            if (troubledNow) {
                b.derived(ATTENTION, "No — the record holds no precedent for this failure.",
                        "This is the first recorded failure on the ground " + current.getName()
                                + " covers, so history cannot shorten the diagnosis. The failure graph can.",
                        "every earlier evaluation sharing an artifact with this one");
                b.becauseDerived(unrelated > 0
                                ? plural((int) unrelated, "earlier failure") + " exist"
                                        + (unrelated == 1 ? "s" : "") + " on record, but none shares an "
                                        + "agent, prompt or dataset with " + current.getName() + "."
                                : "No earlier evaluation on record has failed.",
                        "the evaluation record");
                b.impact("A failure without a precedent has to be diagnosed on its own evidence.", 0);
                b.recommend("Open the failure graph for " + current.getName(),
                        "With no history to lean on, the broken stage itself is the fastest evidence.",
                        "Shows whether this new failure is quality or infrastructure.", NEAR_CERTAIN, DERIVED,
                        List.of("evaluation:" + current.getId()),
                        BrokActions.openFailureGraph(current.getId(), current.getProjectId(),
                                "View the failure graph"));
                b.recommend("Investigate " + current.getName() + " with Brok",
                        "First-of-its-kind failures are the ones worth recording properly — the next engineer "
                                + "who asks \"has this happened before?\" will inherit this investigation.",
                        "Turns a novel failure into a documented precedent.", NEAR_CERTAIN, SUGGESTED,
                        List.of("evaluation:" + current.getId()),
                        BrokActions.startInvestigation("evaluation:" + current.getId(), "Start investigation",
                                "Why did " + current.getName() + " fail?"));
                b.followUp("Why did " + current.getName() + " fail?",
                        "Diagnose it directly from its runs.", "evaluation:" + current.getId());
            } else {
                b.derived(HEALTHY, "No — nothing on this ground has ever failed.",
                        current.getName() + " is healthy, and no earlier evaluation sharing its agent, "
                                + "prompt or dataset recorded a failure.",
                        "every evaluation sharing an artifact with this one");
                b.recommend("Open the engineering intelligence for " + current.getName(),
                        "Its full history — observations, decisions, evidence — lives on its "
                                + "intelligence view.",
                        "Confirms the clean history first-hand.", NEAR_CERTAIN, DERIVED,
                        List.of("evaluation:" + current.getId()),
                        BrokActions.openIntelligence("evaluation", current.getId(),
                                current.getProjectId(), "Open intelligence"));
            }
            return;
        }

        EvaluationJob precedent = precedents.get(0);
        String when = agoWord(BrokRecord.atOf(precedent), record.now());
        List<String> sharedNames = sharedGround(record, current, precedent);

        // Same symptom, or merely the same ground? Read the failed runs of both and compare their causes.
        String causeNow = troubledNow ? read(current).dominantError() : null;
        String causeThen = read(precedent).dominantError();
        boolean sameCause = causeNow != null && causeNow.equals(causeThen);

        b.derived(sameCause ? RISK : ATTENTION,
                troubledNow ? "Yes — this has happened before."
                        : "Not now — but the same ground has failed before.",
                precedent.getName() + " failed " + when
                        + (sharedNames.isEmpty() ? "" : " against the same " + list(sharedNames)) + "."
                        + (sameCause ? " The recorded cause is identical, which makes this a recurrence, "
                                + "not a coincidence." : ""),
                "earlier evaluations sharing an artifact with this one");

        b.evaluation(record.refOf(precedent));
        b.evidence(record.refOf(precedent));
        b.becauseDerived(precedent.getName() + " recorded "
                        + (precedent.getStatus() == EvaluationStatus.FAILED
                                ? "a failure" : plural(precedent.getFailedItems(), "failed item"))
                        + " " + when + ".", "the evaluation record");
        if (precedents.size() > 1) {
            b.becauseDerived("In total, " + plural(precedents.size(), "earlier evaluation")
                            + " on the same ground failed: "
                            + list(precedents.stream().limit(3).map(EvaluationJob::getName).toList())
                            + (precedents.size() > 3 ? " and more" : "") + ".",
                    "the evaluation record");
            precedents.stream().skip(1).limit(2).forEach(p -> b.evaluation(record.refOf(p)));
        }
        if (sameCause) {
            b.becauseDerived("Both failures recorded the same cause: " + quoted(causeNow) + ".",
                    "the failed runs of both evaluations");
        } else if (causeNow != null && causeThen != null) {
            b.becauseDerived("The recorded causes differ — this time " + quoted(causeNow)
                            + ", then " + quoted(causeThen)
                            + " — so the precedent is the ground, not the symptom.",
                    "the failed runs of both evaluations");
        }

        // What the team did about it last time — the decision, and the reasoning it was recorded with.
        KnowledgeObject resolution = resolutionAfter(record, ground, precedent);
        if (resolution != null) {
            b.becauseDerived("After that failure, the team recorded: " + quoted(resolution.title()) + ".",
                    "the derived decision record");
            b.decision(record.refOf(resolution));
        } else {
            b.becauseDerived("No engineering decision was recorded after that failure — the precedent was "
                    + "never closed with a documented change.", "the derived decision record");
        }
        ground.forEach(s -> rememberWhy(b, record, s));

        b.impact("A failure with a precedent is one the system has already paid for once — "
                + plural(precedents.size(), "earlier occurrence") + " on record.", precedents.size());

        b.recommend("Open the failure graph for " + current.getName(),
                "Confirm whether today's break is at the same stage as " + precedent.getName() + "'s.",
                "Distinguishes a recurrence from a coincidence.", NEAR_CERTAIN, DERIVED,
                List.of("evaluation:" + current.getId(), "evaluation:" + precedent.getId()),
                BrokActions.openFailureGraph(current.getId(), current.getProjectId(),
                        "View the failure graph"));

        Subject versioned = ground.stream()
                .filter(s -> List.of("prompt", "agent", "dataset").contains(s.type()))
                .findFirst().orElse(null);
        if (versioned != null && safeRevisions(record, versioned).size() > 1) {
            b.recommend("Compare what changed in " + versioned.name() + " between the two failures",
                    "If the artifact moved between the occurrences, the diff is the shortest path to the "
                            + "cause; if it did not, the cause lies outside it.",
                    "Narrows the search to what actually changed.", LIKELY, INFERRED,
                    List.of(versioned.nodeId()),
                    BrokActions.compareRevisions(versioned.type(), versioned.entityId(),
                            versioned.projectId(), "Compare revisions"));
        }

        b.followUp("Why did " + precedent.getName() + " fail?",
                "Read the precedent's own diagnosis.", "evaluation:" + precedent.getId());
        if (resolution != null && versioned != null) {
            b.followUp("What was the reasoning?",
                    "The recorded why behind what the team did last time.", versioned.nodeId());
        }
    }

    /** "Has this happened before?" with nothing troubled in scope — an honest answer, not a shrug. */
    private void noPrecedentToLookFor(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        if (q.hasSubject()) {
            Subject subject = q.subject();
            b.artifact(subjectRef(record, subject));
            List<EvaluationJob> history = record.evaluationsFor(subject.type(), subject.entityId());
            b.derived(history.isEmpty() ? UNKNOWN_STATE : HEALTHY,
                    history.isEmpty()
                            ? "Nothing has ever measured " + subject.name()
                                    + ", so it has no history to search."
                            : "No — " + subject.name() + " has never recorded a failure.",
                    history.isEmpty()
                            ? "History begins with a first evaluation."
                            : plural(history.size(), "evaluation") + " of it completed with nothing failing.",
                    "every evaluation that referenced " + subject.name());
            history.stream().limit(4).forEach(j -> {
                b.evaluation(record.refOf(j));
                b.evidence(record.refOf(j));
            });
            record.knowledgeAbout(subject.type(), subject.entityId()).stream()
                    .filter(k -> "decision".equals(k.type())).limit(3)
                    .forEach(k -> b.decision(record.refOf(k)));
            rememberWhy(b, record, subject);
            b.impact("A clean history is itself evidence — it says the current state was reached "
                    + "without recorded incident.", 0);
            b.recommend("Open the engineering intelligence for " + subject.name(),
                    "Everything the record holds about it — observations, decisions, evidence — in one view.",
                    "Confirms the clean history first-hand.", NEAR_CERTAIN, DERIVED,
                    List.of(subject.nodeId()),
                    BrokActions.openIntelligence(subject.type(), subject.entityId(), subject.projectId(),
                            "Open intelligence"));
            return;
        }
        if (!record.hasEvidence()) {
            b.unknown("There is no history to search yet.",
                    "Precedent is read from evaluations, and nothing has been evaluated.",
                    "the absence of any completed evaluation");
            b.recommend("Run a first evaluation",
                    "History begins with a first measurement.",
                    "Gives the record something to remember.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
            return;
        }
        b.derived(HEALTHY, "Nothing is failing right now, so there is no failure to find a precedent for.",
                plural(record.completed().size(), "evaluation")
                        + " completed and none is currently failing.",
                "the outcome of every evaluation in scope");
        record.completed().stream().limit(4).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });
        b.impact("No open failure means no recurrence to chase.", 0);
        b.recommend("Open the engineering brief",
                "The brief reads the same record, ordered by what would need a human first.",
                "Keeps the healthy reading honest.", NEAR_CERTAIN, DERIVED, List.of(),
                BrokActions.openInsights("Open insights"));
        b.followUp("Summarize what happened this week.",
                "The recent past, read from the record.", null);
    }

    /** The artifacts an evaluation ran against — the ground a precedent must share. */
    private List<Subject> groundOf(BrokRecord record, EvaluationJob job) {
        List<Subject> out = new ArrayList<>();
        if (job.getAgentId() != null) {
            subjectFor(record, "agent", job.getAgentId()).ifPresent(out::add);
        }
        if (job.getPromptId() != null) {
            subjectFor(record, "prompt", job.getPromptId()).ifPresent(out::add);
        }
        if (job.getDatasetId() != null) {
            subjectFor(record, "dataset", job.getDatasetId()).ifPresent(out::add);
        }
        return out;
    }

    private List<String> sharedGround(BrokRecord record, EvaluationJob a, EvaluationJob b) {
        List<String> names = new ArrayList<>();
        if (a.getAgentId() != null && a.getAgentId().equals(b.getAgentId())) {
            record.agent(a.getAgentId()).ifPresent(x -> names.add("agent " + x.getName()));
        }
        if (a.getPromptId() != null && a.getPromptId().equals(b.getPromptId())) {
            record.prompt(a.getPromptId()).ifPresent(x -> names.add("prompt " + x.getName()));
        }
        if (a.getDatasetId() != null && a.getDatasetId().equals(b.getDatasetId())) {
            record.dataset(a.getDatasetId()).ifPresent(x -> names.add("dataset " + x.getName()));
        }
        return names;
    }

    /** The most recent decision recorded about the shared ground after the precedent failed. */
    private KnowledgeObject resolutionAfter(BrokRecord record, List<Subject> ground,
                                            EvaluationJob precedent) {
        Instant at = BrokRecord.atOf(precedent);
        return ground.stream()
                .flatMap(s -> record.knowledgeAbout(s.type(), s.entityId()).stream())
                .filter(k -> "decision".equals(k.type()))
                .filter(k -> at == null || (k.at() != null && k.at().isAfter(at)))
                .max(Comparator.comparing(KnowledgeObject::at,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private void promotionRationale(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Subject subject = versionedSubject(record, q);
        if (subject == null) {
            b.unknown("No promoted revision could be resolved from that question.",
                    "Name the artifact — a prompt or an agent — and Brok will read its promotion record.",
                    "the absence of a resolvable subject");
            listCandidates(b, record, "prompt");
            return;
        }
        List<EngineeringRevision> revisions = safeRevisions(record, subject);
        EngineeringRevision active = revisions.stream().filter(EngineeringRevision::active).findFirst()
                .orElse(null);
        EngineeringRevision named = namedRevision(revisions, q);
        EngineeringRevision target = named != null ? named : active;

        if (target == null) {
            b.unknown(subject.name() + " has no promoted revision.",
                    "Nothing has been made canonical, so there is no promotion to explain.",
                    "the artifact's revision history");
            b.artifact(subjectRef(record, subject));
            return;
        }

        Optional<KnowledgeObject> decision = record.knowledgeAbout(subject.type(), subject.entityId()).stream()
                .filter(k -> "decision".equals(k.type()))
                .filter(k -> k.id().contains(target.id()))
                .findFirst()
                .or(() -> record.knowledgeAbout(subject.type(), subject.entityId()).stream()
                        .filter(k -> "decision".equals(k.type())).findFirst());

        List<EvaluationJob> evidence = record.evaluationsFor(subject.type(), subject.entityId());
        String rationale = decision.map(KnowledgeObject::rationale).filter(r -> !r.isBlank())
                .orElse(target.rationale());

        if (rationale != null && !rationale.isBlank()) {
            b.derived(HEALTHY, subject.name() + " " + target.label() + " was promoted because: " + rationale,
                    "That reason is recorded on the revision itself, so it survives the person who wrote it.",
                    "the revision's recorded rationale");
        } else {
            b.verdict(ATTENTION, subject.name() + " " + target.label()
                            + " was promoted, but no reason was recorded.",
                    "The promotion is real; the reasoning behind it is not in the record, so it cannot be "
                            + "reconstructed later.",
                    DERIVED, NEAR_CERTAIN, "the revision's empty rationale field");
            b.becauseDerived("The revision carries no notes and the derived decision has no rationale.",
                    "the promotion record");
        }

        b.becauseDerived(target.label() + " is " + (target.active() ? "the active revision"
                : "no longer the active revision") + ".", "the artifact's revision timeline");
        if (evidence.isEmpty()) {
            b.becauseDerived("No evaluation has measured " + subject.name()
                    + ", so the promotion rests on judgement rather than evidence.", "the evaluation record");
        } else {
            long failures = evidence.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).count();
            b.becauseDerived(plural(evidence.size(), "evaluation") + " bear" + (evidence.size() == 1 ? "s" : "")
                            + " on it" + (failures > 0 ? ", of which " + failures + " failed" : "") + ".",
                    "evaluations referencing this artifact");
        }

        b.artifact(subjectRef(record, subject));
        rememberWhy(b, record, subject);
        decision.ifPresent(d -> b.decision(record.refOf(d)));
        b.revision(revisionRef(subject, target));
        evidence.stream().limit(MAX_LIST).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });

        int affected = impactCount(record, subject);
        b.impact(affected > 0
                ? "This revision is what " + plural(affected, "downstream artifact") + " currently depends on."
                : "Nothing downstream depends on this artifact yet.", affected);

        b.recommend("Open AI Git for " + subject.name(),
                "The revision timeline shows what changed at each promotion, alongside the reason given.",
                "Turns a version number into a story.", NEAR_CERTAIN, DERIVED, List.of(subject.nodeId()),
                BrokActions.openRevisions(subject.type(), subject.entityId(), subject.projectId(),
                        "Open AI Git"));

        b.followUp("Which evaluations support this decision?", "Check what the promotion rests on.",
                subject.nodeId());
        b.followUp("Should I rollback " + subject.name() + "?",
                "Weigh the promoted revision against its evidence.", subject.nodeId());
    }

    private void rollbackAdvice(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Subject subject = versionedSubject(record, q);
        if (subject == null) {
            b.unknown("No artifact was named, so there is nothing to weigh a rollback against.",
                    "Name the prompt or agent you are considering rolling back.",
                    "the absence of a resolvable subject");
            listCandidates(b, record, "prompt");
            return;
        }
        List<EngineeringRevision> revisions = safeRevisions(record, subject);
        EngineeringRevision active = revisions.stream().filter(EngineeringRevision::active).findFirst()
                .orElse(revisions.isEmpty() ? null : revisions.get(0));
        EngineeringRevision previous = revisions.stream()
                .filter(r -> active == null || !r.id().equals(active.id()))
                .findFirst().orElse(null);

        b.artifact(subjectRef(record, subject));
        rememberWhy(b, record, subject);
        if (active == null) {
            b.unknown(subject.name() + " has no revisions, so there is nothing to roll back to.",
                    "Rollback is a movement between recorded revisions.", "the artifact's revision history");
            return;
        }
        b.revision(revisionRef(subject, active));
        if (previous != null) {
            b.revision(revisionRef(subject, previous));
        }

        Instant promotedAt = active.at();
        List<EvaluationJob> all = record.evaluationsFor(subject.type(), subject.entityId());
        List<EvaluationJob> after = all.stream()
                .filter(j -> promotedAt == null || (BrokRecord.atOf(j) != null
                        && BrokRecord.atOf(j).isAfter(promotedAt)))
                .toList();
        List<EvaluationJob> before = all.stream().filter(j -> !after.contains(j)).toList();
        long failuresAfter = after.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).count();
        long failuresBefore = before.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).count();

        all.stream().limit(MAX_LIST).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });
        int affected = impactCount(record, subject);
        b.impact(affected > 0
                ? "Rolling back changes what " + plural(affected, "downstream artifact") + " depends on."
                : "No other artifact depends on this one, so a rollback is contained.", affected);

        if (after.isEmpty()) {
            b.verdict(UNKNOWN_STATE, "There is no evidence either way, so a rollback would be a guess.",
                    "Nothing has measured " + subject.name() + " since " + active.label()
                            + " was promoted — rolling back would trade an unmeasured revision for another "
                            + "unmeasured revision.",
                    DERIVED, NEAR_CERTAIN, "the absence of evaluations after the promotion");
            b.becauseDerived("No evaluation has run against " + subject.name() + " since "
                    + active.label() + " became active.", "evaluations referencing this artifact");
            b.recommend("Evaluate " + active.label() + " before deciding",
                    "A rollback decided without evidence produces a second undocumented decision.",
                    "Replaces a guess with a measurement.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openIntelligence(subject.type(), subject.entityId(), subject.projectId(),
                            "Open intelligence"));
        } else if (failuresAfter > 0 && failuresBefore == 0) {
            b.verdict(RISK, "The evidence is consistent with rolling back " + subject.name() + ".",
                    plural(failuresAfter, "evaluation") + " failed after " + active.label()
                            + " was promoted, and none failed before it.",
                    INFERRED, failuresAfter >= 3 ? LIKELY : CONSISTENT_WITH,
                    "evaluations before and after the promotion");
            b.becauseDerived(plural(failuresAfter, "failing evaluation") + " ran after the promotion.",
                    "the evaluation record");
            b.becauseDerived("No evaluation failed before it.", "the evaluation record");
            b.becauseInferred("The promotion is the most likely change to explain the difference, though "
                            + "nothing in the record proves it caused the failures.",
                    "the ordering of the promotion and the failures");
            if (previous != null) {
                b.recommend("Compare " + active.label() + " against " + previous.label(),
                        "Seeing exactly what changed is the difference between a rollback and a superstition.",
                        "Identifies the field to revert rather than reverting everything.",
                        LIKELY, INFERRED, after.stream().limit(3).map(j -> "evaluation:" + j.getId()).toList(),
                        BrokActions.compareRevisions(subject.type(), subject.entityId(), subject.projectId(),
                                "Compare revisions"));
            }
        } else if (failuresAfter > 0) {
            b.verdict(ATTENTION, "The case for rolling back is weak — " + subject.name()
                            + " was failing before this revision too.",
                    plural(failuresAfter, "evaluation") + " failed after the promotion, but "
                            + plural(failuresBefore, "evaluation") + " failed before it as well.",
                    INFERRED, LIKELY, "evaluations before and after the promotion");
            b.becauseInferred("Rolling back would return to a revision that was also failing.",
                    "the evaluation record on both sides of the promotion");
            b.recommend("Investigate the failure rather than the revision",
                    "The failures pre-date the promotion, so the revision is unlikely to be the cause.",
                    "Avoids a rollback that changes nothing.", LIKELY, INFERRED, List.of(),
                    BrokActions.openEvolution(subject.type(), subject.entityId(), subject.projectId(),
                            "Open evolution"));
        } else {
            b.verdict(HEALTHY, "The evidence does not support rolling back " + subject.name() + ".",
                    plural(after.size(), "evaluation") + " ran after " + active.label()
                            + " was promoted and none failed.",
                    DERIVED, confidenceFor(after.size()), "evaluations after the promotion");
            b.becauseDerived("Every evaluation since the promotion completed without failing.",
                    "the evaluation record");
            b.recommend("Keep " + active.label() + " promoted",
                    "The recorded evidence since the promotion is clean.",
                    "Avoids discarding a revision that is measurably fine.",
                    confidenceFor(after.size()), DERIVED,
                    after.stream().limit(3).map(j -> "evaluation:" + j.getId()).toList(),
                    BrokActions.openRevisions(subject.type(), subject.entityId(), subject.projectId(),
                            "Open AI Git"));
        }

        b.followUp("What changed between these revisions?", "See the diff before you decide.",
                subject.nodeId());
        b.followUp("Show every artifact affected by " + subject.name() + ".",
                "Know the blast radius before you move.", subject.nodeId());
    }

    private void revisionDiff(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Subject subject = versionedSubject(record, q);
        if (subject == null) {
            b.unknown("No artifact was named, so there are no revisions to compare.",
                    "Name a prompt, agent or dataset and Brok will diff its revisions.",
                    "the absence of a resolvable subject");
            listCandidates(b, record, "prompt");
            return;
        }
        List<EngineeringRevision> revisions = safeRevisions(record, subject);
        if (revisions.size() < 2) {
            b.unknown(subject.name() + " has only " + plural(revisions.size(), "revision")
                            + ", so there is nothing to compare.",
                    "A diff needs two recorded revisions of the same artifact.",
                    "the artifact's revision history");
            b.artifact(subjectRef(record, subject));
            revisions.forEach(r -> b.revision(revisionRef(subject, r)));
            return;
        }

        EngineeringRevision target = namedRevision(revisions, q, 0);
        EngineeringRevision base = namedRevision(revisions, q, 1);
        if (target != null && base == null) {
            // "Compare it with v7" names one revision. The other side of that comparison is the revision
            // that is actually live, which is what the engineer is implicitly comparing against.
            EngineeringRevision live = revisions.stream().filter(EngineeringRevision::active).findFirst()
                    .orElse(revisions.get(0));
            if (!live.id().equals(target.id())) {
                base = target;
                target = live;
            }
        }
        if (target == null || base == null || target.id().equals(base.id())) {
            target = revisions.get(0);
            base = revisions.get(1);
        }
        // Revisions are newest-first, so the one later in the list is the older side of the diff.
        if (revisions.indexOf(target) > revisions.indexOf(base)) {
            EngineeringRevision swap = target;
            target = base;
            base = swap;
        }

        RevisionComparison comparison = intelligence.compare(record.organizationId(), subject.type(),
                subject.entityId(), base.id(), target.id());
        List<RevisionDiff> changed = comparison.diffs().stream()
                .filter(d -> !"unchanged".equals(d.change())).toList();

        if (changed.isEmpty()) {
            b.derived(ATTENTION, base.label() + " and " + target.label() + " are identical.",
                    "Two revisions with the same content means a promotion happened without a change.",
                    "a field-by-field comparison of the two revision snapshots");
        } else {
            b.derived(HEALTHY, plural(changed.size(), "field") + " changed between " + base.label()
                            + " and " + target.label() + ".",
                    "Those fields are the entire difference between the two revisions.",
                    "a field-by-field comparison of the two revision snapshots");
        }
        for (RevisionDiff diff : changed.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived(humanize(diff.field()) + " was " + humanize(diff.change()).toLowerCase(Locale.ROOT)
                            + (diff.before() != null && diff.after() != null
                                    ? ": \"" + shorten(diff.before()) + "\" → \"" + shorten(diff.after()) + "\""
                                    : diff.after() != null ? ": \"" + shorten(diff.after()) + "\"" : "") + ".",
                    "the two revision snapshots");
        }

        b.artifact(subjectRef(record, subject));
        b.revision(revisionRef(subject, base));
        b.revision(revisionRef(subject, target));
        record.evaluationsFor(subject.type(), subject.entityId()).stream().limit(MAX_LIST)
                .forEach(j -> b.evaluation(record.refOf(j)));

        int affected = impactCount(record, subject);
        b.impact(affected > 0
                ? "This change is visible to " + plural(affected, "downstream artifact") + "."
                : "Nothing downstream depends on this artifact yet.", affected);

        b.recommend("Open the comparison in AI Git",
                "The full diff shows the surrounding context each change sits in.",
                "Makes the change reviewable rather than merely visible.", NEAR_CERTAIN, DERIVED,
                List.of(subject.nodeId()),
                BrokActions.compareRevisions(subject.type(), subject.entityId(), subject.projectId(),
                        "Compare revisions"));
        b.followUp("Why was " + subject.name() + " promoted?", "Read the reason recorded at promotion.",
                subject.nodeId());
    }

    private void decisionEvidence(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        KnowledgeObject decision = resolveDecision(record, q);
        if (decision == null) {
            b.unknown("No engineering decision could be resolved from that question.",
                    "A decision here is a promotion or a deprecation — none matched.",
                    "the derived decision record");
            record.knowledgeOfType("decision").stream().limit(5)
                    .forEach(d -> b.followUp("Which evaluations support \"" + d.title() + "\"?",
                            "One of the decisions on record.", d.id()));
            return;
        }
        List<BrokRef> evidence = record.evidenceRefs(decision);
        b.decision(record.refOf(decision));

        if (evidence.isEmpty()) {
            b.verdict(RISK, "Nothing supports this decision.",
                    "\"" + decision.title() + "\" is recorded, but no evaluation stands behind it — the "
                            + "organization is carrying it on judgement alone.",
                    DERIVED, NEAR_CERTAIN, "the decision's evidence links");
            b.becauseDerived("The decision carries no evidence link.", "the derived decision record");
            b.recommend("Evaluate the artifact this decision concerns",
                    "An unsupported decision cannot be defended later, and cannot be safely reversed either.",
                    "Converts a judgement call into a measured one.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openKnowledge(decision.id(), "Open the decision"));
        } else {
            b.verdict(HEALTHY, plural(evidence.size(), "evaluation") + " support"
                            + (evidence.size() == 1 ? "s" : "") + " this decision.",
                    "\"" + decision.title() + "\" rests on evidence that can be opened and checked.",
                    DERIVED, confidenceFor(evidence.size()), "the decision's evidence links");
            for (BrokRef ref : evidence.stream().limit(MAX_LIST).toList()) {
                b.becauseDerived(ref.label() + (ref.outcome() != null
                        ? " — " + humanize(ref.outcome()).toLowerCase(Locale.ROOT) : "") + ".",
                        "an evaluation linked to this decision");
                b.evidence(ref);
            }
            b.recommend("Open the decision and walk its evidence",
                    "Each linked evaluation can be opened, so the decision is checkable rather than asserted.",
                    "Makes the promotion defensible.", confidenceFor(evidence.size()), DERIVED,
                    evidence.stream().map(BrokRef::id).limit(3).toList(),
                    BrokActions.openKnowledge(decision.id(), "Open the decision"));
        }

        if (decision.artifactType() != null && decision.artifactEntityId() != null) {
            record.knowledgeAbout(decision.artifactType(), decision.artifactEntityId()).stream()
                    .filter(k -> !k.id().equals(decision.id())).limit(MAX_LIST)
                    .forEach(k -> b.reference(record.refOf(k)));
        }
        b.impact("This decision is what the artifact's current behaviour rests on.", evidence.size());
        b.followUp("What engineering decisions remain unsupported?",
                "See whether this is an isolated case.", null);
    }

    private void impact(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Subject subject = q.subject();
        if (subject == null) {
            b.unknown("No artifact was named, so there is no blast radius to compute.",
                    "Name an artifact and Brok will read its dependents from the Forge Graph.",
                    "the absence of a resolvable subject");
            listCandidates(b, record, "dataset");
            return;
        }
        ArtifactEvolutionResponse ev = safeEvolution(record, subject.type(), subject.entityId());
        b.artifact(subjectRef(record, subject));
        if (ev == null) {
            b.unknown("The graph holds no relationships for " + subject.name() + " yet.",
                    "Impact is read from real relationships; this artifact has none recorded.",
                    "the engineering graph");
            return;
        }

        List<EvolutionRef> dependents = ev.dependents();
        if (dependents.isEmpty()) {
            b.derived(HEALTHY, "Nothing depends on " + subject.name() + " yet.",
                    "A change here is contained — no other artifact reads from it.",
                    "the engineering graph's relationships");
        } else {
            b.derived(dependents.size() > 3 ? ATTENTION : HEALTHY,
                    plural(dependents.size(), "artifact") + " depend"
                            + (dependents.size() == 1 ? "s" : "") + " directly on " + subject.name() + ".",
                    "Changing it changes what those artifacts are measured against.",
                    "the engineering graph's relationships");
            for (EvolutionRef ref : dependents.stream().limit(MAX_LIST).toList()) {
                b.becauseDerived(ref.name() + " " + (ref.relation() != null ? ref.relation() : "depends on")
                        + " it.", "a real relationship in the engineering graph");
                b.artifact(new BrokRef(ref.id(), ref.type(), ref.name(), null, null, ref.entityId(),
                        ref.projectId(), null));
            }
        }
        for (EvolutionRef dep : ev.dependencies().stream().limit(MAX_LIST).toList()) {
            b.artifact(new BrokRef(dep.id(), dep.type(), dep.name(), "upstream of " + subject.name(),
                    null, dep.entityId(), dep.projectId(), null));
        }
        record.evaluationsFor(subject.type(), subject.entityId()).stream().limit(MAX_LIST)
                .forEach(j -> {
                    b.evaluation(record.refOf(j));
                    b.evidence(record.refOf(j));
                });

        b.impact(ev.impactCount() > 0
                ? "A change here transitively affects " + plural(ev.impactCount(), "artifact") + "."
                : "Nothing is transitively affected by a change here.", ev.impactCount());

        b.recommend("Open the Forge Graph focused on " + subject.name(),
                "Seeing the subgraph is faster than reading a list of names.",
                "Shows the whole neighbourhood a change would touch.", NEAR_CERTAIN, DERIVED,
                List.of(subject.nodeId()),
                BrokActions.openGraph(subject.nodeId(), "Open in Forge Graph"));
        b.recommend("Review " + subject.name() + "'s evolution",
                "Evolution shows lineage and history alongside the dependents.",
                "Puts the blast radius in the context of how the artifact got here.", NEAR_CERTAIN, DERIVED,
                List.of(subject.nodeId()),
                BrokActions.openEvolution(subject.type(), subject.entityId(), subject.projectId(),
                        "Open evolution"));
    }

    private void riskRanking(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        String only = restrictedType(q);
        List<RiskItem> ranked = rank(record, only);
        if (ranked.isEmpty()) {
            b.unknown("There is nothing to rank yet.",
                    only != null ? "No " + only + " exists in this workspace."
                            : "No artifact exists in this workspace.",
                    "the absence of artifacts");
            return;
        }
        RiskItem top = ranked.get(0);
        if (top.score() == 0) {
            b.derived(HEALTHY, "Nothing here carries recorded engineering risk.",
                    "Every artifact has evidence, and none of it is failing.",
                    "evaluations and decisions across this workspace");
        } else {
            b.derived(top.state(), top.subject().name() + " carries the highest engineering risk"
                            + (only != null ? " of your " + only + "s" : "") + ".",
                    top.reason(), "evaluations, promotions and evidence for each artifact");
        }
        for (RiskItem item : ranked.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived(item.subject().name() + " — " + item.reason(),
                    "the evaluations and decisions recorded for it");
            b.artifact(subjectRef(record, item.subject()));
        }
        top.failures().stream().limit(MAX_LIST).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });

        int affected = impactCount(record, top.subject());
        b.impact(affected > 0
                ? "Risk here is not contained: " + plural(affected, "artifact") + " depends on it."
                : "The risk is contained to this artifact.", affected);

        if (top.score() > 0) {
            b.recommend("Investigate " + top.subject().name() + " first",
                    top.reason(),
                    affected > 0 ? "Reduces risk for " + plural(affected, "downstream artifact") + "."
                            : "Removes the largest recorded uncertainty in this workspace.",
                    LIKELY, INFERRED, top.failures().stream().limit(3)
                            .map(j -> "evaluation:" + j.getId()).toList(),
                    BrokActions.openIntelligence(top.subject().type(), top.subject().entityId(),
                            top.subject().projectId(), "Open intelligence"));
            b.followUp("Show every artifact affected by " + top.subject().name() + ".",
                    "Understand how far this risk reaches.", top.subject().nodeId());
        }
    }

    private void knowledgeTopic(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        List<String> terms = List.of(q.topic().split("\\s+")).stream()
                .filter(t -> t.length() >= 3).toList();
        if (terms.isEmpty()) {
            b.unknown("No subject was named, so there is no knowledge to look up.",
                    "Ask about a topic, an artifact or a tag and Brok will search the derived "
                            + "engineering knowledge.",
                    "the absence of a topic in the question");
            return;
        }
        List<KnowledgeObject> matches = record.knowledge().stream()
                .filter(k -> matchesAny(terms, k.title(), k.summary(), k.rationale()))
                .limit(20).toList();

        if (matches.isEmpty()) {
            b.unknown("No engineering knowledge exists about " + list(terms) + ".",
                    "Knowledge in Broks Forge is derived, not written: it appears when an artifact is "
                            + "promoted, evaluated or deprecated. Nothing on record touches this subject.",
                    "a search of the derived knowledge catalog");
            b.becauseDerived("The catalog holds " + plural(record.knowledge().size(), "knowledge object")
                    + ", none matching those words.", "the derived knowledge catalog");
            b.recommend("Browse the knowledge catalog",
                    "Seeing what the organization does know is the fastest way to find the nearest thing.",
                    "Avoids concluding that nothing is known when the wording simply differs.",
                    CONSISTENT_WITH, SUGGESTED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
            return;
        }

        b.derived(HEALTHY, plural(matches.size(), "knowledge object") + " touch"
                        + (matches.size() == 1 ? "es" : "") + " " + list(terms) + ".",
                "Each one is derived from a real engineering act and can be opened.",
                "a search of the derived knowledge catalog");
        for (KnowledgeObject k : matches.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived(humanize(k.type()) + " · " + k.title() + " — " + k.summary(),
                    "a derived knowledge object");
            b.reference(record.refOf(k));
        }
        b.impact("This is everything the record knows on the subject.", matches.size());
        b.recommend("Open the knowledge catalog",
                "The catalog lets you filter by kind and artifact rather than by wording.",
                "Finds adjacent knowledge this search missed.", NEAR_CERTAIN, DERIVED, List.of(),
                BrokActions.openRegistry("Open the registry"));
        b.followUp("Show contradictions in our engineering knowledge.",
                "Check whether any of it disagrees with itself.", null);
    }

    private void periodSummary(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Instant from = record.now().minus(q.window());
        String period = periodWord(q.window());
        List<EvaluationJob> jobs = record.since(from);
        List<KnowledgeObject> knowledge = record.knowledge().stream()
                .filter(k -> k.at() != null && k.at().isAfter(from)).toList();
        List<KnowledgeObject> decisions = knowledge.stream().filter(k -> "decision".equals(k.type())).toList();
        long failed = jobs.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).count();
        long completed = jobs.stream().filter(j -> j.getStatus() == EvaluationStatus.COMPLETED).count();

        if (jobs.isEmpty() && knowledge.isEmpty()) {
            b.unknown("Nothing was recorded " + period + ".",
                    "No evaluation ran and no revision was promoted, so the engineering record is unchanged.",
                    "the engineering record over the window");
            b.followUp("What should my team work on next?",
                    "A quiet week is not the same as a finished one.", null);
            return;
        }

        String state = failed > 0 ? FAILED : (jobs.isEmpty() ? UNKNOWN_STATE : HEALTHY);
        b.derived(state,
                failed > 0
                        ? plural(failed, "evaluation") + " failed " + period + "."
                        : plural(completed, "evaluation") + " completed " + period + " with no failures.",
                decisions.isEmpty()
                        ? "No revision was promoted, so what production runs is unchanged."
                        : plural(decisions.size(), "promotion") + " changed what production runs.",
                "evaluations and promotions inside the window");

        if (completed > 0) {
            b.becauseDerived(plural(completed, "evaluation") + " completed.", "the evaluation record");
        }
        if (failed > 0) {
            b.becauseDerived(plural(failed, "evaluation") + " failed: "
                            + list(jobs.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED)
                                    .limit(5).map(EvaluationJob::getName).toList()) + ".",
                    "the evaluation record");
        }
        for (KnowledgeObject decision : decisions.stream().limit(5).toList()) {
            b.becauseDerived(decision.title() + " — " + decision.summary(), "a derived decision");
            b.decision(record.refOf(decision));
        }
        jobs.stream().limit(MAX_LIST).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });
        knowledge.stream().filter(k -> !"decision".equals(k.type())).limit(MAX_LIST)
                .forEach(k -> b.reference(record.refOf(k)));

        b.impact(failed > 0
                ? plural(failed, "evaluation") + " left conclusions unproven."
                : "Everything measured in the window produced a result.", (int) failed);

        if (failed > 0) {
            EvaluationJob first = jobs.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED)
                    .findFirst().orElseThrow();
            b.recommend("Start with " + first.getName(),
                    "It is the most recent failure in the window, so its cause is the freshest.",
                    "Closes the newest gap in the record.", NEAR_CERTAIN, DERIVED,
                    List.of("evaluation:" + first.getId()),
                    BrokActions.openExecutionGraph(first.getId(), first.getProjectId(),
                            "View execution graph"));
            b.followUp("Why did " + first.getName() + " fail?", "The most recent failure in the window.",
                    "evaluation:" + first.getId());
        } else {
            b.recommend("Review what changed in AI Git",
                    "Promotions are the changes that actually reach production.",
                    "Confirms the week's changes were intended.", NEAR_CERTAIN, DERIVED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
        }
    }

    private void providerFailures(BrokAnswerBuilder b, BrokRecord record) {
        List<Map.Entry<String, Long>> ranked = record.failuresByProvider();
        if (ranked.isEmpty()) {
            if (!record.hasEvidence()) {
                b.unknown("No provider can be blamed, because nothing has been evaluated yet.",
                        "Failure attribution is read from real evaluation outcomes, and there are none.",
                        "the absence of evaluation outcomes");
            } else {
                b.derived(HEALTHY, "No provider is causing failures.",
                        "Every evaluation on record completed — no failure has been attributed to a provider.",
                        "the outcomes of every evaluation in scope");
                b.recommend("Watch provider health over time",
                        "Provider reliability is a trend, not a moment — analytics holds the history.",
                        "Catches a degradation before it becomes a failure.", NEAR_CERTAIN, DERIVED,
                        List.of(), BrokActions.openAnalytics("Open analytics"));
            }
            record.providers().stream().limit(MAX_LIST).forEach(p -> b.artifact(record.refOf(p)));
            return;
        }

        Map.Entry<String, Long> worst = ranked.get(0);
        boolean attributed = !"Unattributed".equals(worst.getKey());
        b.verdict(FAILED,
                attributed ? worst.getKey() + " is behind the most failures." : "Most failures cannot be "
                        + "attributed to a provider.",
                attributed
                        ? plural(worst.getValue(), "failing evaluation") + " reached it."
                        : "The failing evaluations have no provider recorded on their agent, so the platform "
                                + "will not guess which one they used.",
                attributed ? INFERRED : DERIVED,
                worst.getValue() >= 3 ? LIKELY : CONSISTENT_WITH,
                "failing evaluations resolved through the agent's configured provider");

        for (Map.Entry<String, Long> entry : ranked.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived(entry.getKey() + " — " + plural(entry.getValue(), "failing evaluation") + ".",
                    "the failing evaluations and their agents' providers");
        }
        if (attributed) {
            b.becauseInferred("Attribution runs through the agent's configured provider, so a failure caused "
                            + "by a prompt would still be counted here.",
                    "how the platform links an evaluation to a provider");
        }
        record.providers().stream()
                .filter(p -> ranked.stream().anyMatch(e -> e.getKey().equals(p.getName())))
                .forEach(p -> b.artifact(record.refOf(p)));
        record.failing().stream().limit(MAX_LIST).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });
        b.impact("Provider failures make every quality number measured through them unreliable.",
                (int) ranked.stream().mapToLong(e -> e.getValue()).sum());

        b.recommend("Open an affected evaluation's execution graph",
                "The graph distinguishes a provider outage from a prompt that is genuinely wrong.",
                "Stops a transport failure being mistaken for a quality regression.", LIKELY, INFERRED,
                record.failing().stream().limit(3).map(j -> "evaluation:" + j.getId()).toList(),
                record.failing().isEmpty() ? BrokActions.openRegistry("Open the registry")
                        : BrokActions.openExecutionGraph(record.failing().get(0).getId(),
                                record.failing().get(0).getProjectId(), "View execution graph"));
    }

    private void nextWork(BrokAnswerBuilder b, BrokRecord record) {
        List<EvaluationJob> failing = record.failing();
        List<KnowledgeObject> unsupported = record.unsupportedDecisions();
        List<Subject> unmeasured = unmeasuredArtifacts(record);
        List<EvaluationJob> inFlight = record.inFlight();

        String state = HEALTHY;
        if (!failing.isEmpty()) {
            state = FAILED;
        } else if (!unsupported.isEmpty() || !unmeasured.isEmpty()) {
            state = worseOf(state, ATTENTION);
        } else if (!record.hasEvidence()) {
            state = UNKNOWN_STATE;
        }

        int queue = failing.size() + unsupported.size() + unmeasured.size();
        if (queue == 0 && inFlight.isEmpty()) {
            b.derived(record.hasEvidence() ? HEALTHY : UNKNOWN_STATE,
                    record.hasEvidence()
                            ? "Nothing in this workspace needs a decision."
                            : "Nothing needs you yet — but nothing has been evaluated either.",
                    record.hasEvidence()
                            ? "No evaluation is failing, every decision has evidence, and every artifact "
                                    + "has been measured."
                            : "Absence of failure is not health: with no evidence, nothing here is known "
                                    + "to be working.",
                    "evaluations, decisions and evidence across this workspace");
            if (!record.hasEvidence()) {
                b.recommend("Run your first evaluation",
                        "Until something is measured, every statement about quality is an assertion.",
                        "Gives the platform real evidence to reason over.", NEAR_CERTAIN, SUGGESTED,
                        List.of(), BrokActions.openRegistry("Open the registry"));
            }
            return;
        }

        b.derived(state, queue == 0
                        ? plural(inFlight.size(), "investigation") + " is still open."
                        : plural(queue, "thing") + " needs a decision.",
                !failing.isEmpty()
                        ? "Failing evaluations come first: until they pass, everything they measure is unproven."
                        : "Nothing is failing, so the queue is about evidence rather than breakage.",
                "evaluations, decisions and evidence across this workspace");

        int rank = 0;
        for (EvaluationJob job : failing.stream().limit(4).toList()) {
            rank++;
            b.becauseDerived(rank + ". " + job.getName() + " is failing"
                            + (job.getFailedItems() > 0 ? " (" + plural(job.getFailedItems(), "item")
                                    + " did not complete)" : "") + ".",
                    "the evaluation record");
            b.evaluation(record.refOf(job));
            b.evidence(record.refOf(job));
            b.recommend("Investigate " + job.getName(),
                    "A failing evaluation leaves everything it measures unproven.",
                    "Restores confidence in the artifacts it covers.", NEAR_CERTAIN, DERIVED,
                    List.of("evaluation:" + job.getId()),
                    BrokActions.openExecutionGraph(job.getId(), job.getProjectId(),
                            "View execution graph"));
        }
        for (KnowledgeObject decision : unsupported.stream().limit(3).toList()) {
            rank++;
            b.becauseDerived(rank + ". \"" + decision.title() + "\" has no evidence behind it.",
                    "the derived decision record");
            b.decision(record.refOf(decision));
            b.recommend("Evidence the decision \"" + decision.title() + "\"",
                    "An unsupported promotion cannot be defended and cannot be safely reversed.",
                    "Turns a judgement call into a measured one.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openKnowledge(decision.id(), "Open the decision"));
        }
        for (Subject subject : unmeasured.stream().limit(3).toList()) {
            rank++;
            b.becauseDerived(rank + ". " + subject.name() + " has never been evaluated.",
                    "the absence of any evaluation referencing it");
            b.artifact(subjectRef(record, subject));
        }
        if (!unmeasured.isEmpty()) {
            b.recommend("Measure " + unmeasured.get(0).name(),
                    "An artifact with no evidence is an artifact nobody can vouch for.",
                    "Removes an unknown from the workspace.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openIntelligence(unmeasured.get(0).type(), unmeasured.get(0).entityId(),
                            unmeasured.get(0).projectId(), "Open intelligence"));
        }
        for (EvaluationJob job : inFlight.stream().limit(3).toList()) {
            b.becauseDerived(job.getName() + " is still "
                    + humanize(job.getStatus().name()).toLowerCase(Locale.ROOT) + ".", "the evaluation record");
            b.evaluation(record.refOf(job));
        }

        b.impact(failing.isEmpty()
                ? plural(queue, "item") + " is holding evidence back."
                : plural(failing.size(), "evaluation") + " is leaving conclusions unproven.", queue);

        b.followUp("What is the biggest engineering risk right now?",
                "Rank the queue by consequence rather than by order.", null);
    }

    private void performance(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q,
                             UUID actorId, boolean latency) {
        Instant from = record.now().minus(q.window().multipliedBy(2));
        List<EvaluationTrendPoint> trend = mergedTrend(actorId, record, from);
        String subject = latency ? "Latency" : "Spend";

        if (trend.size() < 2) {
            b.unknown("There is not enough recorded telemetry to say whether " + subject.toLowerCase(Locale.ROOT)
                            + " changed.",
                    trend.isEmpty()
                            ? "No evaluation run has recorded timing or cost in this window."
                            : "Only one day of telemetry exists, and a trend needs at least two.",
                    "daily run telemetry over the window");
            b.recommend("Open analytics", "Analytics shows the same telemetry across a longer window.",
                    "May reveal a trend this window is too short to show.", CONSISTENT_WITH, SUGGESTED,
                    List.of(), BrokActions.openAnalytics("Open analytics"));
            return;
        }

        EvaluationTrendPoint latest = trend.get(trend.size() - 1);
        List<EvaluationTrendPoint> earlier = trend.subList(0, trend.size() - 1);
        double latestValue = latency
                ? (latest.avgLatencyMs() != null ? latest.avgLatencyMs() : 0d)
                : perRunCost(latest);
        double baseline = earlier.stream()
                .mapToDouble(p -> latency ? (p.avgLatencyMs() != null ? p.avgLatencyMs() : 0d) : perRunCost(p))
                .filter(v -> v > 0)
                .average().orElse(0d);

        if (baseline <= 0 || latestValue <= 0) {
            b.unknown(subject + " cannot be compared across this window.",
                    "Some days recorded no " + (latency ? "timing" : "cost") + ", so a comparison would be "
                            + "arithmetic rather than evidence.",
                    "daily run telemetry over the window");
            b.recommend("Open analytics", "The analytics view shows exactly which days recorded telemetry.",
                    "Shows where the gap is.", CONSISTENT_WITH, SUGGESTED, List.of(),
                    BrokActions.openAnalytics("Open analytics"));
            return;
        }

        double delta = (latestValue - baseline) / baseline;
        String direction = delta > 0 ? "higher" : "lower";
        boolean material = Math.abs(delta) >= 0.15;
        String unit = latency ? Math.round(latestValue) + " ms" : trimCost(latestValue);
        String baseUnit = latency ? Math.round(baseline) + " ms" : trimCost(baseline);

        if (!material) {
            b.derived(HEALTHY, subject + " has not moved meaningfully.",
                    "The most recent day averaged " + unit + " against a baseline of " + baseUnit + ".",
                    "daily run telemetry over the window");
        } else {
            b.verdict(delta > 0 ? ATTENTION : HEALTHY,
                    subject + " is " + percent(Math.abs(delta)) + " " + direction + " than the baseline.",
                    "The most recent day averaged " + unit + " against " + baseUnit + " over the preceding days.",
                    DERIVED, NEAR_CERTAIN, "daily run telemetry over the window");
        }
        b.becauseDerived("Most recent day: " + unit + " across " + plural(latest.runCount(), "run") + ".",
                "the daily run telemetry");
        b.becauseDerived("Preceding baseline: " + baseUnit + " across " + plural(
                earlier.stream().mapToLong(EvaluationTrendPoint::runCount).sum(), "run") + ".",
                "the daily run telemetry");

        // Attribution is an inference, and is labelled as one.
        List<EvaluationJob> recent = record.since(record.now().minus(q.window()));
        Map<String, Long> byModel = new LinkedHashMap<>();
        for (EvaluationJob job : recent) {
            String key = job.getModel() != null ? job.getModel() : record.providerNameOf(job);
            if (key != null) {
                byModel.merge(key, 1L, Long::sum);
            }
        }
        if (material && delta > 0 && !byModel.isEmpty()) {
            String busiest = byModel.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            b.becauseInferred("Most runs in the window went through " + busiest
                            + ", so it is the most likely place to look — the record does not prove it is "
                            + "the cause.",
                    "the models and providers recorded on runs in the window");
        }

        recent.stream().limit(MAX_LIST).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });
        b.impact(material && delta > 0
                ? "Every evaluation run in this workspace pays this " + (latency ? "delay" : "price") + "."
                : (latency ? "Timing" : "Spend") + " is stable, so quality numbers are comparable across "
                        + "the window.", recent.size());

        b.recommend("Open analytics",
                latency
                        ? "Quality is never worth reading without its price — analytics shows pass rate, "
                                + "latency and cost together."
                        : "Analytics shows spend beside the quality it bought.",
                "Prevents optimising one at the silent expense of the other.", NEAR_CERTAIN, DERIVED,
                List.of(), BrokActions.openAnalytics("Open analytics"));
        b.followUp(latency ? "Which provider causes the most failures?" : "Why did latency increase?",
                "Cost, latency and reliability usually move together.", null);
    }

    private void unsupportedDecisions(BrokAnswerBuilder b, BrokRecord record) {
        List<KnowledgeObject> unsupported = record.unsupportedDecisions();
        List<KnowledgeObject> all = record.knowledgeOfType("decision");

        if (all.isEmpty()) {
            b.unknown("No engineering decision has been recorded yet.",
                    "Decisions appear when a revision is promoted or an artifact is deprecated.",
                    "the derived decision record");
            return;
        }
        if (unsupported.isEmpty()) {
            b.derived(HEALTHY, "Every recorded decision has evidence behind it.",
                    plural(all.size(), "decision") + " on record, each linked to at least one evaluation.",
                    "the evidence links on every derived decision");
            all.stream().limit(MAX_LIST).forEach(d -> b.decision(record.refOf(d)));
            return;
        }

        b.derived(RISK, plural(unsupported.size(), "decision") + " of " + all.size()
                        + " has no evidence behind it.",
                "Those promotions are being carried on judgement — they cannot be defended later and cannot "
                        + "be safely reversed.",
                "the evidence links on every derived decision");
        for (KnowledgeObject decision : unsupported.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived("\"" + decision.title() + "\" — " + decision.summary(),
                    "a derived decision with no evidence link");
            b.decision(record.refOf(decision));
            if (decision.artifactType() != null && decision.artifactEntityId() != null) {
                subjectFor(record, decision.artifactType(), decision.artifactEntityId())
                        .ifPresent(s -> b.artifact(subjectRef(record, s)));
            }
        }
        b.impact(plural(unsupported.size(), "artifact") + " is running on an unevidenced decision.",
                unsupported.size());

        KnowledgeObject first = unsupported.get(0);
        b.recommend("Evidence \"" + first.title() + "\" first",
                "It is the decision with the widest reach among the unsupported ones on record.",
                "Converts the organization's largest unbacked assumption into a measurement.",
                NEAR_CERTAIN, SUGGESTED, List.of(),
                BrokActions.openKnowledge(first.id(), "Open the decision"));
        b.followUp("Show contradictions in our engineering knowledge.",
                "Unsupported decisions and contradictions usually travel together.", null);
    }

    private void contradictions(BrokAnswerBuilder b, BrokRecord record) {
        List<BrokRecord.Tension> tensions = record.tensions();
        if (tensions.isEmpty()) {
            b.derived(record.knowledge().isEmpty() ? UNKNOWN_STATE : HEALTHY,
                    record.knowledge().isEmpty()
                            ? "There is no engineering knowledge yet, so nothing can contradict anything."
                            : "Nothing in the engineering record contradicts itself.",
                    record.knowledge().isEmpty()
                            ? "Knowledge appears once artifacts are promoted and evaluated."
                            : "Every claim on record sits beside evidence that agrees with it.",
                    "each claim compared against the evaluations about the same artifact");
            return;
        }

        b.verdict(RISK, plural(tensions.size(), "claim") + " sit"
                        + (tensions.size() == 1 ? "s" : "") + " uneasily beside the evidence.",
                "A claim asserting a settled revision, while evaluations of that same artifact are failing, "
                        + "means the record is telling you two different things.",
                INFERRED, LIKELY, "each claim compared against the evaluations about the same artifact");

        for (BrokRecord.Tension tension : tensions.stream().limit(MAX_LIST).toList()) {
            KnowledgeObject claim = tension.claim();
            b.becauseDerived("\"" + claim.title() + "\" is on record.", "a derived claim");
            b.becauseDerived(plural(tension.failures().size(), "evaluation")
                            + " of the same artifact failed: "
                            + list(tension.failures().stream().limit(3).map(EvaluationJob::getName).toList())
                            + ".", "the evaluation record");
            b.becauseInferred("A failing evaluation does not automatically invalidate the promotion — but "
                            + "the claim should not be read as settled while it stands.",
                    "the relationship between claims and evidence");
            b.knowledge(record.refOf(claim));
            tension.failures().stream().limit(3).forEach(j -> {
                b.evaluation(record.refOf(j));
                b.evidence(record.refOf(j));
            });
        }
        b.impact("Decisions taken on these claims rest on evidence that disagrees with them.", tensions.size());

        BrokRecord.Tension first = tensions.get(0);
        b.recommend("Reconcile \"" + first.claim().title() + "\"",
                "Either the failing evaluation is an infrastructure problem, or the claim is no longer true. "
                        + "Both answers are useful; leaving it unresolved is not.",
                "Removes the record's largest internal disagreement.", LIKELY, INFERRED,
                first.failures().stream().limit(3).map(j -> "evaluation:" + j.getId()).toList(),
                BrokActions.openKnowledge(first.claim().id(), "Open the claim"));
        b.followUp("Why did " + first.failures().get(0).getName() + " fail?",
                "Resolving the failure resolves the contradiction.",
                "evaluation:" + first.failures().get(0).getId());
    }

    private void incompleteInvestigations(BrokAnswerBuilder b, BrokRecord record) {
        List<EvaluationJob> inFlight = record.inFlight();
        List<EvaluationJob> unclosed = unclosedFailures(record);
        List<EvaluationJob> partial = record.completedWithFailures();

        int total = inFlight.size() + unclosed.size() + partial.size();
        if (total == 0) {
            b.derived(record.hasEvidence() ? HEALTHY : UNKNOWN_STATE,
                    record.hasEvidence()
                            ? "No investigation is left open."
                            : "There are no investigations, because nothing has been evaluated.",
                    record.hasEvidence()
                            ? "Every evaluation finished, and every failure was followed by a passing run."
                            : "An investigation begins with a measurement.",
                    "the status of every evaluation in scope");
            return;
        }

        b.derived(unclosed.isEmpty() ? ATTENTION : RISK,
                plural(total, "investigation") + " is still open.",
                unclosed.isEmpty()
                        ? "None of them is a failure left unanswered."
                        : plural(unclosed.size(), "failure") + " was never followed by a passing run.",
                "the status and ordering of every evaluation in scope");

        for (EvaluationJob job : unclosed.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived(job.getName() + " failed and nothing has passed since.",
                    "the evaluation record for this artifact");
            b.evaluation(record.refOf(job));
            b.evidence(record.refOf(job));
        }
        for (EvaluationJob job : inFlight.stream().limit(MAX_LIST).toList()) {
            b.becauseDerived(job.getName() + " is still "
                            + humanize(job.getStatus().name()).toLowerCase(Locale.ROOT) + " — "
                            + job.getCompletedItems() + " of " + job.getTotalItems() + " items so far.",
                    "the evaluation's progress counters");
            b.evaluation(record.refOf(job));
        }
        for (EvaluationJob job : partial.stream().limit(4).toList()) {
            b.becauseDerived(job.getName() + " completed but left " + plural(job.getFailedItems(), "item")
                    + " unmeasured.", "the evaluation's item counters");
            b.evaluation(record.refOf(job));
        }
        b.impact(plural(total, "investigation") + " is holding a conclusion open.", total);

        if (!unclosed.isEmpty()) {
            EvaluationJob first = unclosed.get(0);
            b.recommend("Close out " + first.getName(),
                    "A failure with no passing run after it is an open question the record still carries.",
                    "Turns an unanswered failure into a resolved one.", NEAR_CERTAIN, DERIVED,
                    List.of("evaluation:" + first.getId()),
                    BrokActions.openExecutionGraph(first.getId(), first.getProjectId(),
                            "View execution graph"));
            b.followUp("Why did " + first.getName() + " fail?", "The oldest unanswered failure.",
                    "evaluation:" + first.getId());
        } else if (!partial.isEmpty()) {
            EvaluationJob first = partial.get(0);
            b.recommend("Read the failed items of " + first.getName(),
                    "The failure graph already isolates the items that produced no result.",
                    "Decides whether the partial failure matters or can be closed.", NEAR_CERTAIN, DERIVED,
                    List.of("evaluation:" + first.getId()),
                    BrokActions.openFailureGraph(first.getId(), first.getProjectId(),
                            "View the failure graph"));
        } else if (!inFlight.isEmpty()) {
            EvaluationJob first = inFlight.get(0);
            b.recommend("Watch " + first.getName() + " to completion",
                    "An in-flight evaluation is a conclusion the record is still earning.",
                    "Closes the investigation the moment the result lands.", NEAR_CERTAIN, DERIVED,
                    List.of("evaluation:" + first.getId()),
                    BrokActions.openEvaluation(first.getId(), first.getProjectId(),
                            "Open the evaluation"));
        }
    }

    private void systemState(BrokAnswerBuilder b, BrokRecord record) {
        List<EvaluationJob> failing = record.failing();
        List<EvaluationJob> completed = record.completed();
        List<KnowledgeObject> unsupported = record.unsupportedDecisions();

        if (!record.hasEvidence()) {
            b.unknown("Your engineering system has not been evaluated yet.",
                    "There is no evidence to reason about, so nothing here is known to be healthy. Absence "
                            + "of failure is not health.",
                    "the absence of any completed evaluation");
        } else if (!failing.isEmpty()) {
            b.derived(FAILED, plural(failing.size(), "evaluation") + " is failing.",
                    "Until they pass, the quality of the artifacts they measure is unproven.",
                    "recent evaluation outcomes");
        } else if (!unsupported.isEmpty()) {
            b.derived(ATTENTION, "Nothing is failing, but " + plural(unsupported.size(), "decision")
                            + " has no evidence behind it.",
                    "The system runs; parts of why it runs the way it does are not recorded.",
                    "evaluation outcomes and decision evidence");
        } else {
            b.derived(HEALTHY, "Your engineering system is healthy.",
                    plural(completed.size(), "evaluation") + " completed, none failing, and every decision "
                            + "has evidence behind it.",
                    "evaluation outcomes and decision evidence");
        }

        b.becauseDerived(plural(record.agents().size(), "agent") + ", "
                        + plural(record.prompts().size(), "prompt") + " and "
                        + plural(record.datasets().size(), "dataset") + " are registered.",
                "the engineering registry");
        b.becauseDerived(plural(record.jobs().size(), "evaluation") + " on record, "
                        + failing.size() + " failing and " + record.inFlight().size() + " still running.",
                "the evaluation record");
        b.becauseDerived(plural(record.knowledge().size(), "knowledge object")
                + " has been derived from those acts.", "the derived knowledge catalog");

        failing.stream().limit(4).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });
        record.knowledgeOfType("decision").stream().limit(4).forEach(d -> b.decision(record.refOf(d)));
        b.impact(failing.isEmpty()
                ? "Nothing is currently blocking a conclusion."
                : plural(failing.size(), "evaluation") + " is leaving conclusions unproven.", failing.size());

        b.recommend("Open the engineering brief",
                "The brief is the same reading, ordered by what needs a human first.",
                "Turns the state of the system into a queue.", NEAR_CERTAIN, DERIVED, List.of(),
                BrokActions.openInsights("Open insights"));
        b.followUp("What should my team work on next?", "Convert this reading into an ordered queue.", null);
    }

    private void artifactExplain(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        Subject subject = q.subject();
        if (subject == null) {
            cannotAnswer(b, q);
            return;
        }
        List<EvaluationJob> evidence = record.evaluationsFor(subject.type(), subject.entityId());
        List<KnowledgeObject> knowledge = record.knowledgeAbout(subject.type(), subject.entityId());
        List<KnowledgeObject> decisions = knowledge.stream()
                .filter(k -> "decision".equals(k.type())).toList();
        long failures = evidence.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).count();

        String state;
        String headline;
        String consequence;
        if (evidence.isEmpty()) {
            state = UNKNOWN_STATE;
            headline = subject.name() + " has never been evaluated.";
            consequence = "There is no evidence about it, so nothing here is known to be working — only "
                    + "that it exists.";
        } else if (failures > 0) {
            state = FAILED;
            headline = subject.name() + " has " + plural(failures, "failing evaluation") + " against it.";
            consequence = "Conclusions that rest on this artifact are unproven while those failures stand.";
        } else {
            state = HEALTHY;
            headline = subject.name() + " is measured and passing.";
            consequence = plural(evidence.size(), "evaluation") + " bear"
                    + (evidence.size() == 1 ? "s" : "") + " on it and none is failing.";
        }
        b.verdict(state, headline, consequence, DERIVED, confidenceFor(evidence.size()),
                "evaluations and derived knowledge about this artifact");

        b.becauseDerived("It is " + article(subject.type()) + " " + subject.type()
                + " in " + (record.projectName() != null ? record.projectName() : "this organization") + ".",
                "the engineering registry");
        if (!decisions.isEmpty()) {
            b.becauseDerived(plural(decisions.size(), "decision") + " has been recorded about it, most "
                            + "recently \"" + decisions.get(0).title() + "\".",
                    "the derived decision record");
        }
        if (!evidence.isEmpty()) {
            b.becauseDerived(plural(evidence.size(), "evaluation") + " reference"
                            + (evidence.size() == 1 ? "s" : "") + " it"
                            + (failures > 0 ? ", of which " + failures + " failed" : "") + ".",
                    "the evaluation record");
        }

        b.artifact(subjectRef(record, subject));
        rememberWhy(b, record, subject);
        knowledge.stream().limit(MAX_LIST).forEach(k -> b.reference(record.refOf(k)));
        evidence.stream().limit(MAX_LIST).forEach(j -> {
            b.evaluation(record.refOf(j));
            b.evidence(record.refOf(j));
        });
        safeRevisions(record, subject).stream().limit(4).forEach(r -> b.revision(revisionRef(subject, r)));

        int affected = impactCount(record, subject);
        b.impact(affected > 0
                ? "A change here affects " + plural(affected, "downstream artifact") + "."
                : "Nothing downstream depends on it yet.", affected);

        b.recommend("Open " + subject.name() + "'s engineering intelligence",
                "Intelligence shows what was observed, claimed, decided and evidenced about it, in one place.",
                "Replaces reading five screens with reading one.", NEAR_CERTAIN, DERIVED,
                List.of(subject.nodeId()),
                BrokActions.openIntelligence(subject.type(), subject.entityId(), subject.projectId(),
                        "Open intelligence"));
        if (evidence.isEmpty()) {
            b.recommend("Evaluate " + subject.name(),
                    "An artifact with no evidence cannot be vouched for by anybody.",
                    "Removes an unknown from the workspace.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openGraph(subject.nodeId(), "Open in Forge Graph"));
        }
        b.followUp("Show every artifact affected by " + subject.name() + ".",
                "Understand the blast radius before changing it.", subject.nodeId());
        if (!decisions.isEmpty()) {
            b.followUp("Which evaluations support this decision?",
                    "Check what the most recent decision rests on.", decisions.get(0).id());
        }
    }

    // ================================================================================================
    // Honest non-answers
    // ================================================================================================

    private BrokAnswerBuilder cannotAnswer(BrokAnswerBuilder b, BrokQuestion q) {
        b.unknown("The engineering record cannot answer that.",
                "Brok only says things it can trace to a real evaluation, promotion, artifact or "
                        + "relationship. Nothing on record addresses this question.",
                "the boundary of the engineering record");
        b.becauseDerived("No engineering intent was resolved from the question, and nothing in the record "
                + "matched it.", "intent resolution against the engineering record");
        BrokIntent.answerable().forEach(question ->
                b.followUp(question, "A question the engineering record can answer.", null));
        return b;
    }

    private BrokAnswerBuilder emptyRecord(BrokAnswerBuilder b) {
        b.unknown("There is no engineering here to reason about yet.",
                "Register an agent, a prompt or a dataset and Brok will start reading what you build, "
                        + "what you decide and what you learn.",
                "an empty engineering record");
        b.recommend("Open the registry",
                "The registry is where artifacts enter the engineering record.",
                "Gives Brok something real to reason over.", NEAR_CERTAIN, SUGGESTED, List.of(),
                BrokActions.openRegistry("Open the registry"));
        b.followUp("What is the biggest engineering risk right now?",
                "Ask again once something has been registered and evaluated.", null);
        return b;
    }

    /** Several artifacts match the kind that was named — asking is more honest than picking one. */
    private BrokAnswerBuilder ambiguous(BrokAnswerBuilder b, BrokQuestion q) {
        List<Subject> candidates = q.ambiguous();
        b.unknown("Which one do you mean?",
                "The question names a kind that matches " + plural(candidates.size(), "artifact")
                        + ". Answering about the wrong one would be worse than asking.",
                "the artifacts matching the kind named in the question");
        for (Subject candidate : candidates) {
            b.becauseDerived(candidate.name() + " is a candidate.", "the engineering registry");
            b.followUp(q.raw().replace("?", "") + " — " + candidate.name() + "?",
                    "Ask the same question about " + candidate.name() + ".", candidate.nodeId());
        }
        return b;
    }

    // ================================================================================================
    // Resolution helpers
    // ================================================================================================

    private static boolean needsSubject(BrokIntent intent) {
        return switch (intent) {
            case PROMOTION_RATIONALE, PROMOTION_ADVICE, ROLLBACK_ADVICE, REVISION_DIFF, IMPACT,
                 ARTIFACT_EXPLAIN, EVALUATION_EXPLAIN, EXECUTION_EXPLAIN, MEMORY_WHY -> true;
            default -> false;
        };
    }

    /** The failing evaluation a "why did it fail" question is about. */
    private EvaluationJob failingJobFor(BrokRecord record, BrokQuestion q) {
        Subject subject = q.subject();
        if (subject != null && "evaluation".equals(subject.type())) {
            return record.job(subject.entityId()).orElse(null);
        }
        if (subject != null) {
            List<EvaluationJob> related = record.evaluationsFor(subject.type(), subject.entityId());
            return related.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).findFirst()
                    .orElse(related.stream().filter(j -> j.getFailedItems() > 0).findFirst().orElse(null));
        }
        return record.failing().stream().findFirst()
                .orElse(record.completedWithFailures().stream().findFirst().orElse(null));
    }

    /** The evaluation an "explain this" question is about, preferring failures when asked about redness. */
    private EvaluationJob evaluationFor(BrokRecord record, BrokQuestion q, boolean preferFailing) {
        Subject subject = q.subject();
        if (subject != null && "evaluation".equals(subject.type())) {
            return record.job(subject.entityId()).orElse(null);
        }
        if (subject != null) {
            List<EvaluationJob> related = record.evaluationsFor(subject.type(), subject.entityId());
            if (!related.isEmpty()) {
                return preferFailing
                        ? related.stream().filter(j -> j.getStatus() == EvaluationStatus.FAILED).findFirst()
                                .orElse(related.get(0))
                        : related.get(0);
            }
        }
        if (preferFailing && !record.failing().isEmpty()) {
            return record.failing().get(0);
        }
        List<EvaluationJob> withResults = record.jobs().stream()
                .filter(j -> j.getStatus() == EvaluationStatus.COMPLETED
                        || j.getStatus() == EvaluationStatus.FAILED)
                .sorted(Comparator.comparing(BrokRecord::atOf,
                        Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed())
                .toList();
        return withResults.isEmpty() ? null : withResults.get(0);
    }

    /** A subject that can carry revisions — prompts, agents and datasets are versioned; providers are not. */
    private Subject versionedSubject(BrokRecord record, BrokQuestion q) {
        Subject subject = q.subject();
        if (subject == null) {
            return null;
        }
        if (List.of("prompt", "agent", "dataset").contains(subject.type())) {
            return subject;
        }
        if ("evaluation".equals(subject.type())) {
            // The question is about a promotion, but focus is an evaluation: fall back to what it measured.
            EvaluationJob job = record.job(subject.entityId()).orElse(null);
            if (job != null && job.getPromptId() != null) {
                return subjectFor(record, "prompt", job.getPromptId()).orElse(null);
            }
            if (job != null && job.getAgentId() != null) {
                return subjectFor(record, "agent", job.getAgentId()).orElse(null);
            }
        }
        return null;
    }

    private Optional<Subject> subjectFor(BrokRecord record, String type, UUID entityId) {
        return switch (type) {
            case "agent" -> record.agent(entityId)
                    .map(a -> new Subject("agent", a.getId(), a.getName(), a.getProjectId()));
            case "prompt" -> record.prompt(entityId)
                    .map(p -> new Subject("prompt", p.getId(), p.getName(), p.getProjectId()));
            case "dataset" -> record.dataset(entityId)
                    .map(d -> new Subject("dataset", d.getId(), d.getName(), d.getProjectId()));
            case "evaluation" -> record.job(entityId)
                    .map(j -> new Subject("evaluation", j.getId(), j.getName(), j.getProjectId()));
            case "provider" -> record.provider(entityId)
                    .map(p -> new Subject("provider", p.getId(), p.getName(), p.getProjectId()));
            default -> Optional.empty();
        };
    }

    private KnowledgeObject resolveDecision(BrokRecord record, BrokQuestion q) {
        if (q.focusKnowledge() != null && "decision".equals(q.focusKnowledge().type())) {
            return q.focusKnowledge();
        }
        if (q.hasSubject()) {
            Optional<KnowledgeObject> about = record
                    .knowledgeAbout(q.subject().type(), q.subject().entityId()).stream()
                    .filter(k -> "decision".equals(k.type())).findFirst();
            if (about.isPresent()) {
                return about.get();
            }
        }
        if (q.focusKnowledge() != null) {
            Optional<KnowledgeObject> linked = q.focusKnowledge().links().stream()
                    .filter(l -> "decision".equals(l.type()))
                    .map(KnowledgeLink::id)
                    .map(record::knowledgeById)
                    .filter(Optional::isPresent).map(Optional::get)
                    .findFirst();
            if (linked.isPresent()) {
                return linked.get();
            }
        }
        return record.knowledgeOfType("decision").stream().findFirst().orElse(null);
    }

    private static String restrictedType(BrokQuestion q) {
        String text = q.raw() == null ? "" : q.raw().toLowerCase(Locale.ROOT);
        for (String type : List.of("prompt", "agent", "dataset", "evaluation")) {
            if (text.contains(type)) {
                return type;
            }
        }
        return null;
    }

    private void listCandidates(BrokAnswerBuilder b, BrokRecord record, String type) {
        List<Subject> candidates = switch (type) {
            case "prompt" -> record.prompts().stream()
                    .map(p -> new Subject("prompt", p.getId(), p.getName(), p.getProjectId())).toList();
            case "dataset" -> record.datasets().stream()
                    .map(d -> new Subject("dataset", d.getId(), d.getName(), d.getProjectId())).toList();
            default -> record.agents().stream()
                    .map(a -> new Subject("agent", a.getId(), a.getName(), a.getProjectId())).toList();
        };
        candidates.stream().limit(5).forEach(c -> {
            b.artifact(subjectRef(record, c));
            b.followUp("Explain " + c.name() + ".", "One of the artifacts on record.", c.nodeId());
        });
    }

    // ================================================================================================
    // Derivations shared across intents
    // ================================================================================================

    private record RiskItem(Subject subject, int score, String state, String reason,
                            List<EvaluationJob> failures) {
    }

    private List<RiskItem> rank(BrokRecord record, String only) {
        List<Subject> subjects = new ArrayList<>();
        if (only == null || "prompt".equals(only)) {
            record.prompts().forEach(p ->
                    subjects.add(new Subject("prompt", p.getId(), p.getName(), p.getProjectId())));
        }
        if (only == null || "agent".equals(only)) {
            record.agents().forEach(a ->
                    subjects.add(new Subject("agent", a.getId(), a.getName(), a.getProjectId())));
        }
        if (only == null || "dataset".equals(only)) {
            record.datasets().forEach(d ->
                    subjects.add(new Subject("dataset", d.getId(), d.getName(), d.getProjectId())));
        }

        List<RiskItem> items = new ArrayList<>();
        for (Subject subject : subjects) {
            List<EvaluationJob> evaluations = record.evaluationsFor(subject.type(), subject.entityId());
            List<EvaluationJob> failures = evaluations.stream()
                    .filter(j -> j.getStatus() == EvaluationStatus.FAILED).toList();
            List<EvaluationJob> partial = evaluations.stream()
                    .filter(j -> j.getStatus() == EvaluationStatus.COMPLETED && j.getFailedItems() > 0).toList();
            boolean unsupported = record.knowledgeAbout(subject.type(), subject.entityId()).stream()
                    .anyMatch(k -> "decision".equals(k.type())
                            && k.links().stream().noneMatch(l -> "evidence".equals(l.type())));
            int reach = impactCount(record, subject);

            int score = failures.size() * 100 + partial.size() * 25 + (unsupported ? 30 : 0)
                    + (evaluations.isEmpty() ? 40 : 0) + Math.min(reach, 10) * 3;
            String state;
            String reason;
            if (!failures.isEmpty()) {
                state = FAILED;
                reason = plural(failures.size(), "evaluation") + " against it "
                        + (failures.size() == 1 ? "is" : "are") + " failing"
                        + (reach > 0 ? ", and " + plural(reach, "artifact") + " depends on it" : "") + ".";
            } else if (evaluations.isEmpty()) {
                state = UNKNOWN_STATE;
                reason = "nothing has ever measured it"
                        + (reach > 0 ? ", yet " + plural(reach, "artifact") + " depends on it" : "") + ".";
            } else if (!partial.isEmpty()) {
                state = ATTENTION;
                reason = plural(partial.size(), "evaluation") + " completed with unmeasured items.";
            } else if (unsupported) {
                state = ATTENTION;
                reason = "its promotion has no evidence behind it.";
            } else {
                state = HEALTHY;
                reason = plural(evaluations.size(), "evaluation") + " passed against it.";
            }
            items.add(new RiskItem(subject, score, state, reason, failures));
        }
        return items.stream()
                .sorted(Comparator.comparingInt(RiskItem::score).reversed())
                .toList();
    }

    /** Artifacts no evaluation has ever referenced — an unknown, never a healthy default (L-34). */
    private List<Subject> unmeasuredArtifacts(BrokRecord record) {
        List<Subject> out = new ArrayList<>();
        record.agents().forEach(a -> {
            if (record.evaluationsFor("agent", a.getId()).isEmpty()) {
                out.add(new Subject("agent", a.getId(), a.getName(), a.getProjectId()));
            }
        });
        record.prompts().forEach(p -> {
            if (record.evaluationsFor("prompt", p.getId()).isEmpty()) {
                out.add(new Subject("prompt", p.getId(), p.getName(), p.getProjectId()));
            }
        });
        return out;
    }

    /** Failures with no later passing evaluation of the same artifact — questions nobody answered. */
    private List<EvaluationJob> unclosedFailures(BrokRecord record) {
        List<EvaluationJob> out = new ArrayList<>();
        for (EvaluationJob failure : record.failing()) {
            UUID agentId = failure.getAgentId();
            Instant at = BrokRecord.atOf(failure);
            boolean closed = agentId != null && record.evaluationsFor("agent", agentId).stream()
                    .anyMatch(j -> j.getStatus() == EvaluationStatus.COMPLETED
                            && j.getFailedItems() == 0
                            && BrokRecord.atOf(j) != null && at != null
                            && BrokRecord.atOf(j).isAfter(at));
            if (!closed) {
                out.add(failure);
            }
        }
        return out;
    }

    /** A reading of why an evaluation's runs failed, sampled from the real rows. */
    private record FailureReading(int failedRuns, int sampled, String dominantError, int dominantCount,
                                  Integer httpStatus, List<BrokRef> samples) {

        /** Transport-level failures point at infrastructure rather than at answer quality. */
        boolean looksLikeInfrastructure() {
            if (httpStatus != null && (httpStatus == 401 || httpStatus == 403 || httpStatus == 429
                    || httpStatus >= 500)) {
                return true;
            }
            if (dominantError == null) {
                return false;
            }
            String text = dominantError.toLowerCase(Locale.ROOT);
            return text.contains("timeout") || text.contains("timed out") || text.contains("connect")
                    || text.contains("unauthorized") || text.contains("rate limit")
                    || text.contains("unavailable") || text.contains("dns") || text.contains("refused");
        }

        String stage() {
            if (httpStatus != null && (httpStatus == 401 || httpStatus == 403)) {
                return "provider authentication";
            }
            if (httpStatus != null && httpStatus == 429) {
                return "provider rate limit";
            }
            if (httpStatus != null && httpStatus >= 500) {
                return "provider response";
            }
            if (looksLikeInfrastructure()) {
                return "agent call";
            }
            return "metric evaluation";
        }
    }

    private FailureReading read(EvaluationJob job) {
        List<EvaluationRun> runs = runRepository.findByEvaluationJobIdAndStatusOrderBySequenceAsc(
                job.getId(), EvaluationRunStatus.FAILED, PageRequest.of(0, MAX_FAILED_RUN_SAMPLE));
        if (runs.isEmpty()) {
            return new FailureReading(job.getFailedItems(), 0, null, 0, null, List.of());
        }
        Map<String, Integer> byError = new LinkedHashMap<>();
        Integer httpStatus = null;
        List<BrokRef> samples = new ArrayList<>();
        for (EvaluationRun run : runs) {
            String error = run.getError() != null && !run.getError().isBlank()
                    ? shorten(run.getError()) : "no error text recorded";
            byError.merge(error, 1, Integer::sum);
            if (httpStatus == null && run.getHttpStatus() != null) {
                httpStatus = run.getHttpStatus();
            }
            samples.add(new BrokRef("run:" + run.getId(), "run",
                    "Run #" + run.getSequence() + " of " + job.getName(), error,
                    run.getHttpStatus() != null ? "HTTP " + run.getHttpStatus() : "FAILED",
                    job.getId(), job.getProjectId(),
                    run.getCompletedAt() != null ? run.getCompletedAt() : run.getStartedAt()));
        }
        Map.Entry<String, Integer> dominant = byError.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);
        return new FailureReading(Math.max(job.getFailedItems(), runs.size()), runs.size(),
                dominant != null ? dominant.getKey() : null, dominant != null ? dominant.getValue() : 0,
                httpStatus, samples.stream().limit(4).toList());
    }

    /** Daily telemetry across the projects in scope, count-weighted so the average stays honest. */
    private List<EvaluationTrendPoint> mergedTrend(UUID actorId, BrokRecord record, Instant from) {
        Map<Instant, EvaluationTrendPoint> merged = new java.util.TreeMap<>();
        for (UUID projectId : record.projectIds()) {
            for (EvaluationTrendPoint point : analytics.dailyTrend(actorId, record.organizationId(),
                    projectId, from)) {
                if (point.date() == null) {
                    continue;
                }
                merged.merge(point.date(), point, BrokService::combine);
            }
        }
        return List.copyOf(merged.values());
    }

    private static EvaluationTrendPoint combine(EvaluationTrendPoint a, EvaluationTrendPoint b) {
        long runs = a.runCount() + b.runCount();
        Double latency;
        if (a.avgLatencyMs() == null) {
            latency = b.avgLatencyMs();
        } else if (b.avgLatencyMs() == null) {
            latency = a.avgLatencyMs();
        } else {
            latency = runs == 0 ? null
                    : (a.avgLatencyMs() * a.runCount() + b.avgLatencyMs() * b.runCount()) / runs;
        }
        BigDecimal cost = (a.totalCost() == null ? BigDecimal.ZERO : a.totalCost())
                .add(b.totalCost() == null ? BigDecimal.ZERO : b.totalCost());
        return new EvaluationTrendPoint(a.date(), runs, latency, a.totalTokens() + b.totalTokens(), cost);
    }

    private static double perRunCost(EvaluationTrendPoint point) {
        if (point.totalCost() == null || point.runCount() == 0) {
            return 0d;
        }
        return point.totalCost().doubleValue() / point.runCount();
    }

    /**
     * The artifact's Engineering Memory, read from the same Intelligence projection the artifact's own tab
     * renders. Brok never re-derives the "why" - it carries it, so the two surfaces can never disagree.
     */
    private List<MemoryEntry> safeMemory(BrokRecord record, Subject subject) {
        try {
            return intelligence.intelligenceOf(record.organizationId(), subject.type(), subject.entityId())
                    .memory();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /** Attaches whatever reasoning was recorded about an artifact to the answer. */
    private void rememberWhy(BrokAnswerBuilder b, BrokRecord record, Subject subject) {
        for (MemoryEntry entry : safeMemory(record, subject)) {
            if (entry.answer() != null && !entry.answer().isBlank()) {
                b.remember(new BrokMemory(entry.decisionId(), entry.question(), entry.answer(), entry.at()));
            }
        }
    }

    /** Wraps a title in quotes without fighting Java string escaping in every call site. */
    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private int impactCount(BrokRecord record, Subject subject) {
        ArtifactEvolutionResponse ev = safeEvolution(record, subject.type(), subject.entityId());
        return ev != null ? ev.impactCount() : 0;
    }

    /** Evolution is only defined for graph-resident artifacts; a miss is not an error, it is an absence. */
    private ArtifactEvolutionResponse safeEvolution(BrokRecord record, String type, UUID entityId) {
        try {
            return evolution.evolutionOf(record.organizationId(), type, entityId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<EngineeringRevision> safeRevisions(BrokRecord record, Subject subject) {
        try {
            return intelligence.revisions(record.organizationId(), subject.type(), subject.entityId())
                    .revisions();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static EngineeringRevision namedRevision(List<EngineeringRevision> revisions, BrokQuestion q) {
        return namedRevision(revisions, q, 0);
    }

    /** Resolves "v7" in a question to the real revision whose label says v7. */
    private static EngineeringRevision namedRevision(List<EngineeringRevision> revisions, BrokQuestion q,
                                                     int index) {
        if (q.revisions().size() <= index) {
            return null;
        }
        // Questions read oldest-first ("between v7 and v8"); revisions are newest-first.
        List<Integer> wanted = new ArrayList<>(q.revisions());
        wanted.sort(Comparator.reverseOrder());
        String label = "v" + wanted.get(index);
        return revisions.stream()
                .filter(r -> label.equalsIgnoreCase(r.label()) || wanted.get(index).toString().equals(r.label()))
                .findFirst().orElse(null);
    }

    private static boolean matchesAny(List<String> terms, String... fields) {
        for (String field : fields) {
            if (field == null) {
                continue;
            }
            String lower = field.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (lower.contains(term)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void linkJobArtifacts(BrokAnswerBuilder b, BrokRecord record, EvaluationJob job) {
        record.agent(job.getAgentId()).ifPresent(a -> b.artifact(record.refOf(a)));
        record.prompt(job.getPromptId()).ifPresent(p -> b.artifact(record.refOf(p)));
        record.dataset(job.getDatasetId()).ifPresent(d -> b.artifact(record.refOf(d)));
        record.knowledgeAbout("evaluation", job.getId()).stream().limit(MAX_LIST)
                .forEach(k -> b.reference(record.refOf(k)));
    }

    private void nothingFailed(BrokAnswerBuilder b, BrokRecord record) {
        if (!record.hasEvidence()) {
            b.unknown("Nothing has failed, because nothing has been evaluated.",
                    "An absence of failures is not the same as things working — there is no evidence "
                            + "either way.",
                    "the absence of any completed evaluation");
            b.recommend("Run an evaluation",
                    "Failure is only meaningful once something has been measured.",
                    "Turns an unknown into a result.", NEAR_CERTAIN, SUGGESTED, List.of(),
                    BrokActions.openRegistry("Open the registry"));
        } else {
            b.derived(HEALTHY, "Nothing in this workspace is failing.",
                    plural(record.completed().size(), "evaluation") + " completed and none failed.",
                    "the outcome of every evaluation in scope");
            record.completed().stream().limit(4).forEach(j -> {
                b.evaluation(record.refOf(j));
                b.evidence(record.refOf(j));
            });
        }
    }

    private void defaultFollowUps(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        if (q.hasSubject()) {
            b.followUp("Show every artifact affected by " + q.subject().name() + ".",
                    "Understand the blast radius.", q.subject().nodeId());
        }
        b.followUp("What should my team work on next?", "Turn this into an ordered queue.", null);
        if (!record.failing().isEmpty()) {
            EvaluationJob job = record.failing().get(0);
            b.followUp("Why did " + job.getName() + " fail?", "The most recent failure on record.",
                    "evaluation:" + job.getId());
        }
    }

    // ================================================================================================
    // Small helpers
    // ================================================================================================

    private BrokAnswer finish(BrokAnswerBuilder b, BrokRecord record, BrokQuestion q) {
        return b.build(record.organizationId(), record.projectId(), record.projectName(),
                focusRef(record, q), scopeOf(record));
    }

    private BrokRef focusRef(BrokRecord record, BrokQuestion q) {
        if (q.focusKnowledge() != null) {
            return record.refOf(q.focusKnowledge());
        }
        return q.subject() != null ? subjectRef(record, q.subject()) : null;
    }

    private BrokRef subjectRef(BrokRecord record, Subject subject) {
        return switch (subject.type()) {
            case "agent" -> record.agent(subject.entityId()).map(record::refOf).orElse(fallbackRef(subject));
            case "prompt" -> record.prompt(subject.entityId()).map(record::refOf).orElse(fallbackRef(subject));
            case "dataset" -> record.dataset(subject.entityId()).map(record::refOf).orElse(fallbackRef(subject));
            case "evaluation" -> record.job(subject.entityId()).map(record::refOf).orElse(fallbackRef(subject));
            case "provider" -> record.provider(subject.entityId()).map(record::refOf).orElse(fallbackRef(subject));
            default -> fallbackRef(subject);
        };
    }

    private static BrokRef fallbackRef(Subject subject) {
        return new BrokRef(subject.nodeId(), subject.type(), subject.name(), null, null,
                subject.entityId(), subject.projectId(), null);
    }

    private static BrokRef revisionRef(Subject subject, EngineeringRevision revision) {
        return new BrokRef(revision.id(), "revision",
                subject.name() + " " + revision.label(),
                revision.detail() != null ? revision.detail() : revision.rationale(),
                revision.active() ? "ACTIVE" : null, subject.entityId(), subject.projectId(), revision.at());
    }

    private static String scopeOf(BrokRecord record) {
        return record.projectId() != null && record.projectName() != null
                ? "The project " + record.projectName()
                : "Every project in this organization";
    }

    private static String periodWord(Duration window) {
        long days = window.toDays();
        if (days <= 1) {
            return "in the last 24 hours";
        }
        if (days <= 2) {
            return "since yesterday";
        }
        if (days <= 7) {
            return "this week";
        }
        return "in the last " + days + " days";
    }

    private static String article(String word) {
        return word != null && !word.isEmpty() && "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
    }

    private static String shorten(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 160 ? cleaned : cleaned.substring(0, 157) + "…";
    }

    private static String trimCost(double value) {
        return BigDecimal.valueOf(value).setScale(6, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static BrokFollowUp follow(String question, String rationale, String focus) {
        return new BrokFollowUp(question, rationale, focus);
    }
}
