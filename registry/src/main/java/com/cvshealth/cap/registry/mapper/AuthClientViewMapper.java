// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.AuthClientView;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.persistent.AuthClientViewEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.DecoratedWith;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

import static com.cvshealth.cap.registry.mapper.AuthClientMapper.buildAuthClientId;

@Mapper(componentModel = "spring", uses = AuthClientViewTeamMapper.class)
@DecoratedWith(AuthClientViewMapperDecorator.class)
public interface AuthClientViewMapper {
    @Mapping(source = "teamAuthClients", target = "teams")
    @Mapping(target = "clientSecret", ignore = true)
    @Mapping(target = "clientSecretExpiresAt", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "redirectUrls", ignore = true)
    @Mapping(target = "accessTokenExpireMinutes", ignore = true)
    @Mapping(target = "refreshTokenExpireMinutes", ignore = true)
    @Mapping(target = "lastAccessTokenIssuedAt", ignore = true)
    AuthClientView toDto(AuthClientViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context CyclicGraphContext context);

    @MappingIgnore
    default AuthClientView toDto(AuthClientViewEntity entity, List<OAuthClient> oAuthClients) {
        return toDto(entity, oAuthClients, new CyclicGraphContext());
    }

    @AfterMapping
    default void afterMapping(AuthClientViewEntity entity, @Context List<OAuthClient> oAuthClients, @MappingTarget AuthClientView.AuthClientViewBuilder out) {
        if (oAuthClients != null && !oAuthClients.isEmpty()) {
            OAuthClient oAuthClient = oAuthClients.stream().filter(arac -> arac.getClientId().equals(buildAuthClientId(entity.getClientId(), entity.getClientVersion())))
                    .findFirst().orElse(null);
            if (oAuthClient != null) {
                out.type(oAuthClient.getType());
                out.clientSecret(oAuthClient.getClientSecret());
                out.clientSecretExpiresAt(oAuthClient.getClientSecretExpiresAt());
                out.redirectUrls(oAuthClient.getRedirectUrls());
                out.accessTokenExpireMinutes(oAuthClient.getAccessTokenExpireMinutes());
                out.refreshTokenExpireMinutes(oAuthClient.getRefreshTokenExpireMinutes());
                out.lastAccessTokenIssuedAt(oAuthClient.getLastAccessTokenIssuedAt());
            }
        }
    }
}
