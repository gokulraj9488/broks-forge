package com.broksforge.modules.agent.web;

import com.broksforge.modules.agent.service.AgentCredentialService;
import com.broksforge.modules.agent.web.dto.AgentCredentialResponse;
import com.broksforge.modules.agent.web.dto.CredentialTestResponse;
import com.broksforge.modules.agent.web.dto.SetAgentCredentialRequest;
import com.broksforge.modules.agent.web.dto.TestAgentCredentialRequest;
import com.broksforge.modules.agent.web.dto.UpdateAgentCredentialRequest;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Agent credential management. Secrets are write-only: they are encrypted on
 * receipt and never returned. All operations require ADMIN or higher.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/agents/{agentId}/credentials")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Agent Credentials", description = "Manage agent authentication secrets (encrypted at rest)")
public class AgentCredentialController {

    private final AgentCredentialService credentialService;

    public AgentCredentialController(AgentCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    @Operation(summary = "Set the agent credential",
            description = "Encrypts and stores the secret; deactivates any previous credential. Requires ADMIN.")
    public ResponseEntity<AgentCredentialResponse> set(@PathVariable UUID organizationId,
                                                       @PathVariable UUID projectId,
                                                       @PathVariable UUID agentId,
                                                       @Valid @RequestBody SetAgentCredentialRequest request) {
        AgentCredentialResponse response = credentialService.set(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{credentialId}")
    @Operation(summary = "Update a credential in place",
            description = "Edits label / header settings and optionally rotates the secret (leave the secret "
                    + "blank to keep it). Requires ADMIN.")
    public ResponseEntity<AgentCredentialResponse> update(@PathVariable UUID organizationId,
                                                          @PathVariable UUID projectId,
                                                          @PathVariable UUID agentId,
                                                          @PathVariable UUID credentialId,
                                                          @Valid @RequestBody UpdateAgentCredentialRequest request) {
        return ResponseEntity.ok(credentialService.update(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, credentialId, request));
    }

    @GetMapping
    @Operation(summary = "List credential metadata", description = "Returns metadata only (never secrets). Requires ADMIN.")
    public ResponseEntity<List<AgentCredentialResponse>> list(@PathVariable UUID organizationId,
                                                              @PathVariable UUID projectId,
                                                              @PathVariable UUID agentId) {
        return ResponseEntity.ok(credentialService.list(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId));
    }

    @PostMapping("/{credentialId}/test")
    @Operation(summary = "Test a saved credential",
            description = "Calls the agent endpoint with this credential and records the outcome. "
                    + "Returns a reachability/auth verdict only — never the secret. Requires ADMIN.")
    public ResponseEntity<CredentialTestResponse> test(@PathVariable UUID organizationId,
                                                       @PathVariable UUID projectId,
                                                       @PathVariable UUID agentId,
                                                       @PathVariable UUID credentialId) {
        return ResponseEntity.ok(credentialService.test(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, credentialId));
    }

    @PostMapping("/test")
    @Operation(summary = "Test an unsaved credential (dry run)",
            description = "Verifies a secret against the agent endpoint before saving it. The secret is used "
                    + "only for the probe and never stored. Requires ADMIN.")
    public ResponseEntity<CredentialTestResponse> testDraft(@PathVariable UUID organizationId,
                                                            @PathVariable UUID projectId,
                                                            @PathVariable UUID agentId,
                                                            @Valid @RequestBody TestAgentCredentialRequest request) {
        return ResponseEntity.ok(credentialService.testDraft(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, request));
    }

    @DeleteMapping("/{credentialId}")
    @Operation(summary = "Delete a credential", description = "Requires ADMIN or higher.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID agentId,
                                       @PathVariable UUID credentialId) {
        credentialService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, credentialId);
        return ResponseEntity.noContent().build();
    }
}
