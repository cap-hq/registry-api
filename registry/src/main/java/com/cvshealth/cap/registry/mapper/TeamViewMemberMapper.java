// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.TeamViewMember;
import com.cvshealth.cap.registry.persistent.TeamMemberViewEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TeamViewMemberMapper {
    @Mapping(source = "id.member", target = ".")
    @Mapping(source = "role", target = "role")
    TeamViewMember toDto(TeamMemberViewEntity entity, @Context CyclicGraphContext context);
}
