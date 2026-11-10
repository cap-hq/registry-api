// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.RegistryIntegrationTests;
import com.cvshealth.cap.registry.rollup.model.User;
import com.microsoft.kiota.RequestInformation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AzureAdUserServiceIntegrationTests extends RegistryIntegrationTests {
    @Autowired
    private AzureAdUserService azureAdUserService;

    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final String UNKNOWN_ID = "11a111aa-aa11-1a11-a111-1aa1aa111aaa";
    private static final String LOGIN_ID = "onetester";

    @Test
    public void userExists() {
        assertThat(azureAdUserService.exists(ID)).isTrue();
    }

    @Test
    public void userDoesNotExist() {
        assertThat(azureAdUserService.exists(UNKNOWN_ID)).isFalse();
    }

    @Test
    public void findById() {
        var user = azureAdUserService.findById(ID).get();
        assertThat(user.getExternalId()).isEqualTo(ID);
        assertThat(user.getDisplayName()).isEqualTo("Tester, One");
        assertThat(user.getFirstName()).isEqualTo("One");
        assertThat(user.getLastName()).isEqualTo("Tester");
        assertThat(user.getEmail()).isEqualToIgnoringCase("one.tester@example.com");
        assertThat(user.getExternalAlias()).isEqualTo(LOGIN_ID);
        assertThat(user.getManager()).isNotNull();
        assertThat(user.getManager().getManager()).isNotNull();
        assertThat(user.getManager().getManager().getManager()).isNotNull();
    }

    @Test
    public void findByIdNotFound() {
        assertThat(azureAdUserService.findById(UNKNOWN_ID).isEmpty()).isTrue();
    }

    @Test
    public void batchFindUsers() {
        // given
        List<RequestInformation> requests = List.of(
                azureAdUserService.getByUserIdRequestInformation(ID),
                azureAdUserService.getByUserIdRequestInformation(UNKNOWN_ID)
        );

        // when
        List<User> users = azureAdUserService.batchFindUsers(requests);

        // then
        assertThat(users).isNotNull();
        assertThat(users.size()).isEqualTo(1);
    }

    @Test
    public void batchFindUserCollections() {
        // given
        List<RequestInformation> requests = List.of(
                azureAdUserService.getByUserNameOrEmailRequestInformation("one.tester@example.com")
        );

        // when
        List<User> users = azureAdUserService.batchFindUserCollections(requests);

        // then
        assertThat(users).isNotNull();
        assertThat(users.size()).isEqualTo(1);
    }

    @Test
    public void batchFindUserCollectionsForEmployeeId() {
        // given
        List<RequestInformation> requests = List.of(
                azureAdUserService.getByUserEmployeeIdRequestInformation("UNKNOWN")
        );

        // when
        List<User> users = azureAdUserService.batchFindUserCollections(requests);

        // then
        assertThat(users).isNotNull();
        assertThat(users.size()).isEqualTo(0);
    }
}
