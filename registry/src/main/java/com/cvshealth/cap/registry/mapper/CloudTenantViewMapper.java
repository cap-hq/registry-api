// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.CloudTenant;
import com.cvshealth.cap.registry.model.CloudTenantView;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CloudTenantViewMapper {

    CloudTenantView toView(CloudTenant cloudTenant);

    List<CloudTenantView> toView(List<CloudTenant> cloudTenants);
}
