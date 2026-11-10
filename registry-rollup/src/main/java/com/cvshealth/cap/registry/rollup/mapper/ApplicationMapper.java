// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.mapper;

import com.cvshealth.cap.registry.rollup.model.ApplicationRollupView;
import com.cvshealth.cap.registry.rollup.model.ApplicationView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApplicationMapper {
    @Mapping(source = "team.name", target = "teamName")
    @Mapping(target = "managers", ignore = true)
    @Mapping(target = "lineOfBusiness", ignore = true)
    ApplicationRollupView toDto(ApplicationView applicationView);
}
