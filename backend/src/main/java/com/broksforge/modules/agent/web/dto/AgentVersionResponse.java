package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.DeploymentEnvironment;
import com.broksforge.modules.agent.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "AgentVersionResponse")
public record AgentVersionResponse(
        UUID id,
        UUID agentId,
        String versionNumber,
        long sequence,
        String model,
        LlmProvider provider,
        String frameworkVersion,
        String gitCommitSha,
        String promptVersion,
        DeploymentEnvironment environment,
        String releaseNotes,
        Instant deploymentTimestamp,
        boolean active,
        boolean rollbackReady,
        Instant createdAt
) {
}
