// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.ApplicationViewAuthClient;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.persistent.TeamAuthClientViewEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

import static com.cvshealth.cap.registry.mapper.AuthClientMapper.buildAuthClientId;

@Mapper(componentModel = "spring")
public interface ApplicationViewAuthClientMapper {
    @Mapping(source = "id.authClient", target = ".")
    @Mapping(target = "clientSecret", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "redirectUrls", ignore = true)
    @Mapping(target = "accessTokenExpireMinutes", ignore = true)
    @Mapping(target = "refreshTokenExpireMinutes", ignore = true)
    @Mapping(target = "lastAccessTokenIssuedAt", ignore = true)
    @Mapping(target = "clientSecretExpiresAt", ignore = true)
    @Mapping(target = "role", ignore = true)
    ApplicationViewAuthClient toDto(TeamAuthClientViewEntity entity, @Context List<OAuthClient> oAuthClients, @Context CyclicGraphContext context);

    @AfterMapping
    default void afterMapping(TeamAuthClientViewEntity entity, @Context List<OAuthClient> oAuthClients, @MappingTarget ApplicationViewAuthClient.ApplicationViewAuthClientBuilder out) {
        if (oAuthClients != null && !oAuthClients.isEmpty()) {
            String oAuthClientId = buildAuthClientId(entity.getId().getAuthClient().getClientId(), entity.getId().getAuthClient().getClientVersion());
            OAuthClient oAuthClient = oAuthClients.stream().filter(arac -> arac.getClientId().equals(oAuthClientId))
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
