// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.MemberView;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.persistent.MemberViewEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = MemberViewTeamMapper.class)
public interface MemberViewMapper {
    @Mapping(source = "teamMemberships", target = "teams")
    MemberView toDto(MemberViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context Set<String> decoratorAttributes, @Context CyclicGraphContext context);

    @MappingIgnore
    default MemberView toDto(MemberViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context Set<String> decoratorAttributes) {
        return toDto(entity, oAuthClients, decoratorAttributes, new CyclicGraphContext());
    }
}
