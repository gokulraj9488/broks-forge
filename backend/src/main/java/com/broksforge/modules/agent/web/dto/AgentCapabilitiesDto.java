package com.broksforge.modules.agent.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Agent capability flags plus an open metadata map. Used in both requests and
 * responses. When supplied on update, the whole capability set is replaced.
 */
@Schema(name = "AgentCapabilities")
public record AgentCapabilitiesDto(
        boolean streaming,
        boolean memory,
        boolean rag,
        boolean toolCalling,
        boolean structuredOutput,
        boolean reasoning,
        boolean multiAgent,
        @Schema(description = "Open, forward-compatible metadata for capabilities not yet first-class")
        Map<String, Object> customMetadata
) {
}
