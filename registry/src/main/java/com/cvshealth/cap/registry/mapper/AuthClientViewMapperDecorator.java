// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.config.AuthClientConfig;
import com.cvshealth.cap.registry.model.AuthClientView;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.persistent.AuthClientViewEntity;
import org.mapstruct.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public abstract class AuthClientViewMapperDecorator implements AuthClientViewMapper {

    @Autowired
    @Qualifier("delegate")
    private AuthClientViewMapper delegate;

    @Autowired
    private AuthClientConfig authClientConfig;

    @Override
    public AuthClientView toDto(AuthClientViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context CyclicGraphContext context) {
        AuthClientView view = delegate.toDto(entity, oAuthClients, context);
        if (view.getTeams() != null) {
            view.getTeams().forEach(team -> team.setRole(authClientConfig.getTeamRole()));
        }
        return view;
    }
}
