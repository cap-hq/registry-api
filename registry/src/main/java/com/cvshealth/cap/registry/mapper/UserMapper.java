// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Optional;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(source = "id", target = "externalId")
    @Mapping(source = "mail", target = "email")
    @Mapping(source = "surname", target = "lastName")
    @Mapping(source = "givenName", target = "firstName")
    @Mapping(source = "mailNickname", target = "externalAlias")
    @Mapping(source = "onPremisesSamAccountName", target = "externalAliasAlternate")
    @Mapping(source = "accountEnabled", target = "accountEnabled")
    @Mapping(source = "manager", target = "manager", qualifiedBy = ManagerMapper.class)
    User toDto(com.microsoft.graph.models.User User);

    @ManagerMapper
    static User managerMapper(Object manager) {
        if (manager instanceof com.microsoft.graph.models.User) {
            return INSTANCE.toDto((com.microsoft.graph.models.User)manager);
        }
        return null;
    }
}
