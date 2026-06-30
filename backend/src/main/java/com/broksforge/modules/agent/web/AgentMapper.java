package com.broksforge.modules.agent.web;

import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentCapabilities;
import com.broksforge.modules.agent.web.dto.AgentCapabilitiesDto;
import com.broksforge.modules.agent.web.dto.AgentResponse;
import com.broksforge.modules.agent.web.dto.AgentSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AgentMapper {

    @Mapping(target = "tags", source = "tags")
    @Mapping(target = "capabilities", source = "agent.capabilities")
    AgentResponse toResponse(Agent agent, List<String> tags);

    @Mapping(target = "tags", source = "tags")
    AgentSummaryResponse toSummary(Agent agent, List<String> tags);

    AgentCapabilitiesDto toCapabilitiesDto(AgentCapabilities capabilities);
}
