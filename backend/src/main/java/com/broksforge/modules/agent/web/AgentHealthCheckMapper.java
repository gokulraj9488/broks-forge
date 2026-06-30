package com.broksforge.modules.agent.web;

import com.broksforge.modules.agent.domain.AgentHealthCheck;
import com.broksforge.modules.agent.web.dto.AgentHealthCheckResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AgentHealthCheckMapper {

    AgentHealthCheckResponse toResponse(AgentHealthCheck healthCheck);
}
