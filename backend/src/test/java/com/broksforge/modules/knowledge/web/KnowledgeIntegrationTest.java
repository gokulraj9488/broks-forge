package com.broksforge.modules.knowledge.web;

import com.broksforge.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform-global Engineering Knowledge Graph: Flyway-seeded reference data (20 nodes, 20 edges),
 * read-only via the API, not tenant-scoped (any authenticated user), with filtering and a
 * not-found path for unknown node keys.
 */
@DisplayName("Knowledge graph (global reference data)")
class KnowledgeIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/knowledge";
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndGetToken(uniqueEmail(), "StrongPass!2026");
    }

    @Test
    @DisplayName("lists the 20 seeded nodes")
    void listsSeededNodes() throws Exception {
        JsonNode nodes = apiGet(token, BASE + "/nodes", 200);
        assertThat(nodes.isArray()).isTrue();
        assertThat(nodes).hasSize(20);
    }

    @Test
    @DisplayName("filters nodes by type")
    void filtersByType() throws Exception {
        JsonNode nodes = apiGet(token, BASE + "/nodes?type=FAILURE_MODE", 200);
        assertThat(nodes).isNotEmpty();
        for (JsonNode node : nodes) {
            assertThat(node.get("nodeType").asText()).isEqualTo("FAILURE_MODE");
        }
    }

    @Test
    @DisplayName("gets a node by key with its neighbours")
    void getsNodeByKey() throws Exception {
        JsonNode detail = apiGet(token, BASE + "/nodes/TIMEOUT", 200);
        assertThat(detail.get("node").get("nodeKey").asText()).isEqualTo("TIMEOUT");
        assertThat(detail.get("neighbors").isArray()).isTrue();
    }

    @Test
    @DisplayName("returns 404 for an unknown node key")
    void unknownKeyIsNotFound() throws Exception {
        apiGet(token, BASE + "/nodes/NOT_A_REAL_KEY", 404);
    }

    @Test
    @DisplayName("returns the full graph (20 nodes, 20 edges)")
    void returnsGraph() throws Exception {
        JsonNode graph = apiGet(token, BASE + "/graph", 200);
        assertThat(graph.get("nodes")).hasSize(20);
        assertThat(graph.get("edges")).hasSize(20);
    }

    @Test
    @DisplayName("requires authentication")
    void requiresAuth() throws Exception {
        apiGet(null, BASE + "/nodes", 401);
    }
}
