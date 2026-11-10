// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.ApplicationTransferView;
import com.cvshealth.cap.registry.persistent.ApplicationTransferViewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApplicationTransferViewMapper {
    @Mapping(target = "receiveTeam.nameNormalized", ignore = true)
    @Mapping(target = "sourceTeam.nameNormalized", ignore = true)
    @Mapping(target = "application.teamId", ignore = true)
    ApplicationTransferViewEntity toEntity(ApplicationTransferView dto);

    ApplicationTransferView toDto(ApplicationTransferViewEntity entity);
}
