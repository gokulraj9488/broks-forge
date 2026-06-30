package com.broksforge.modules.agent.web;

import com.broksforge.modules.agent.domain.AgentVersion;
import com.broksforge.modules.agent.web.dto.AgentVersionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AgentVersionMapper {

    AgentVersionResponse toResponse(AgentVersion version);
}
