// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.microsoft.graph.models.Group;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder (disableBuilder = true))
public interface GroupMapper {
    @Mapping( source = "odataType", target = "odata_type")
    @Mapping( source = "displayName", target = "displayName")
    @Mapping( source = "mailEnabled", target = "mailEnabled")
    @Mapping( source = "securityEnabled", target = "securityEnabled")
    com.cvshealth.cap.registry.model.Group toDto(Group Group);
}
