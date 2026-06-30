package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentFramework;
import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.domain.AgentLanguage;
import com.broksforge.modules.agent.domain.AgentStatus;
import com.broksforge.modules.agent.domain.AgentVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Query parameters for searching and filtering agents. Bound from the request
 * query string; all fields are optional.
 *
 * @param q            free-text search across name, slug and description
 * @param framework    exact framework match
 * @param language     exact language match
 * @param visibility   exact visibility match
 * @param status       exact lifecycle status match
 * @param healthStatus exact health status match
 * @param tag          agents carrying this tag
 */
@Schema(name = "AgentFilter")
public record AgentFilter(
        String q,
        AgentFramework framework,
        AgentLanguage language,
        AgentVisibility visibility,
        AgentStatus status,
        AgentHealthStatus healthStatus,
        String tag
) {
}
