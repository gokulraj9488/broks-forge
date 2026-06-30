package com.broksforge.modules.user.web;

import com.broksforge.modules.user.domain.Role;
import com.broksforge.modules.user.domain.User;
import com.broksforge.modules.user.web.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps {@link User} aggregates to their public {@link UserResponse} form.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "fullName", expression = "java(user.fullName())")
    @Mapping(target = "roles", expression = "java(mapRoles(user.getRoles()))")
    UserResponse toResponse(User user);

    default Set<String> mapRoles(Set<Role> roles) {
        return roles.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
