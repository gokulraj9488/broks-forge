package com.broksforge.modules.brok.service;

import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.brok.web.dto.BrokDtos.BrokRef;
import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.platform.web.dto.KnowledgeLink;
import com.broksforge.modules.platform.web.dto.KnowledgeObject;
import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.provider.domain.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The engineering record Brok reasons over — one immutable snapshot of the live model, read once per
 * question.
 *
 * <p>This class is the reason Brok cannot hallucinate. It holds only real rows (agents, prompts,
 * datasets, providers, evaluations) and the derived knowledge objects the rest of the platform already
 * publishes; every sentence Brok produces is composed from these fields. If something is not in this
 * snapshot, Brok has no way to say it — the honest "I cannot answer that from the engineering record"
 * is a structural property, not a prompt instruction.
 *
 * <p>Derivations live here rather than in the answer composer so that the same reading of "what is failing",
 * "what has no evidence" or "which provider is behind the failures" is shared by every question and every
 * brief. One record, one reading.
 */
public final class BrokRecord {

    private final UUID organizationId;
    private final UUID projectId;
    private final String projectName;
    private final Map<UUID, String> projectNames;
    private final List<Agent> agents;
    private final List<Prompt> prompts;
    private final List<Dataset> datasets;
    private final List<Provider> providers;
    private final List<EvaluationJob> jobs;
    private final List<KnowledgeObject> knowledge;
    private final Instant now;

    BrokRecord(UUID organizationId, UUID projectId, Map<UUID, String> projectNames, List<Agent> agents,
                  List<Prompt> prompts, List<Dataset> datasets, List<Provider> providers,
                  List<EvaluationJob> jobs, List<KnowledgeObject> knowledge, Instant now) {
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.projectName = projectId != null ? projectNames.get(projectId) : null;
        this.projectNames = projectNames;
        this.agents = agents;
        this.prompts = prompts;
        this.datasets = datasets;
        this.providers = providers;
        this.jobs = jobs;
        this.knowledge = knowledge;
        this.now = now;
    }

    // ------------------------------------------------------------------------------------------
    // The record itself
    // ------------------------------------------------------------------------------------------

    public UUID organizationId() {
        return organizationId;
    }

    public UUID projectId() {
        return projectId;
    }

    public String projectName() {
        return projectName;
    }

    public List<Agent> agents() {
        return agents;
    }

    public List<Prompt> prompts() {
        return prompts;
    }

    public List<Dataset> datasets() {
        return datasets;
    }

    public List<Provider> providers() {
        return providers;
    }

    public List<EvaluationJob> jobs() {
        return jobs;
    }

    public List<KnowledgeObject> knowledge() {
        return knowledge;
    }

    /** Every project in scope — one when the workspace is project-scoped, all of them otherwise. */
    public List<UUID> projectIds() {
        return projectId != null ? List.of(projectId) : List.copyOf(projectNames.keySet());
    }

    public Instant now() {
        return now;
    }

    /** True when there is nothing at all to reason about — the honest empty state (L-34). */
    public boolean isEmpty() {
        return agents.isEmpty() && prompts.isEmpty() && datasets.isEmpty() && jobs.isEmpty();
    }

    /** True when at least one evaluation has actually produced a result. Absence of this is not health. */
    public boolean hasEvidence() {
        return jobs.stream().anyMatch(j -> j.getStatus() == EvaluationStatus.COMPLETED
                || j.getStatus() == EvaluationStatus.FAILED);
    }

    // ------------------------------------------------------------------------------------------
    // Evaluations
    // ------------------------------------------------------------------------------------------

    public List<EvaluationJob> jobsWithStatus(EvaluationStatus status) {
        return jobs.stream().filter(j -> j.getStatus() == status).toList();
    }

    /** Failing evaluations, newest first — the single most consequential thing Brok reports. */
    public List<EvaluationJob> failing() {
        return sortedByRecency(jobsWithStatus(EvaluationStatus.FAILED));
    }

    public List<EvaluationJob> completed() {
        return sortedByRecency(jobsWithStatus(EvaluationStatus.COMPLETED));
    }

    /** Evaluations that have not finished — an investigation nobody has closed yet. */
    public List<EvaluationJob> inFlight() {
        return sortedByRecency(jobs.stream()
                .filter(j -> j.getStatus() == EvaluationStatus.RUNNING || j.getStatus() == EvaluationStatus.PENDING)
                .toList());
    }

    /** Completed evaluations that still recorded failed items — passing overall, broken in part. */
    public List<EvaluationJob> completedWithFailures() {
        return sortedByRecency(completed().stream().filter(j -> j.getFailedItems() > 0).toList());
    }

    public List<EvaluationJob> since(Instant from) {
        return sortedByRecency(jobs.stream().filter(j -> atOf(j) != null && atOf(j).isAfter(from)).toList());
    }

    /** Every evaluation that referenced a given artifact — its evidence, newest first. */
    public List<EvaluationJob> evaluationsFor(String artifactType, UUID entityId) {
        return sortedByRecency(jobs.stream().filter(j -> switch (artifactType) {
            case "agent" -> entityId.equals(j.getAgentId());
            case "prompt" -> entityId.equals(j.getPromptId());
            case "dataset" -> entityId.equals(j.getDatasetId());
            case "evaluation" -> entityId.equals(j.getId());
            default -> false;
        }).toList());
    }

    public Optional<EvaluationJob> job(UUID id) {
        return jobs.stream().filter(j -> j.getId().equals(id)).findFirst();
    }

    /** When an evaluation actually happened: completion where recorded, otherwise creation. */
    public static Instant atOf(EvaluationJob job) {
        return job.getCompletedAt() != null ? job.getCompletedAt() : job.getCreatedAt();
    }

    /** True when an evaluation recorded any failure — a hard failure, or failed items inside a completed run. */
    public static boolean troubled(EvaluationJob job) {
        return job.getStatus() == EvaluationStatus.FAILED || job.getFailedItems() > 0;
    }

    /**
     * The precedents of an evaluation: earlier troubled evaluations that share an agent, prompt or dataset
     * with it, newest first. This is one reading shared by "Has this happened before?" and the Incident
     * Brief, so a precedent can never exist for one and not the other.
     */
    public List<EvaluationJob> precedentsOf(EvaluationJob current) {
        return sortedByRecency(jobs.stream()
                .filter(j -> !j.getId().equals(current.getId()))
                .filter(BrokRecord::troubled)
                .filter(j -> {
                    Instant a = atOf(j);
                    Instant b = atOf(current);
                    return a != null && b != null && a.isBefore(b);
                })
                .filter(j -> sharesGround(j, current))
                .toList());
    }

    private static boolean sharesGround(EvaluationJob a, EvaluationJob b) {
        return (a.getAgentId() != null && a.getAgentId().equals(b.getAgentId()))
                || (a.getPromptId() != null && a.getPromptId().equals(b.getPromptId()))
                || (a.getDatasetId() != null && a.getDatasetId().equals(b.getDatasetId()));
    }

    private static List<EvaluationJob> sortedByRecency(List<EvaluationJob> input) {
        return input.stream()
                .sorted(Comparator.comparing(BrokRecord::atOf,
                        Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed())
                .toList();
    }

    // ------------------------------------------------------------------------------------------
    // Providers — resolved through the agent an evaluation actually ran against
    // ------------------------------------------------------------------------------------------

    public Optional<Provider> provider(UUID id) {
        return providers.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public Optional<Agent> agent(UUID id) {
        return agents.stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    public Optional<Prompt> prompt(UUID id) {
        return prompts.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public Optional<Dataset> dataset(UUID id) {
        return datasets.stream().filter(d -> d.getId().equals(id)).findFirst();
    }

    /**
     * The provider an evaluation reached, resolved the way the platform models it: through the agent's
     * configured provider first, falling back to the provider recorded on the job itself. Returns null when
     * neither is known — in which case Brok says the attribution is unknown rather than guessing.
     */
    public String providerNameOf(EvaluationJob job) {
        Agent agent = job.getAgentId() != null ? agent(job.getAgentId()).orElse(null) : null;
        if (agent != null && agent.getProviderId() != null) {
            String name = provider(agent.getProviderId()).map(Provider::getName).orElse(null);
            if (name != null) {
                return name;
            }
        }
        return job.getProvider() != null ? job.getProvider().name() : null;
    }

    /** Failure counts by provider name, most failures first. Only real failures are counted. */
    public List<Map.Entry<String, Long>> failuresByProvider() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EvaluationJob job : failing()) {
            String name = providerNameOf(job);
            counts.merge(name != null ? name : "Unattributed", 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();
    }

    // ------------------------------------------------------------------------------------------
    // Knowledge — the same derived objects the rest of the platform publishes
    // ------------------------------------------------------------------------------------------

    public List<KnowledgeObject> knowledgeOfType(String type) {
        return knowledge.stream().filter(k -> type.equals(k.type())).toList();
    }

    public Optional<KnowledgeObject> knowledgeById(String id) {
        return knowledge.stream().filter(k -> k.id().equals(id)).findFirst();
    }

    public List<KnowledgeObject> knowledgeAbout(String artifactType, UUID entityId) {
        String nodeId = artifactType + ":" + entityId;
        return knowledge.stream()
                .filter(k -> (artifactType.equals(k.artifactType()) && entityId.equals(k.artifactEntityId()))
                        || k.links().stream().anyMatch(l -> nodeId.equals(l.id())))
                .toList();
    }

    /**
     * Decisions that no evidence stands behind. This is one of the most valuable questions the record can
     * answer: a promotion nobody measured is a decision the organization is carrying on faith.
     */
    public List<KnowledgeObject> unsupportedDecisions() {
        return knowledgeOfType("decision").stream()
                .filter(d -> d.links().stream().noneMatch(l -> "evidence".equals(l.type())))
                .toList();
    }

    /** How many evidence links a knowledge object carries — the input to the verbal confidence ladder. */
    public static int evidenceCount(KnowledgeObject object) {
        return (int) object.links().stream().filter(l -> "evidence".equals(l.type())).count();
    }

    /**
     * Tensions in the engineering record: a claim that an artifact's canonical revision is settled while the
     * evidence about that same artifact contains failures. This is reported as an inference, never as a fact —
     * a failing evaluation does not automatically invalidate a promotion, it only puts it in question.
     */
    public List<Tension> tensions() {
        List<Tension> out = new ArrayList<>();
        for (KnowledgeObject claim : knowledgeOfType("claim")) {
            if (claim.artifactType() == null || claim.artifactEntityId() == null) {
                continue;
            }
            List<EvaluationJob> failures = evaluationsFor(claim.artifactType(), claim.artifactEntityId()).stream()
                    .filter(j -> j.getStatus() == EvaluationStatus.FAILED)
                    .toList();
            if (!failures.isEmpty()) {
                out.add(new Tension(claim, failures));
            }
        }
        return out;
    }

    /** A claim and the failing evaluations that sit uneasily beside it. */
    public record Tension(KnowledgeObject claim, List<EvaluationJob> failures) {
    }

    // ------------------------------------------------------------------------------------------
    // Reference building — every ref points at a real record
    // ------------------------------------------------------------------------------------------

    public BrokRef refOf(EvaluationJob job) {
        String status = job.getStatus() != null ? job.getStatus().name() : "PENDING";
        String detail = switch (status) {
            case "COMPLETED" -> job.getCompletedItems() + "/" + job.getTotalItems() + " items measured"
                    + (job.getFailedItems() > 0 ? ", " + job.getFailedItems() + " failed" : "");
            case "FAILED" -> job.getErrorMessage() != null && !job.getErrorMessage().isBlank()
                    ? job.getErrorMessage() : "Failed before completion";
            case "RUNNING" -> job.getCompletedItems() + "/" + job.getTotalItems() + " items so far";
            case "CANCELLED" -> "Cancelled before completion";
            default -> "Queued — not yet measured";
        };
        return new BrokRef("evaluation:" + job.getId(), "evaluation", job.getName(), detail, status,
                job.getId(), job.getProjectId(), atOf(job));
    }

    public BrokRef refOf(Agent agent) {
        return new BrokRef("agent:" + agent.getId(), "agent", agent.getName(),
                agent.getHealthStatus() != null ? "Health " + lower(agent.getHealthStatus().name()) : null,
                agent.getStatus() != null ? agent.getStatus().name() : null,
                agent.getId(), agent.getProjectId(), agent.getCreatedAt());
    }

    public BrokRef refOf(Prompt prompt) {
        String detail = prompt.getLatestVersionNumber() > 0
                ? "v" + prompt.getLatestVersionNumber() + " is the latest revision"
                : "No revisions yet";
        return new BrokRef("prompt:" + prompt.getId(), "prompt", prompt.getName(), detail,
                prompt.getStatus() != null ? prompt.getStatus().name() : null,
                prompt.getId(), prompt.getProjectId(), prompt.getCreatedAt());
    }

    public BrokRef refOf(Dataset dataset) {
        return new BrokRef("dataset:" + dataset.getId(), "dataset", dataset.getName(),
                dataset.getCurrentItemCount() + " item(s) of ground truth",
                dataset.getStatus() != null ? dataset.getStatus().name() : null,
                dataset.getId(), dataset.getProjectId(), dataset.getCreatedAt());
    }

    public BrokRef refOf(Provider provider) {
        return new BrokRef("provider:" + provider.getId(), "provider", provider.getName(),
                provider.getType() != null ? provider.getType().name() : null,
                provider.getHealthStatus() != null ? provider.getHealthStatus().name() : null,
                provider.getId(), provider.getProjectId(), provider.getCreatedAt());
    }

    public BrokRef refOf(KnowledgeObject object) {
        return new BrokRef(object.id(), object.type(), object.title(), object.summary(), object.outcome(),
                object.artifactEntityId(), object.projectId(), object.at());
    }

    /** The label the graph and registry use for a node id, when the record knows it. */
    public String labelOf(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        int colon = nodeId.indexOf(':');
        if (colon < 0) {
            return nodeId;
        }
        String type = nodeId.substring(0, colon);
        String rest = nodeId.substring(colon + 1);
        UUID id;
        try {
            id = UUID.fromString(rest);
        } catch (IllegalArgumentException ex) {
            return knowledgeById(nodeId).map(KnowledgeObject::title).orElse(nodeId);
        }
        return switch (type) {
            case "agent" -> agent(id).map(Agent::getName).orElse(nodeId);
            case "prompt" -> prompt(id).map(Prompt::getName).orElse(nodeId);
            case "dataset" -> dataset(id).map(Dataset::getName).orElse(nodeId);
            case "provider" -> provider(id).map(Provider::getName).orElse(nodeId);
            case "evaluation" -> job(id).map(EvaluationJob::getName).orElse(nodeId);
            case "project" -> projectNames.getOrDefault(id, nodeId);
            default -> nodeId;
        };
    }

    /** The evidence links of a knowledge object, resolved into references to the real evaluations. */
    public List<BrokRef> evidenceRefs(KnowledgeObject object) {
        List<BrokRef> refs = new ArrayList<>();
        for (KnowledgeLink link : object.links()) {
            if (!"evidence".equals(link.type())) {
                continue;
            }
            knowledgeById(link.id()).ifPresent(e -> refs.add(refOf(e)));
        }
        return refs;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
