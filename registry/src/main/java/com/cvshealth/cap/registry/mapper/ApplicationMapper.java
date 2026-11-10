// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.Application;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApplicationMapper {
    @Mapping(target = "dataPerimeter", defaultValue = "UNRESTRICTED")
    ApplicationEntity toEntity(Application dto);
    Application toDto(ApplicationEntity entity);
}
