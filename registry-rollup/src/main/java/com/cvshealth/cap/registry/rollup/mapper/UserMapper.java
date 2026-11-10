// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.mapper;

import com.cvshealth.cap.registry.rollup.model.MemberView;
import com.cvshealth.cap.registry.rollup.model.User;
import com.cvshealth.cap.registry.rollup.model.UserView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(source = "id", target = "externalId")
    @Mapping(source = "mail", target = "email")
    @Mapping(source = "surname", target = "lastName")
    @Mapping(source = "givenName", target = "firstName")
    @Mapping(source = "mailNickname", target = "externalAlias")
    @Mapping(source = "jobTitle", target = "title")
    @Mapping(source = "manager", target = "manager", qualifiedBy = ManagerMapper.class)
    User toDto(com.microsoft.graph.models.User user);

    UserView toUserViewDto(User user);

    @Mapping(source = "displayName", target = "name")
    MemberView toMemberViewDto(User user);

    @ManagerMapper
    static User managerMapper(Object manager) {
        if (manager instanceof com.microsoft.graph.models.User) {
            return INSTANCE.toDto((com.microsoft.graph.models.User)manager);
        }
        return null;
    }
}
