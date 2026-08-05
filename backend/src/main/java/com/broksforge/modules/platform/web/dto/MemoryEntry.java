package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A persistent engineering-memory entry — a "why did this happen" question and its answer, derived from a real
 * engineering {@link KnowledgeObject Decision}. Memory is not a separate store: it is a reading of the same
 * engineering knowledge model, so it stays a single source of truth and never drifts from what actually
 * happened.
 */
@Schema(name = "MemoryEntry", description = "A derived 'why' question and answer, backed by a real decision")
public record MemoryEntry(
        @Schema(description = "The decision id this memory is derived from") String decisionId,
        @Schema(description = "The engineering question, e.g. \"Why was X promoted?\"") String question,
        @Schema(description = "The answer, from the recorded rationale or the decision itself") String answer,
        @Schema(description = "When the decision was made") Instant at
) {
}
