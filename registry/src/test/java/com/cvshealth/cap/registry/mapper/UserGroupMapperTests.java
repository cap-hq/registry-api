// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.Group;
import com.cvshealth.cap.registry.model.UserGroups;
import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.DirectoryObjectCollectionResponse;
import com.microsoft.graph.models.DirectoryRole;
import com.microsoft.graph.models.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UserGroupMapperTests {

    private final UserGroupMapper mapper = new UserGroupMapperImpl();

    @Test
    public void secondaryGroupMapIgnoresNonGroupDirectoryObjects() {
        DirectoryRole role = new DirectoryRole();
        role.setId("role-1");
        role.setDisplayName("Global Reader");

        User user = new User();
        user.setId("user-1");

        List<DirectoryObject> directoryObjects = List.of(role, user, distributionList(), securityGroup());

        List<Group> groups = mapper.secondaryGroupMap(directoryObjects);

        assertThat(groups).hasSize(2)
                .extracting(Group::getMail)
                .containsExactly("cap-devex@example.com", "cap-security@example.com");
    }

    @Test
    public void secondaryGroupMapReturnsNullForNullInput() {
        assertThat(mapper.secondaryGroupMap(null)).isNull();
    }

    @Test
    public void secondaryGroupMapReturnsEmptyListWhenNoGroupsPresent() {
        DirectoryRole role = new DirectoryRole();
        role.setId("role-1");

        assertThat(mapper.secondaryGroupMap(List.of(role))).isEmpty();
    }

    @Test
    public void toDtoMapsMixedMembershipResponse() {
        DirectoryRole role = new DirectoryRole();
        role.setId("role-1");

        DirectoryObjectCollectionResponse response = new DirectoryObjectCollectionResponse();
        response.setValue(List.of(role, distributionList()));

        UserGroups userGroups = mapper.toDto(response);

        assertThat(userGroups.getValue()).hasSize(1);
        Group group = userGroups.getValue().getFirst();
        assertThat(group.getMail()).isEqualTo("cap-devex@example.com");
        assertThat(group.getMailEnabled()).isEqualTo("true");
        assertThat(group.getSecurityEnabled()).isEqualTo("false");
    }

    private com.microsoft.graph.models.Group distributionList() {
        com.microsoft.graph.models.Group group = new com.microsoft.graph.models.Group();
        group.setId("group-1");
        group.setDisplayName("CAP DevEx");
        group.setMail("cap-devex@example.com");
        group.setMailEnabled(true);
        group.setSecurityEnabled(false);
        return group;
    }

    private com.microsoft.graph.models.Group securityGroup() {
        com.microsoft.graph.models.Group group = new com.microsoft.graph.models.Group();
        group.setId("group-2");
        group.setDisplayName("CAP Security");
        group.setMail("cap-security@example.com");
        group.setMailEnabled(true);
        group.setSecurityEnabled(true);
        return group;
    }
}

