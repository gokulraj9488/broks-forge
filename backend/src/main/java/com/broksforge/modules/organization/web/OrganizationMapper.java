package com.broksforge.modules.organization.web;

import com.broksforge.modules.organization.domain.Organization;
import com.broksforge.modules.organization.domain.OrganizationMember;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.web.dto.OrganizationMemberResponse;
import com.broksforge.modules.organization.web.dto.OrganizationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrganizationMapper {

    @Mapping(target = "currentUserRole", source = "currentUserRole")
    @Mapping(target = "memberCount", source = "memberCount")
    OrganizationResponse toResponse(Organization organization, OrganizationRole currentUserRole, long memberCount);

    @Mapping(target = "id", source = "member.id")
    @Mapping(target = "userId", source = "member.userId")
    @Mapping(target = "role", source = "member.role")
    @Mapping(target = "joinedAt", source = "member.joinedAt")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "fullName", source = "fullName")
    OrganizationMemberResponse toMemberResponse(OrganizationMember member, String email, String fullName);
}
