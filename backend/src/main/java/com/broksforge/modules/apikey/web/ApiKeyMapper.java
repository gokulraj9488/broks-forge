package com.broksforge.modules.apikey.web;

import com.broksforge.modules.apikey.domain.ApiKey;
import com.broksforge.modules.apikey.web.dto.ApiKeyResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApiKeyMapper {

    ApiKeyResponse toResponse(ApiKey apiKey);
}
