// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.Team;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.DecoratedWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
@DecoratedWith(TeamMapperDecorator.class)
public interface TeamMapper {
    @Mapping(source = "name", target = "nameNormalized", qualifiedByName = "normalizeTeamName")
    TeamEntity toEntity(Team dto);

    @Mapping(target = "slackChannelName", ignore = true)
    Team toDto(TeamEntity entity);

    @Named("normalizeTeamName")
    default String normalizeTeamName(String name) {
        if (!StringUtils.isEmpty(name)) {
            return name.toLowerCase().replaceAll("[^a-z0-9]", "");
        }
        return name;
    }
}
