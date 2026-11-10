// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.ApplicationTransfer;
import com.cvshealth.cap.registry.persistent.ApplicationTransferEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApplicationTransferMapper {
    ApplicationTransferEntity toEntity(ApplicationTransfer dto);

    ApplicationTransfer toDto(ApplicationTransferEntity entity);
}
