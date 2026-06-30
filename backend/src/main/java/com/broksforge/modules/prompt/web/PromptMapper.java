package com.broksforge.modules.prompt.web;

import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.prompt.domain.PromptVersion;
import com.broksforge.modules.prompt.web.dto.PromptResponse;
import com.broksforge.modules.prompt.web.dto.PromptSummaryResponse;
import com.broksforge.modules.prompt.web.dto.PromptVersionResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PromptMapper {

    PromptResponse toResponse(Prompt prompt);

    PromptSummaryResponse toSummary(Prompt prompt);

    PromptVersionResponse toVersionResponse(PromptVersion version);
}
