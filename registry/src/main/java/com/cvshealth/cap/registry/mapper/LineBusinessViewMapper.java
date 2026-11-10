// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.LineBusinessManagerView;
import com.cvshealth.cap.registry.model.LineBusinessView;
import com.cvshealth.cap.registry.persistent.LineBusinessManagerEntity;
import com.cvshealth.cap.registry.persistent.LineBusinessViewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LineBusinessViewMapper {

    @Mapping(target = "managers", ignore = true)
    LineBusinessViewEntity toEntity(LineBusinessView dto);

    LineBusinessView toDto(LineBusinessViewEntity entity);

    LineBusinessManagerView toDto(LineBusinessManagerEntity entity);
}
