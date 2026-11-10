// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.TeamAuthClient;
import com.cvshealth.cap.registry.persistent.TeamAuthClientEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TeamAuthClientMapper {
    @Mapping(target = "id.teamId", source = "teamId")
    @Mapping(target = "id.authClientId", source = "authClientId")
    TeamAuthClientEntity toEntity(TeamAuthClient dto);

    @Mapping(target = "teamId", source = "id.teamId")
    @Mapping(target = "authClientId", source = "id.authClientId")
    TeamAuthClient toDto(TeamAuthClientEntity entity);
}
