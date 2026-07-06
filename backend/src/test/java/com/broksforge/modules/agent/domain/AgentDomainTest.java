package com.broksforge.modules.agent.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Agent aggregate (domain behaviour)")
class AgentDomainTest {

    @Test
    @DisplayName("a new agent is active, undeleted, private and unknown-health by default")
    void sensibleDefaults() {
        Agent agent = new Agent();
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ACTIVE);
        assertThat(agent.isArchived()).isFalse();
        assertThat(agent.isDeleted()).isFalse();
        assertThat(agent.getVisibility()).isEqualTo(AgentVisibility.PRIVATE);
        assertThat(agent.getAuthType()).isEqualTo(AgentAuthType.NONE);
        assertThat(agent.getHealthStatus()).isEqualTo(AgentHealthStatus.UNKNOWN);
        assertThat(agent.getCapabilities()).isNotNull();
    }

    @Test
    @DisplayName("archive / unarchive toggle lifecycle status")
    void archiveToggles() {
        Agent agent = new Agent();
        agent.archive();
        assertThat(agent.isArchived()).isTrue();
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ARCHIVED);
        agent.unarchive();
        assertThat(agent.isArchived()).isFalse();
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ACTIVE);
    }

    @Test
    @DisplayName("soft delete stamps the deleted flag, timestamp and actor")
    void softDeleteStamps() {
        Agent agent = new Agent();
        UUID actor = UUID.randomUUID();
        agent.softDelete(actor);
        assertThat(agent.isDeleted()).isTrue();
        assertThat(agent.getDeletedAt()).isNotNull();
        assertThat(agent.getDeletedBy()).isEqualTo(actor);
    }

    @Test
    @DisplayName("applyHealth records the latest status and time")
    void applyHealth() {
        Agent agent = new Agent();
        Instant now = Instant.now();
        agent.applyHealth(AgentHealthStatus.HEALTHY, now);
        assertThat(agent.getHealthStatus()).isEqualTo(AgentHealthStatus.HEALTHY);
        assertThat(agent.getLastHealthCheckAt()).isEqualTo(now);
    }
}
