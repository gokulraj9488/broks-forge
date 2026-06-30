package com.broksforge.security.apikey;

import java.util.UUID;

/**
 * Principal representing a successfully authenticated project API key.
 *
 * @param apiKeyId       the API key's identifier
 * @param projectId      the project the key grants access to
 * @param organizationId the organization owning the project
 * @param name           human-readable key name (for auditing/logging)
 */
public record ApiKeyPrincipal(
        UUID apiKeyId,
        UUID projectId,
        UUID organizationId,
        String name
) {
}
