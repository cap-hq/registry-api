// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.ApplicationView;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.persistent.ApplicationViewEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = {ApplicationViewTeamMapper.class})
public interface ApplicationViewMapper {

    ApplicationView toDto(ApplicationViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context Set<String> decoratorAttributes, @Context CyclicGraphContext context);

    @MappingIgnore
    default ApplicationView toDto(ApplicationViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context Set<String> decoratorAttributes) {
        return toDto(entity, oAuthClients, decoratorAttributes, new CyclicGraphContext());
    }
}