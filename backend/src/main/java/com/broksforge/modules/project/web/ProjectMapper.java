package com.broksforge.modules.project.web;

import com.broksforge.modules.project.domain.Project;
import com.broksforge.modules.project.web.dto.ProjectResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);
}
