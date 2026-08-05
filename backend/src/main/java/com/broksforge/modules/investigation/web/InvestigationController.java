package com.broksforge.modules.investigation.web;

import com.broksforge.modules.investigation.service.InvestigationService;
import com.broksforge.modules.investigation.web.dto.InvestigationDtos.Investigation;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The Root Cause Explorer (P13) — one assembled engineering investigation per evaluation.
 *
 * <p>Org-scoped rather than project-scoped, because an investigation legitimately reaches past its own
 * project for precedent and lineage; the project is a narrowing parameter, exactly as it is for Brok.
 * Membership is enforced before anything is read, and the evaluation is loaded through the evaluation
 * module's own service, so tenant scoping is checked twice by two independent paths.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/investigations")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Root Cause Explorer", description = "Assemble an engineering investigation for an evaluation")
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class InvestigationController {

    private final InvestigationService investigations;
    private final OrganizationAccessService access;

    public InvestigationController(InvestigationService investigations, OrganizationAccessService access) {
        this.investigations = investigations;
        this.access = access;
    }

    @GetMapping("/evaluation/{evaluationId}")
    @Operation(summary = "Assemble the investigation for an evaluation",
            description = "Gathers the evaluation, its failed runs, the artifacts it ran against, their AI Git "
                    + "revisions, engineering knowledge, decisions, memory, earlier failures on the same ground "
                    + "and related evaluations — as a timeline, a causal chain and the engineering story.")
    public Investigation investigate(@PathVariable UUID organizationId,
                                     @PathVariable UUID evaluationId,
                                     @RequestParam(required = false) UUID projectId) {
        UUID actorId = SecurityUtils.requireCurrentUserId();
        access.requireMembership(organizationId, actorId);
        return investigations.investigate(actorId, organizationId, projectId, evaluationId);
    }
}
