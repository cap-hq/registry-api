// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.mapper;

import com.cvshealth.cap.registry.rollup.model.TeamRollupView;
import com.cvshealth.cap.registry.rollup.model.TeamView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TeamMapper {
    @Mapping(target = "managers", ignore = true)
    @Mapping(target = "lineOfBusiness", ignore = true)
    TeamRollupView toDto(TeamView teamView);
}
