package com.broksforge.modules.user.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for the {@code user} profile endpoints that back the frontend <b>Settings</b> page:
 * read own profile, partial-update first/last name, validation, and the deliberate constraints
 * (email is not updatable here; only the authenticated user is addressable via {@code /me}).
 */
@DisplayName("User profile / Settings")
class UserProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String ME = "/api/v1/users/me";
    private String email;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        email = uniqueEmail();
        token = registerAndGetToken(email, "StrongPass!2026");
    }

    @Test
    @DisplayName("returns the authenticated user's profile")
    void getsOwnProfile() throws Exception {
        JsonNode me = apiGet(token, ME, 200);
        assertThat(me.get("email").asText()).isEqualTo(email);
        assertThat(me.get("firstName").asText()).isEqualTo("Test");
        assertThat(me.get("lastName").asText()).isEqualTo("User");
        assertThat(me.get("roles").toString()).contains("USER");
    }

    @Test
    @DisplayName("updates the first and last name")
    void updatesName() throws Exception {
        JsonNode updated = apiPatch(token, ME, Map.of("firstName", "Ada", "lastName", "Lovelace"), 200);
        assertThat(updated.get("firstName").asText()).isEqualTo("Ada");
        assertThat(updated.get("lastName").asText()).isEqualTo("Lovelace");
        assertThat(updated.get("fullName").asText()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("leaves a field unchanged when it is omitted from the patch")
    void omittedFieldUnchanged() throws Exception {
        apiPatch(token, ME, Map.of("firstName", "Grace"), 200);
        // patch only lastName; firstName is omitted (null) -> unchanged
        apiPatch(token, ME, Map.of("lastName", "Hopper"), 200);
        JsonNode me = apiGet(token, ME, 200);
        assertThat(me.get("firstName").asText()).isEqualTo("Grace");
        assertThat(me.get("lastName").asText()).isEqualTo("Hopper");
    }

    @Test
    @DisplayName("clears a field when a blank value is sent (trim-to-null)")
    void blankClearsField() throws Exception {
        apiPatch(token, ME, Map.of("firstName", "Grace"), 200);
        apiPatch(token, ME, Map.of("firstName", "   "), 200);
        JsonNode me = apiGet(token, ME, 200);
        assertThat(me.get("firstName")).isNull(); // absent because null is omitted from the response
        assertThat(me.get("fullName").asText()).isEqualTo("User"); // last name only
    }

    @Test
    @DisplayName("rejects an over-long name with 400")
    void rejectsOverLongName() throws Exception {
        apiPatch(token, ME, Map.of("firstName", "x".repeat(101)), 400);
    }

    @Test
    @DisplayName("ignores an email field — email is not updatable via the profile endpoint")
    void emailNotUpdatable() throws Exception {
        apiPatch(token, ME, Map.of("firstName", "Same", "email", "hacker@evil.com"), 200);
        assertThat(apiGet(token, ME, 200).get("email").asText()).isEqualTo(email);
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuth() throws Exception {
        apiGet(null, ME, 401);
    }
}
