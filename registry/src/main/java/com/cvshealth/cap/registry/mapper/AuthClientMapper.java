// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.AuthClient;
import com.cvshealth.cap.registry.model.AuthClientRequest;
import com.cvshealth.cap.registry.model.OAuthClient;
import com.cvshealth.cap.registry.persistent.AuthClientEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import static com.cvshealth.cap.registry.persistent.base.PersistenceConstants.AUTH_CLIENT_FIRST_VERSION;

@Mapper(componentModel = "spring")
public interface AuthClientMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createUser", ignore = true)
    @Mapping(target = "createTimestamp", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updateUser", ignore = true)
    @Mapping(target = "updateTimestamp", ignore = true)
    @Mapping(target = "clientVersion", ignore = true)
    AuthClientEntity toEntity(AuthClientRequest dto, @Context Integer clientVersion);

    @AfterMapping
    default AuthClientEntity afterMapping(AuthClientRequest request, @Context Integer clientVersion,
                                          @MappingTarget AuthClientEntity.AuthClientEntityBuilder<?, ?> out) {
        out.clientVersion(clientVersion == null ? AUTH_CLIENT_FIRST_VERSION : clientVersion);
        return out.build();
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "clientSecret", ignore = true)
    @Mapping(target = "clientName", ignore = true)
    @Mapping(target = "accessTokenExpireMinutes", ignore = true)
    @Mapping(target = "refreshTokenExpireMinutes", ignore = true)
    @Mapping(target = "clientSecretExpiresAt", ignore = true)
    @Mapping(target = "lastAccessTokenIssuedAt", ignore = true)
    OAuthClient toOauthClient(AuthClientRequest dto, @Context Integer clientVersion);

    @AfterMapping
    default OAuthClient afterMapping(AuthClientRequest dto, @Context Integer clientVersion,
                                     @MappingTarget OAuthClient.OAuthClientBuilder<?, ?> out) {
        out.clientName(dto.getClientId());
        out.clientId(buildAuthClientId(dto.getClientId(), clientVersion));
        return out.build();
    }

    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "clientId", source = "entity.clientId")
    @Mapping(target = "description", source = "entity.description")
    AuthClient toDto(AuthClientEntity entity, OAuthClient oAuthClient);

    // Note: not appending v1 to first auth client for clientId to support legacy client before support for multiple auth client secrets was introduced
    static String buildAuthClientId(String clientId, Integer clientVersion) {
        return clientVersion == AUTH_CLIENT_FIRST_VERSION ? clientId : clientId + "-v" + clientVersion;
    }
}
