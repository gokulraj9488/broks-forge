package com.broksforge.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base class for full-context integration tests. Boots the real Spring context against a
 * <b>real PostgreSQL 16</b> (the same image production uses) via a singleton Testcontainer, so
 * Flyway migrations run and Hibernate's {@code ddl-auto=validate} exercises the true schema.
 *
 * <p>The container is started once for the whole test run (static singleton pattern) and shared by
 * every subclass, which is far faster than a container per class. It is never explicitly stopped —
 * the Ryuk sidecar / JVM shutdown reclaims it.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** A unique e-mail so each test is independent of the shared database. */
    protected String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    /** Registers a fresh user and returns its access token (bearer, without the scheme prefix). */
    protected String registerAndGetToken(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody(email, password, "Test", "User"));
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }

    public record RegisterBody(String email, String password, String firstName, String lastName) {
    }
}
