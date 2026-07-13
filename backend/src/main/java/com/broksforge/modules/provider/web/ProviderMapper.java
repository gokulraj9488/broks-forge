package com.broksforge.modules.provider.web;

import com.broksforge.modules.agent.domain.AgentCapabilities;
import com.broksforge.modules.agent.web.dto.AgentCapabilitiesDto;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.web.dto.ProviderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProviderMapper {

    @Mapping(target = "apiKeyConfigured", source = "apiKeyConfigured")
    @Mapping(target = "linkedAgentCount", source = "linkedAgentCount")
    @Mapping(target = "modelCount", source = "modelCount")
    @Mapping(target = "capabilities", source = "provider.capabilities")
    ProviderResponse toResponse(Provider provider, boolean apiKeyConfigured, int linkedAgentCount, int modelCount);

    AgentCapabilitiesDto toCapabilitiesDto(AgentCapabilities capabilities);
}
