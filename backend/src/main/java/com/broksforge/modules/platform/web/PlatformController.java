package com.broksforge.modules.platform.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.platform.service.PlatformEvolutionService;
import com.broksforge.modules.platform.service.PlatformGraphService;
import com.broksforge.modules.platform.service.PlatformIntelligenceService;
import com.broksforge.modules.platform.service.PlatformObservabilityService;
import com.broksforge.modules.platform.service.PlatformRegistryService;
import com.broksforge.modules.platform.web.dto.ArtifactEvolutionResponse;
import com.broksforge.modules.platform.web.dto.ArtifactIntelligenceResponse;
import com.broksforge.modules.platform.web.dto.EngineeringRevisionTimeline;
import com.broksforge.modules.platform.web.dto.KnowledgeObject;
import com.broksforge.modules.platform.web.dto.PlatformGraphResponse;
import com.broksforge.modules.platform.web.dto.PlatformHealthResponse;
import com.broksforge.modules.platform.web.dto.RegistryItemResponse;
import com.broksforge.modules.platform.web.dto.RegistryTypeCount;
import com.broksforge.modules.platform.web.dto.RevisionComparison;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The engineering platform's read-only observability API, namespaced under {@code /platform} so later phases
 * (graph, registry, provenance, lineage, history) grow here naturally. This phase exposes only integrity.
 *
 * <p>Org-scoped because the append-only ledger is per organization. The controller exists only when the
 * platform is enabled; when disabled the endpoint is simply absent (reversible by flag), and callers treat
 * its absence as "platform not available". Read-only: it never mutates V1 or the kernel.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/platform")
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Platform", description = "Read-only engineering platform observability (integrity, ledger)")
public class PlatformController {

    private final PlatformObservabilityService observability;
    private final PlatformGraphService graph;
    private final PlatformRegistryService registry;
    private final PlatformEvolutionService evolution;
    private final PlatformIntelligenceService intelligence;
    private final OrganizationAccessService accessService;

    public PlatformController(PlatformObservabilityService observability,
                             PlatformGraphService graph,
                             PlatformRegistryService registry,
                             PlatformEvolutionService evolution,
                             PlatformIntelligenceService intelligence,
                             OrganizationAccessService accessService) {
        this.observability = observability;
        this.graph = graph;
        this.registry = registry;
        this.evolution = evolution;
        this.intelligence = intelligence;
        this.accessService = accessService;
    }

    @GetMapping("/health")
    @Operation(summary = "Get platform integrity for this organization",
            description = "Read-only integrity snapshot: whether the engineering ledger verifies, whether the "
                    + "knowledge integrity scan is clean, and how many entries the ledger holds.")
    public ResponseEntity<PlatformHealthResponse> health(@PathVariable UUID organizationId) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return ResponseEntity.ok(observability.health(organizationId));
    }

    @GetMapping("/graph")
    @Operation(summary = "Get the organization's engineering graph",
            description = "Read-only graph of real engineering artifacts (providers, models, agents, prompts, "
                    + "datasets, evaluations) and the relationships between them, for exploration. Pass "
                    + "include=knowledge to overlay the derived engineering-knowledge nodes (observations, "
                    + "claims, decisions, evidence, knowledge) on the same graph.")
    public ResponseEntity<PlatformGraphResponse> graph(@PathVariable UUID organizationId,
                                                       @RequestParam(required = false) String include) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        boolean withKnowledge = "knowledge".equalsIgnoreCase(include);
        return ResponseEntity.ok(withKnowledge
                ? intelligence.knowledgeGraph(organizationId)
                : graph.buildOrganizationGraph(organizationId));
    }

    @GetMapping("/registry")
    @Operation(summary = "Browse/search the unified engineering registry",
            description = "Read-only, paginated catalog of every engineering artifact in the organization. "
                    + "Supports partial-match search (q), and filters by type, project, provider and tag, plus "
                    + "sorting — all server-side. Discovery only; items link to their existing pages.")
    public ResponseEntity<PageResponse<RegistryItemResponse>> registry(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return ResponseEntity.ok(
                registry.list(organizationId, q, type, projectId, providerId, tag, sort, page, size));
    }

    @GetMapping("/registry/types")
    @Operation(summary = "Registry artifact-type counts",
            description = "Read-only per-type counts for the organization, powering the registry's type filters.")
    public ResponseEntity<List<RegistryTypeCount>> registryTypes(@PathVariable UUID organizationId) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return ResponseEntity.ok(registry.types(organizationId));
    }

    @GetMapping("/evolution/{type}/{entityId}")
    @Operation(summary = "Get the engineering evolution of an artifact",
            description = "Read-only: where an artifact came from (dependencies), what it influences (dependents "
                    + "and transitive impact), how it changed (historical revisions) and the evidence that bears "
                    + "on it (evaluations). Derived entirely from the live engineering model.")
    public ResponseEntity<ArtifactEvolutionResponse> evolution(@PathVariable UUID organizationId,
                                                               @PathVariable String type,
                                                               @PathVariable UUID entityId) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return ResponseEntity.ok(evolution.evolutionOf(organizationId, type, entityId));
    }

    // ============================================================================================
    // P11 — Engineering Intelligence (read-only, derived from the live model; no new storage)
    // ============================================================================================

    @GetMapping("/knowledge")
    @Operation(summary = "Browse/search the engineering-knowledge catalog",
            description = "Read-only, paginated catalog of derived engineering-knowledge objects "
                    + "(observations, claims, decisions, evidence, knowledge) for the organization. Supports "
                    + "partial-match search (q) and filters by knowledge type, subject artifact type and "
                    + "project, plus sorting — all server-side. Everything is traceable to real artifacts.")
    public ResponseEntity<PageResponse<KnowledgeObject>> knowledge(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String artifactType,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(defaultValue = "recent") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return ResponseEntity.ok(
                intelligence.list(organizationId, q, type, artifactType, projectId, sort, page, size));
    }

    @GetMapping("/knowledge/{id}")
    @Operation(summary = "Get one knowledge node",
            description = "Read-only: a single derived Knowledge object and its traceable links.")
    public ResponseEntity<KnowledgeObject> knowledgeById(@PathVariable UUID organizationId,
                                                         @PathVariable String id) {
        return ResponseEntity.ok(getKnowledge(organizationId, "knowledge", id));
    }

    @GetMapping("/decision/{id}")
    @Operation(summary = "Get one engineering decision",
            description = "Read-only: a derived Decision (version promotion or deprecation) and its rationale.")
    public ResponseEntity<KnowledgeObject> decisionById(@PathVariable UUID organizationId,
                                                       @PathVariable String id) {
        return ResponseEntity.ok(getKnowledge(organizationId, "decision", id));
    }

    @GetMapping("/claim/{id}")
    @Operation(summary = "Get one engineering claim",
            description = "Read-only: a derived Claim and the evidence/decision it is based on.")
    public ResponseEntity<KnowledgeObject> claimById(@PathVariable UUID organizationId,
                                                    @PathVariable String id) {
        return ResponseEntity.ok(getKnowledge(organizationId, "claim", id));
    }

    @GetMapping("/observation/{id}")
    @Operation(summary = "Get one engineering observation",
            description = "Read-only: a derived Observation (a measured evaluation outcome).")
    public ResponseEntity<KnowledgeObject> observationById(@PathVariable UUID organizationId,
                                                          @PathVariable String id) {
        return ResponseEntity.ok(getKnowledge(organizationId, "observation", id));
    }

    @GetMapping("/evidence/{id}")
    @Operation(summary = "Get one piece of engineering evidence",
            description = "Read-only: a derived Evidence object (an evaluation supporting a claim/decision).")
    public ResponseEntity<KnowledgeObject> evidenceById(@PathVariable UUID organizationId,
                                                       @PathVariable String id) {
        return ResponseEntity.ok(getKnowledge(organizationId, "evidence", id));
    }

    @GetMapping("/intelligence/{type}/{entityId}")
    @Operation(summary = "Get the engineering intelligence of an artifact",
            description = "Read-only: what was observed, claimed, decided, what evidence supports it, the "
                    + "durable knowledge that emerged and the engineering memory (the 'why') for one artifact "
                    + "— all derived from the live model.")
    public ResponseEntity<ArtifactIntelligenceResponse> intelligence(@PathVariable UUID organizationId,
                                                                     @PathVariable String type,
                                                                     @PathVariable UUID entityId) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return ResponseEntity.ok(intelligence.intelligenceOf(organizationId, type, entityId));
    }

    @GetMapping("/revisions/{type}/{entityId}")
    @Operation(summary = "Get an artifact's engineering revision timeline (AI Git)",
            description = "Read-only: the artifact's real revisions newest-first, with snapshots, rationale, "
                    + "promotion and rollback metadata. Derived from the artifact's immutable version records.")
    public ResponseEntity<EngineeringRevisionTimeline> revisions(@PathVariable UUID organizationId,
                                                                @PathVariable String type,
                                                                @PathVariable UUID entityId) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return ResponseEntity.ok(intelligence.revisions(organizationId, type, entityId));
    }

    @GetMapping("/compare")
    @Operation(summary = "Compare two engineering revisions",
            description = "Read-only: a field-by-field diff of two revisions of the same artifact (what "
                    + "changed between them). Derived by diffing the two immutable version snapshots.")
    public ResponseEntity<RevisionComparison> compare(@PathVariable UUID organizationId,
                                                      @RequestParam String type,
                                                      @RequestParam UUID entityId,
                                                      @RequestParam String base,
                                                      @RequestParam String target) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return ResponseEntity.ok(intelligence.compare(organizationId, type, entityId, base, target));
    }

    private KnowledgeObject getKnowledge(UUID organizationId, String expectedType, String id) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        accessService.requireMembership(organizationId, actorId);
        return intelligence.get(organizationId, expectedType, id);
    }
}
