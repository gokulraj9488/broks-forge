package com.broksforge.security;

import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deny-by-default security: every business endpoint requires authentication, while the public
 * health probe stays open. Because the security filter chain runs before dispatch, even unknown
 * paths are rejected with 401 rather than leaking existence via 404.
 */
@DisplayName("Security (deny-by-default)")
class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String U = "11111111-1111-1111-1111-111111111111";

    @ParameterizedTest(name = "401 for unauthenticated GET {0}")
    @ValueSource(strings = {
            "/api/v1/organizations",
            "/api/v1/organizations/" + U + "/projects",
            "/api/v1/organizations/" + U + "/projects/" + U + "/agents",
            "/api/v1/organizations/" + U + "/projects/" + U + "/datasets",
            "/api/v1/organizations/" + U + "/projects/" + U + "/prompts",
            "/api/v1/organizations/" + U + "/projects/" + U + "/evaluations",
            "/api/v1/organizations/" + U + "/projects/" + U + "/benchmarks",
    })
    @DisplayName("protected endpoints reject anonymous callers")
    void protectedEndpointsRequireAuth(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the health probe is public")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
