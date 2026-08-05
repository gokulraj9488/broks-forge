package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Observability snapshot of the engineering platform for one organization — a read-only projection of the
 * kernel's append-only ledger. Exposes user-meaningful health (integrity, chain consistency, ledger size)
 * without leaking kernel implementation details.
 */
@Schema(name = "PlatformHealthResponse",
        description = "Read-only integrity snapshot of the engineering platform for an organization")
public record PlatformHealthResponse(
        @Schema(description = "Whether the platform is enabled for this deployment") boolean enabled,
        @Schema(description = "Whether the append-only engineering ledger verifies end-to-end") boolean chainValid,
        @Schema(description = "Whether the knowledge integrity scan found no problems") boolean integrityClean,
        @Schema(description = "Number of integrity problems found (0 when clean)") long integrityErrors,
        @Schema(description = "Number of entries in the organization's engineering ledger") long ledgerSize
) {
}
