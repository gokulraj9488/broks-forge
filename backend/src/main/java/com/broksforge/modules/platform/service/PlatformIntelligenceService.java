package com.broksforge.modules.platform.service;

import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentVersion;
import com.broksforge.modules.agent.repository.AgentRepository;
import com.broksforge.modules.agent.repository.AgentVersionRepository;
import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.dataset.domain.DatasetVersion;
import com.broksforge.modules.dataset.repository.DatasetRepository;
import com.broksforge.modules.dataset.repository.DatasetVersionRepository;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.platform.web.dto.ArtifactIntelligenceResponse;
import com.broksforge.modules.platform.web.dto.EngineeringRevision;
import com.broksforge.modules.platform.web.dto.EngineeringRevisionTimeline;
import com.broksforge.modules.platform.web.dto.EvolutionRef;
import com.broksforge.modules.platform.web.dto.GraphEdge;
import com.broksforge.modules.platform.web.dto.GraphNode;
import com.broksforge.modules.platform.web.dto.KnowledgeLink;
import com.broksforge.modules.platform.web.dto.KnowledgeObject;
import com.broksforge.modules.platform.web.dto.MemoryEntry;
import com.broksforge.modules.platform.web.dto.PlatformGraphResponse;
import com.broksforge.modules.platform.web.dto.RevisionComparison;
import com.broksforge.modules.platform.web.dto.RevisionDiff;
import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.prompt.domain.PromptVersion;
import com.broksforge.modules.prompt.repository.PromptRepository;
import com.broksforge.modules.prompt.repository.PromptVersionRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The engineering intelligence layer (P11) — the semantic reading of the platform. It derives first-class
 * engineering-knowledge objects (Observation, Claim, Decision, Evidence, Knowledge), engineering memory (the
 * "why"), and the "AI Git" revision timeline/comparison <b>entirely from the live engineering model</b>:
 * evaluations, version promotions and status changes that already exist in V1.
 *
 * <p>There is no new storage and no parallel data model — this is a deterministic projection over the existing
 * repositories, exactly like the Forge Graph, the Registry and Engineering Evolution before it. Every object is
 * traceable: nothing is fabricated, knowledge emerges only where real decisions and evidence exist, and object
 * ids are stable and composite so a single object can be fetched and referenced across the platform.
 */
@Service
public class PlatformIntelligenceService {

    /** The knowledge-object kinds, in reasoning order. */
    public static final List<String> KNOWLEDGE_TYPES =
            List.of("observation", "claim", "decision", "evidence", "knowledge");

    /** Artifact kinds that can carry engineering intelligence. */
    private static final List<String> SUBJECT_TYPES = List.of("agent", "prompt", "dataset", "evaluation");

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REVISIONS = 200;

    private final PlatformGraphService graph;
    private final AgentRepository agentRepository;
    private final PromptRepository promptRepository;
    private final DatasetRepository datasetRepository;
    private final EvaluationJobRepository evaluationJobRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final DatasetVersionRepository datasetVersionRepository;

    public PlatformIntelligenceService(PlatformGraphService graph,
                                       AgentRepository agentRepository,
                                       PromptRepository promptRepository,
                                       DatasetRepository datasetRepository,
                                       EvaluationJobRepository evaluationJobRepository,
                                       AgentVersionRepository agentVersionRepository,
                                       PromptVersionRepository promptVersionRepository,
                                       DatasetVersionRepository datasetVersionRepository) {
        this.graph = graph;
        this.agentRepository = agentRepository;
        this.promptRepository = promptRepository;
        this.datasetRepository = datasetRepository;
        this.evaluationJobRepository = evaluationJobRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.datasetVersionRepository = datasetVersionRepository;
    }

    // ================================================================================================
    // Knowledge catalog
    // ================================================================================================

    /** Server-side search/filter/sort/pagination over the organization's derived knowledge objects. */
    @Transactional(readOnly = true)
    public PageResponse<KnowledgeObject> list(UUID organizationId, String q, String type, String artifactType,
                                              UUID projectId, String sort, int page, int size) {
        String query = normalize(q);
        List<KnowledgeObject> items = assemble(organizationId).objects().stream()
                .filter(o -> type == null || type.isBlank() || o.type().equals(type))
                .filter(o -> artifactType == null || artifactType.isBlank() || artifactType.equals(o.artifactType()))
                .filter(o -> projectId == null || projectId.equals(o.projectId()))
                .filter(o -> query == null
                        || o.title().toLowerCase(Locale.ROOT).contains(query)
                        || (o.summary() != null && o.summary().toLowerCase(Locale.ROOT).contains(query)))
                .sorted(comparator(sort))
                .toList();

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, items.size());
        int to = Math.min(from + safeSize, items.size());
        return PageResponse.from(
                new PageImpl<>(items.subList(from, to), PageRequest.of(safePage, safeSize), items.size()));
    }

    /**
     * Every derived knowledge object for the organization, unpaged. Brok reasons over exactly this
     * projection — the same objects the catalog, the graph overlay and the artifact views present — so the
     * Brok can never assert knowledge the rest of the platform does not also show.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeObject> catalog(UUID organizationId) {
        return assemble(organizationId).objects();
    }

    /** Fetches one knowledge object by id, requiring it to be of {@code expectedType}. */
    @Transactional(readOnly = true)
    public KnowledgeObject get(UUID organizationId, String expectedType, String id) {
        return assemble(organizationId).objects().stream()
                .filter(o -> o.id().equals(id) && o.type().equals(expectedType))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of(capitalize(expectedType), id));
    }

    // ================================================================================================
    // Per-artifact intelligence (the artifact UX)
    // ================================================================================================

    /** Everything the platform can reason about one artifact, partitioned by knowledge kind, plus memory. */
    @Transactional(readOnly = true)
    public ArtifactIntelligenceResponse intelligenceOf(UUID organizationId, String type, UUID entityId) {
        Subject subject = requireSubject(organizationId, type, entityId);
        OrgIntel intel = assemble(organizationId);
        String nodeId = type + ":" + entityId;

        List<KnowledgeObject> relevant = intel.objects().stream()
                .filter(o -> isAbout(o, type, entityId, nodeId))
                .toList();

        List<KnowledgeObject> observations = ofType(relevant, "observation");
        List<KnowledgeObject> claims = ofType(relevant, "claim");
        List<KnowledgeObject> decisions = ofType(relevant, "decision");
        List<KnowledgeObject> evidence = ofType(relevant, "evidence");
        List<KnowledgeObject> knowledge = ofType(relevant, "knowledge");
        List<MemoryEntry> memory = decisions.stream().map(d -> memoryOf(subject, d)).toList();

        return new ArtifactIntelligenceResponse(refOf(subject), observations, claims, decisions, evidence,
                knowledge, memory);
    }

    // ================================================================================================
    // Engineering Git — revisions + comparison
    // ================================================================================================

    /** The artifact's real revision timeline, newest first (agents, prompts and datasets are versioned). */
    @Transactional(readOnly = true)
    public EngineeringRevisionTimeline revisions(UUID organizationId, String type, UUID entityId) {
        Subject subject = requireSubject(organizationId, type, entityId);
        List<EngineeringRevision> revisions = revisionsFor(type, entityId);
        int promotions = (int) revisions.stream().filter(EngineeringRevision::active).count();
        return new EngineeringRevisionTimeline(refOf(subject), revisions, promotions);
    }

    /** Field-by-field comparison of two revisions of the same artifact. */
    @Transactional(readOnly = true)
    public RevisionComparison compare(UUID organizationId, String type, UUID entityId,
                                      String baseId, String targetId) {
        requireSubject(organizationId, type, entityId);
        List<EngineeringRevision> revisions = revisionsFor(type, entityId);
        EngineeringRevision base = revisions.stream().filter(r -> r.id().equals(baseId)).findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Revision", baseId));
        EngineeringRevision target = revisions.stream().filter(r -> r.id().equals(targetId)).findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Revision", targetId));

        List<RevisionDiff> diffs = new ArrayList<>();
        for (String field : new TreeSet<>(union(base.snapshot().keySet(), target.snapshot().keySet()))) {
            String before = base.snapshot().get(field);
            String after = target.snapshot().get(field);
            String change;
            if (java.util.Objects.equals(before, after)) {
                change = "unchanged";
            } else if (before == null) {
                change = "added";
            } else if (after == null) {
                change = "removed";
            } else {
                change = "changed";
            }
            diffs.add(new RevisionDiff(field, before, after, change));
        }
        return new RevisionComparison(type, entityId, base, target, diffs);
    }

    // ================================================================================================
    // Forge Graph overlay — the base artifact graph enriched with knowledge nodes/edges
    // ================================================================================================

    /**
     * The organization's engineering graph enriched with the knowledge layer: the same real artifacts and
     * relationships as the Forge Graph, plus Observation/Claim/Decision/Evidence/Knowledge nodes wired to the
     * artifacts they were derived from. The base graph is untouched; knowledge is layered on top.
     */
    @Transactional(readOnly = true)
    public PlatformGraphResponse knowledgeGraph(UUID organizationId) {
        PlatformGraphResponse base = graph.buildOrganizationGraph(organizationId);
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        base.nodes().forEach(n -> nodes.put(n.id(), n));
        Map<String, GraphEdge> edges = new LinkedHashMap<>();
        base.edges().forEach(e -> edges.put(e.id(), e));

        for (KnowledgeObject o : assemble(organizationId).objects()) {
            nodes.putIfAbsent(o.id(),
                    new GraphNode(o.id(), o.type(), o.title(), o.artifactType(), o.artifactEntityId(), o.projectId()));
            for (KnowledgeLink link : o.links()) {
                if (nodes.containsKey(link.id()) || isKnowledgeId(link.id())) {
                    String id = o.id() + "->" + link.id() + ":" + link.relation();
                    edges.putIfAbsent(id, new GraphEdge(id, o.id(), link.id(), link.relation()));
                }
            }
        }
        return new PlatformGraphResponse(List.copyOf(nodes.values()), List.copyOf(edges.values()));
    }

    // ================================================================================================
    // Assembly — derive all knowledge objects for an organization from the live model
    // ================================================================================================

    private OrgIntel assemble(UUID organizationId) {
        List<Agent> agents = agentRepository.findByOrganizationIdAndDeletedFalse(organizationId);
        List<Prompt> prompts = promptRepository.findByOrganizationIdAndDeletedFalse(organizationId);
        List<Dataset> datasets = datasetRepository.findByOrganizationIdAndDeletedFalse(organizationId);
        List<EvaluationJob> jobs = evaluationJobRepository.findByOrganizationIdAndDeletedFalse(organizationId);

        Map<String, String> label = new LinkedHashMap<>();
        Map<String, UUID> project = new LinkedHashMap<>();
        agents.forEach(a -> { label.put("agent:" + a.getId(), a.getName()); project.put("agent:" + a.getId(), a.getProjectId()); });
        prompts.forEach(p -> { label.put("prompt:" + p.getId(), p.getName()); project.put("prompt:" + p.getId(), p.getProjectId()); });
        datasets.forEach(d -> { label.put("dataset:" + d.getId(), d.getName()); project.put("dataset:" + d.getId(), d.getProjectId()); });
        jobs.forEach(j -> { label.put("evaluation:" + j.getId(), j.getName()); project.put("evaluation:" + j.getId(), j.getProjectId()); });

        List<KnowledgeObject> objects = new ArrayList<>();

        // Observations + Evidence — measured facts and supporting evidence from evaluations.
        for (EvaluationJob job : jobs) {
            String status = job.getStatus() != null ? job.getStatus().name() : "PENDING";
            Instant at = job.getCompletedAt() != null ? job.getCompletedAt() : job.getCreatedAt();
            List<KnowledgeLink> obsLinks = new ArrayList<>();
            addLink(obsLinks, label, "evaluation:" + job.getId(), "evaluation", "derivedFrom");
            addLink(obsLinks, label, agentNode(job), "agent", "about");
            addLink(obsLinks, label, promptNode(job), "prompt", "about");
            addLink(obsLinks, label, datasetNode(job), "dataset", "measures");
            objects.add(new KnowledgeObject("observation:evaluation:" + job.getId(), "observation",
                    "Observed " + job.getName(), observationSummary(job, status), null,
                    "evaluation", job.getId(), job.getProjectId(), status, at, obsLinks));

            List<KnowledgeLink> evLinks = new ArrayList<>();
            addLink(evLinks, label, agentNode(job), "agent", "supports");
            addLink(evLinks, label, promptNode(job), "prompt", "supports");
            addLink(evLinks, label, datasetNode(job), "dataset", "supports");
            objects.add(new KnowledgeObject("evidence:evaluation:" + job.getId(), "evidence",
                    "Evidence · " + job.getName(),
                    "Evaluation outcome " + humanize(status) + " — evidence for " + evidenceSubjects(job, label) + ".",
                    null, "evaluation", job.getId(), job.getProjectId(), status, at, evLinks));
        }

        // Group evaluations by subject artifact, for decision/claim/knowledge evidence linking.
        Map<UUID, List<EvaluationJob>> byAgent = groupBy(jobs, EvaluationJob::getAgentId);
        Map<UUID, List<EvaluationJob>> byPrompt = groupBy(jobs, EvaluationJob::getPromptId);
        Map<UUID, List<EvaluationJob>> byDataset = groupBy(jobs, EvaluationJob::getDatasetId);

        // Decisions/Claims/Knowledge from prompt version promotions.
        for (Prompt prompt : prompts) {
            List<PromptVersion> versions = promptVersionRepository
                    .findByPromptIdOrderByVersionNumberDesc(prompt.getId(), PageRequest.of(0, MAX_REVISIONS))
                    .getContent();
            Optional<PromptVersion> active = versions.stream().filter(PromptVersion::isActive).findFirst();
            active.ifPresent(av -> {
                PromptVersion prior = versions.stream()
                        .filter(v -> v.getVersionNumber() < av.getVersionNumber())
                        .max(Comparator.comparingInt(PromptVersion::getVersionNumber)).orElse(null);
                addPromotion(objects, "prompt", prompt.getId(), prompt.getName(), prompt.getProjectId(),
                        "decision:prompt-version:" + av.getId(), "claim:prompt:" + prompt.getId(),
                        "v" + av.getVersionNumber(), prior != null ? "v" + prior.getVersionNumber() : null,
                        prior != null ? "prompt-version:" + prior.getId() : null, av.getNotes(),
                        byPrompt.getOrDefault(prompt.getId(), List.of()), av.getCreatedAt(), label);
            });
            maybeArchive(objects, "prompt", prompt.getId(), prompt.getName(), prompt.getProjectId(),
                    statusName(prompt.getStatus()), prompt.getUpdatedAt());
        }

        // Decisions/Claims/Knowledge from agent version promotions.
        for (Agent agent : agents) {
            List<AgentVersion> versions = agentVersionRepository
                    .findByAgentId(agent.getId(), PageRequest.of(0, MAX_REVISIONS, Sort.by(Sort.Direction.DESC, "sequence")))
                    .getContent();
            Optional<AgentVersion> active = versions.stream().filter(AgentVersion::isActive).findFirst();
            active.ifPresent(av -> {
                AgentVersion prior = versions.stream()
                        .filter(v -> v.getSequence() < av.getSequence())
                        .max(Comparator.comparingLong(AgentVersion::getSequence)).orElse(null);
                addPromotion(objects, "agent", agent.getId(), agent.getName(), agent.getProjectId(),
                        "decision:agent-version:" + av.getId(), "claim:agent:" + agent.getId(),
                        av.getVersionNumber(), prior != null ? prior.getVersionNumber() : null,
                        prior != null ? "agent-version:" + prior.getId() : null, av.getReleaseNotes(),
                        byAgent.getOrDefault(agent.getId(), List.of()), av.getCreatedAt(), label);
            });
            maybeArchive(objects, "agent", agent.getId(), agent.getName(), agent.getProjectId(),
                    statusName(agent.getStatus()), agent.getUpdatedAt());
        }

        // Datasets: no active flag, but deprecation is a real decision; evidence still links to them.
        for (Dataset dataset : datasets) {
            maybeArchive(objects, "dataset", dataset.getId(), dataset.getName(), dataset.getProjectId(),
                    statusName(dataset.getStatus()), dataset.getUpdatedAt());
            List<EvaluationJob> evals = byDataset.getOrDefault(dataset.getId(), List.of());
            if (!evals.isEmpty()) {
                addDatasetKnowledge(objects, dataset, evals, label);
            }
        }

        return new OrgIntel(objects, label, project);
    }

    /** Adds the Decision + Claim + Knowledge triple for a version promotion. */
    private void addPromotion(List<KnowledgeObject> out, String artifactType, UUID entityId, String name,
                              UUID projectId, String decisionId, String claimId, String revLabel,
                              String priorLabel, String priorRevisionNode, String rationale,
                              List<EvaluationJob> evals, Instant at, Map<String, String> label) {
        String artifactNode = artifactType + ":" + entityId;

        List<KnowledgeLink> dLinks = new ArrayList<>();
        addLink(dLinks, label, artifactNode, artifactType, "concerns");
        if (priorRevisionNode != null) {
            dLinks.add(new KnowledgeLink(priorRevisionNode, "revision", "supersedes", priorLabel));
        }
        for (EvaluationJob e : evals) {
            dLinks.add(new KnowledgeLink("evidence:evaluation:" + e.getId(), "evidence", "informedBy", e.getName()));
        }
        String decisionSummary = revLabel + " is the active " + artifactType + " revision"
                + (priorLabel != null ? ", superseding " + priorLabel : "") + ".";
        out.add(new KnowledgeObject(decisionId, "decision", "Promoted " + name + " to " + revLabel,
                decisionSummary, rationale, artifactType, entityId, projectId, null, at, dLinks));

        List<KnowledgeLink> cLinks = new ArrayList<>();
        cLinks.add(new KnowledgeLink(decisionId, "decision", "basedOn", "Promotion to " + revLabel));
        addLink(cLinks, label, artifactNode, artifactType, "about");
        for (EvaluationJob e : evals) {
            cLinks.add(new KnowledgeLink("evidence:evaluation:" + e.getId(), "evidence", "supportedBy", e.getName()));
        }
        String claimSummary = "The canonical revision is " + revLabel + ", "
                + (evals.isEmpty() ? "with no evaluation evidence yet." : supportedBy(evals.size()) + ".");
        out.add(new KnowledgeObject(claimId, "claim", name + "'s canonical revision is " + revLabel,
                claimSummary, null, artifactType, entityId, projectId, null, at, cLinks));

        List<KnowledgeLink> kLinks = new ArrayList<>();
        kLinks.add(new KnowledgeLink(decisionId, "decision", "summarizes", "Promotion to " + revLabel));
        kLinks.add(new KnowledgeLink(claimId, "claim", "asserts", name + " → " + revLabel));
        addLink(kLinks, label, artifactNode, artifactType, "about");
        for (EvaluationJob e : evals) {
            kLinks.add(new KnowledgeLink("evidence:evaluation:" + e.getId(), "evidence", "supportedBy", e.getName()));
        }
        String knowledgeSummary = "Canonical revision is " + revLabel + ", "
                + (evals.isEmpty() ? "with no evaluation evidence yet." : "backed by " + supportedBy(evals.size()) + ".");
        out.add(new KnowledgeObject("knowledge:" + artifactType + ":" + entityId, "knowledge",
                "Knowledge · " + name, knowledgeSummary, null, artifactType, entityId, projectId, null, at, kLinks));
    }

    /** A dataset that has been evaluated carries durable knowledge even without a promotion flag. */
    private void addDatasetKnowledge(List<KnowledgeObject> out, Dataset dataset, List<EvaluationJob> evals,
                                     Map<String, String> label) {
        List<KnowledgeLink> kLinks = new ArrayList<>();
        addLink(kLinks, label, "dataset:" + dataset.getId(), "dataset", "about");
        for (EvaluationJob e : evals) {
            kLinks.add(new KnowledgeLink("evidence:evaluation:" + e.getId(), "evidence", "supportedBy", e.getName()));
        }
        Instant at = evals.stream().map(EvaluationJob::getCreatedAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(dataset.getCreatedAt());
        out.add(new KnowledgeObject("knowledge:dataset:" + dataset.getId(), "knowledge",
                "Knowledge · " + dataset.getName(),
                "Used as evaluation ground truth, backed by " + supportedBy(evals.size()) + ".",
                null, "dataset", dataset.getId(), dataset.getProjectId(), null, at, kLinks));
    }

    /** Emits a deprecation Decision when an artifact has been archived. */
    private void maybeArchive(List<KnowledgeObject> out, String artifactType, UUID entityId, String name,
                              UUID projectId, String status, Instant at) {
        if (!"ARCHIVED".equals(status)) {
            return;
        }
        List<KnowledgeLink> links = new ArrayList<>();
        links.add(new KnowledgeLink(artifactType + ":" + entityId, artifactType, "concerns", name));
        out.add(new KnowledgeObject("decision:archive:" + artifactType + ":" + entityId, "decision",
                "Deprecated " + name, name + " was archived and is no longer active.", null,
                artifactType, entityId, projectId, null, at, links));
    }

    // ================================================================================================
    // Revisions
    // ================================================================================================

    private List<EngineeringRevision> revisionsFor(String type, UUID entityId) {
        PageRequest first = PageRequest.of(0, MAX_REVISIONS);
        return switch (type) {
            case "prompt" -> promptVersionRepository.findByPromptIdOrderByVersionNumberDesc(entityId, first)
                    .stream().map(this::promptRevision).toList();
            case "agent" -> agentVersionRepository
                    .findByAgentId(entityId, PageRequest.of(0, MAX_REVISIONS, Sort.by(Sort.Direction.DESC, "sequence")))
                    .stream().map(this::agentRevision).toList();
            case "dataset" -> datasetVersionRepository.findByDatasetIdOrderByVersionNumberDesc(entityId, first)
                    .stream().map(this::datasetRevision).toList();
            default -> List.of();
        };
    }

    private EngineeringRevision promptRevision(PromptVersion v) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("version", "v" + v.getVersionNumber());
        snapshot.put("template", v.getTemplate());
        snapshot.put("variables", joinList(v.getVariables()));
        snapshot.put("provider", v.getProvider() != null ? v.getProvider().name() : null);
        snapshot.put("model", v.getModel());
        String detail = (v.getVariables() != null ? v.getVariables().size() : 0) + " variable(s)"
                + (StringUtils.hasText(v.getModel()) ? " · " + v.getModel() : "");
        return new EngineeringRevision("prompt-version:" + v.getId(), "prompt", v.getPromptId(),
                "v" + v.getVersionNumber(), detail, v.getNotes(), v.isActive(), true, v.getCreatedAt(), snapshot);
    }

    private EngineeringRevision agentRevision(AgentVersion v) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("version", v.getVersionNumber());
        snapshot.put("model", v.getModel());
        snapshot.put("provider", v.getProvider() != null ? v.getProvider().name() : null);
        snapshot.put("environment", v.getEnvironment() != null ? v.getEnvironment().name() : null);
        snapshot.put("frameworkVersion", v.getFrameworkVersion());
        snapshot.put("promptVersion", v.getPromptVersion());
        snapshot.put("gitCommitSha", v.getGitCommitSha());
        String detail = orDash(v.getModel())
                + (v.getEnvironment() != null ? " · " + v.getEnvironment().name() : "");
        return new EngineeringRevision("agent-version:" + v.getId(), "agent", v.getAgentId(),
                v.getVersionNumber(), detail, v.getReleaseNotes(), v.isActive(), v.isRollbackReady(),
                v.getCreatedAt(), snapshot);
    }

    private EngineeringRevision datasetRevision(DatasetVersion v) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("version", "v" + v.getVersionNumber());
        snapshot.put("items", String.valueOf(v.getItemCount()));
        snapshot.put("format", v.getSourceFormat() != null ? v.getSourceFormat().name() : null);
        snapshot.put("columns", joinList(v.getColumns()));
        snapshot.put("checksum", v.getChecksum());
        String detail = v.getItemCount() + " item(s)"
                + (v.getSourceFormat() != null ? " · " + v.getSourceFormat().name() : "");
        return new EngineeringRevision("dataset-version:" + v.getId(), "dataset", v.getDatasetId(),
                "v" + v.getVersionNumber(), detail, v.getDescription(), false, true, v.getCreatedAt(), snapshot);
    }

    // ================================================================================================
    // Helpers
    // ================================================================================================

    private Subject requireSubject(UUID organizationId, String type, UUID entityId) {
        Optional<Subject> subject = switch (type == null ? "" : type) {
            case "agent" -> agentRepository.findByOrganizationIdAndDeletedFalse(organizationId).stream()
                    .filter(a -> a.getId().equals(entityId))
                    .map(a -> new Subject("agent", a.getId(), a.getName(), a.getProjectId())).findFirst();
            case "prompt" -> promptRepository.findByOrganizationIdAndDeletedFalse(organizationId).stream()
                    .filter(p -> p.getId().equals(entityId))
                    .map(p -> new Subject("prompt", p.getId(), p.getName(), p.getProjectId())).findFirst();
            case "dataset" -> datasetRepository.findByOrganizationIdAndDeletedFalse(organizationId).stream()
                    .filter(d -> d.getId().equals(entityId))
                    .map(d -> new Subject("dataset", d.getId(), d.getName(), d.getProjectId())).findFirst();
            case "evaluation" -> evaluationJobRepository.findByOrganizationIdAndDeletedFalse(organizationId).stream()
                    .filter(j -> j.getId().equals(entityId))
                    .map(j -> new Subject("evaluation", j.getId(), j.getName(), j.getProjectId())).findFirst();
            default -> Optional.empty();
        };
        if (!SUBJECT_TYPES.contains(type)) {
            throw ResourceNotFoundException.of("Artifact", entityId);
        }
        return subject.orElseThrow(() -> ResourceNotFoundException.of("Artifact", entityId));
    }

    /** An object is relevant to an artifact when it is about it directly, or any link targets its node. */
    private static boolean isAbout(KnowledgeObject o, String type, UUID entityId, String nodeId) {
        if (type.equals(o.artifactType()) && entityId.equals(o.artifactEntityId())) {
            return true;
        }
        return o.links().stream().anyMatch(l -> nodeId.equals(l.id()));
    }

    private MemoryEntry memoryOf(Subject subject, KnowledgeObject decision) {
        String question = decision.id().startsWith("decision:archive:")
                ? "Why was " + subject.name() + " deprecated?"
                : "Why was " + subject.name() + " changed?";
        String answer = StringUtils.hasText(decision.rationale())
                ? decision.rationale()
                : decision.summary();
        return new MemoryEntry(decision.id(), question, answer, decision.at());
    }

    private static List<KnowledgeObject> ofType(List<KnowledgeObject> objects, String type) {
        return objects.stream().filter(o -> o.type().equals(type)).toList();
    }

    private static void addLink(List<KnowledgeLink> links, Map<String, String> label, String nodeId,
                                String type, String relation) {
        if (nodeId != null && label.containsKey(nodeId)) {
            links.add(new KnowledgeLink(nodeId, type, relation, label.get(nodeId)));
        }
    }

    private static String agentNode(EvaluationJob j) {
        return j.getAgentId() != null ? "agent:" + j.getAgentId() : null;
    }

    private static String promptNode(EvaluationJob j) {
        return j.getPromptId() != null ? "prompt:" + j.getPromptId() : null;
    }

    private static String datasetNode(EvaluationJob j) {
        return j.getDatasetId() != null ? "dataset:" + j.getDatasetId() : null;
    }

    private static String evidenceSubjects(EvaluationJob j, Map<String, String> label) {
        List<String> names = new ArrayList<>();
        if (agentNode(j) != null && label.containsKey(agentNode(j))) names.add(label.get(agentNode(j)));
        if (datasetNode(j) != null && label.containsKey(datasetNode(j))) names.add(label.get(datasetNode(j)));
        return names.isEmpty() ? "its inputs" : String.join(" + ", names);
    }

    private static String observationSummary(EvaluationJob job, String status) {
        return switch (status) {
            case "COMPLETED" -> "Completed — " + job.getCompletedItems() + "/" + job.getTotalItems()
                    + " items measured" + (job.getFailedItems() > 0 ? ", " + job.getFailedItems() + " failed." : ".");
            case "FAILED" -> StringUtils.hasText(job.getErrorMessage())
                    ? "Failed: " + job.getErrorMessage() : "Failed before completion.";
            case "RUNNING" -> "Running — " + job.getCompletedItems() + "/" + job.getTotalItems() + " items so far.";
            case "CANCELLED" -> "Cancelled before completion.";
            default -> "Queued — not yet measured.";
        };
    }

    private static <K> Map<K, List<EvaluationJob>> groupBy(List<EvaluationJob> jobs,
                                                           java.util.function.Function<EvaluationJob, K> key) {
        Map<K, List<EvaluationJob>> map = new LinkedHashMap<>();
        for (EvaluationJob j : jobs) {
            K k = key.apply(j);
            if (k != null) {
                map.computeIfAbsent(k, x -> new ArrayList<>()).add(j);
            }
        }
        return map;
    }

    private static Comparator<KnowledgeObject> comparator(String sort) {
        Comparator<KnowledgeObject> byAt =
                Comparator.comparing(KnowledgeObject::at, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<KnowledgeObject> byTitle =
                Comparator.comparing(o -> o.title() == null ? "" : o.title().toLowerCase(Locale.ROOT));
        return switch (sort == null ? "recent" : sort) {
            case "name" -> byTitle;
            case "name_desc" -> byTitle.reversed();
            case "oldest" -> byAt;
            default -> byAt.reversed();
        };
    }

    private static EvolutionRef refOf(Subject s) {
        return new EvolutionRef(s.type() + ":" + s.id(), s.type(), s.name(), s.id(), s.projectId(), null);
    }

    private static boolean isKnowledgeId(String id) {
        return KNOWLEDGE_TYPES.stream().anyMatch(t -> id.startsWith(t + ":")) || id.startsWith("revision:");
    }

    private static String statusName(Object status) {
        return status == null ? null : ((Enum<?>) status).name();
    }

    private static String supportedBy(int count) {
        return count + " evaluation" + (count == 1 ? "" : "s");
    }

    private static String humanize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String joinList(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(", ", values);
    }

    private static String orDash(String value) {
        return StringUtils.hasText(value) ? value : "—";
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static String capitalize(String value) {
        return value == null || value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static <T> java.util.Set<T> union(java.util.Set<T> a, java.util.Set<T> b) {
        java.util.Set<T> set = new java.util.LinkedHashSet<>(a);
        set.addAll(b);
        return set;
    }

    private record Subject(String type, UUID id, String name, UUID projectId) {
    }

    private record OrgIntel(List<KnowledgeObject> objects, Map<String, String> label, Map<String, UUID> project) {
    }
}
