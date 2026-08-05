package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A directed relationship from one engineering-knowledge object to another knowledge object or to a real
 * artifact. The {@code id} is the target's stable node id ({@code "<type>:<id>"} for artifacts, or the target
 * knowledge object's id); {@code relation} is the reasoning verb (derivedFrom / about / supports / basedOn /
 * supersedes / informedBy / summarizes / concerns). Every link is traceable to something real.
 */
@Schema(name = "KnowledgeLink", description = "A traceable link from a knowledge object to an artifact or another knowledge object")
public record KnowledgeLink(
        @Schema(description = "Target id — an artifact node id or another knowledge object id") String id,
        @Schema(description = "Target kind (artifact type, or knowledge object type)") String type,
        @Schema(description = "Reasoning relation verb") String relation,
        @Schema(description = "Target display label") String label
) {
}
