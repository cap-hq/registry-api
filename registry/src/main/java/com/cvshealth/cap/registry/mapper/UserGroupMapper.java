// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.Group;
import com.cvshealth.cap.registry.model.UserGroups;
import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.DirectoryObjectCollectionResponse;
import java.util.List;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface UserGroupMapper {
    @Mapping(source = "odataCount", target = "odataCount")
    @Mapping(source = "value", target = "value", qualifiedByName = "namedMapGroup")
    @Mapping(source = "additionalData", target = "additionalData")
    UserGroups toDto(DirectoryObjectCollectionResponse userGroups);

    @Named("namedMapGroup")
    default List<Group> secondaryGroupMap (List<DirectoryObject> secondaryValue){
        if(secondaryValue == null){
            return null;
        }
        GroupMapper groupMapper = Mappers.getMapper(GroupMapper.class);

        return secondaryValue.stream()
                .filter(com.microsoft.graph.models.Group.class::isInstance)
                .map(com.microsoft.graph.models.Group.class::cast)
                .map(groupMapper::toDto)
                .toList();
    }
}
