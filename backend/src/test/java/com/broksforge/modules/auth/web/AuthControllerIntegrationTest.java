package com.broksforge.modules.auth.web;

import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth API (register / login / guard)")
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "StrongPass!2026";

    private String register(String email) throws Exception {
        return objectMapper.writeValueAsString(
                new RegisterBody(email, PASSWORD, "Test", "User"));
    }

    @Test
    @DisplayName("registers a new account and returns tokens (201)")
    void registerSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(register(uniqueEmail())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("rejects a duplicate e-mail (409)")
    void duplicateEmailConflicts() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(register(email)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(register(email)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("rejects invalid registration payloads (400)")
    void validationRejects() throws Exception {
        String blankEmail = objectMapper.writeValueAsString(new RegisterBody("", PASSWORD, "A", "B"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(blankEmail))
                .andExpect(status().isBadRequest());

        String weakPassword = objectMapper.writeValueAsString(new RegisterBody(uniqueEmail(), "weak", "A", "B"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(weakPassword))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("logs in with valid credentials and rejects wrong ones")
    void loginFlow() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(register(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "WrongPass!1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid access token authorizes a protected endpoint (200)")
    void tokenAuthorizesProtectedEndpoint() throws Exception {
        String token = registerAndGetToken(uniqueEmail(), PASSWORD);
        mockMvc.perform(get("/api/v1/organizations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
