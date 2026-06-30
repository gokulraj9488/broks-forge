package com.broksforge.modules.agent.web;

import com.broksforge.modules.agent.domain.AgentCredential;
import com.broksforge.modules.agent.web.dto.AgentCredentialResponse;
import org.mapstruct.Mapper;

/**
 * Maps credentials to their non-sensitive representation. The encrypted secret
 * has no corresponding response field and is therefore never exposed.
 */
@Mapper(componentModel = "spring")
public interface AgentCredentialMapper {

    AgentCredentialResponse toResponse(AgentCredential credential);
}
