// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.Group;
import com.cvshealth.cap.registry.model.MemberView;
import com.cvshealth.cap.registry.model.User;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AzureAdUserServiceIntegrationTests extends RegistryIntegrationTests {
    @Autowired
    private AzureAdUserService azureAdUserService;

    private static final String ID = "00000000-0000-0000-0000-000000000001";
    private static final String ID_WITH_MANAGER = "00000000-0000-0000-0000-000000000003";
    private static final String UNKNOWN_ID = "11a111aa-aa11-1a11-a111-1aa1aa111aaa";
    private static final String LOGIN_ID = "onetester";
    private static final String MANAGER_ID = "00000000-0000-0000-0000-000000000004";

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
        assertThat(user.getExternalAliasAlternate()).isEqualTo(LOGIN_ID);
    }

    @Test
    public void findByIdNotFound() {
        assertThat(azureAdUserService.findById(UNKNOWN_ID).isEmpty()).isTrue();
    }

    @Test
    public void findAllByName() {
        var users = azureAdUserService.findAllByNameOrEmail("Tester, One");
        assertThat(users).isNotEmpty();
        assertThat(users.size()).isLessThanOrEqualTo(10);
        assertThat(users.stream().anyMatch(user -> ID.equals(user.getExternalId()))).isTrue();
    }

    @Test
    public void findAllByEmail() {
        var users = azureAdUserService.findAllByNameOrEmail("one.tester@example.com");
        assertThat(users).isNotEmpty();
        assertThat(users.size()).isLessThanOrEqualTo(10);
        assertThat(users.stream().anyMatch(user -> ID.equals(user.getExternalId()))).isTrue();
    }

    @Test
    public void findAllByNameOrEmailEmptyString() {
        ApiException ex = assertThrows(ApiException.class, () -> azureAdUserService.findAllByNameOrEmail(""));
        assertThat(ex.getMessage()).contains(ExceptionReason.USER_SEARCH_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.USER_SEARCH_REQUIRED);
    }

    @Test
    public void findAllByNameOrEmailMinLength() {
        ApiException ex = assertThrows(ApiException.class, () -> azureAdUserService.findAllByNameOrEmail("te"));
        assertThat(ex.getMessage()).contains(String.format(ExceptionReason.USER_SEARCH_INVALID_LENGTH.label, AzureAdUserServiceValidator.SEARCH_MIN_LENGTH));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.USER_SEARCH_INVALID_LENGTH);
    }

    @Test
    public void findAllByNameOrEmailNoResults() {
        assertThat(azureAdUserService.findAllByNameOrEmail("nosdnumA")).isEmpty();
    }

    @Test
    public void findAllByNameOrEmailNoResults_IncludingDisabledAccounts() {
        assertThat(azureAdUserService.findAllByNameOrEmail("nosdnumA", true)).isEmpty();
    }

    @Test
    public void findAllByName_NotMember() {
        var users = azureAdUserService.findAllByNameOrEmail("johnytac");
        assertThat(users).isEmpty();
    }

    @Test
    public void batchFindMembersByIds() throws IOException {
        List<MemberView> members = List.of(
                MemberView.builder().externalId(ID).build(),
                MemberView.builder().externalId(UNKNOWN_ID).build());
        Map<MemberView, Optional<User>> users = azureAdUserService.batchFindMembersByIds(members);
        assertThat(users).isNotEmpty();
        assertThat(users.size()).isEqualTo(2);

        Optional<User> user1 = users.get(members.get(0));
        assertThat(user1.isPresent()).isTrue();

        Optional<User> user2 = users.get(members.get(1));
        assertThat(user2.isPresent()).isFalse();
    }

    @Test
    public void findMembersByIds() {
        List<MemberView> members = List.of(
                MemberView.builder().externalId(ID).build(),
                MemberView.builder().externalId(UNKNOWN_ID).build());
        Map<MemberView, Optional<User>> users = azureAdUserService.findMembersByIds(members);
        assertThat(users).isNotEmpty();
        assertThat(users.size()).isEqualTo(2);

        Optional<User> user1 = users.get(members.get(0));
        assertThat(user1.isPresent()).isTrue();

        Optional<User> user2 = users.get(members.get(1));
        assertThat(user2.isPresent()).isFalse();
    }

    @Test
    public void findByIdWithManager() {
        Optional<User> user = azureAdUserService.findByIdWithManager(ID_WITH_MANAGER);
        assertThat(user.isPresent()).isTrue();
        assertThat(user.get().getExternalId()).isEqualTo(ID_WITH_MANAGER);
        assertThat(user.get().getManager()).isNotNull();
    }

    @Test
    public void findUserManager() {
        Optional<User> user = azureAdUserService.findUserManager(ID_WITH_MANAGER);
        assertThat(user.isPresent()).isTrue();
        assertThat(user.get().getExternalId()).isNotNull();
    }

    @Test
    public void findUserManager_notFound() {
        Optional<User> user = azureAdUserService.findUserManager(UNKNOWN_ID);
        assertThat(user.isEmpty()).isTrue();
    }

    @Test
    public void batchFindUserManagers() {
        // given
        List<MemberView> members = List.of(
                MemberView.builder().externalId(ID_WITH_MANAGER).build(),
                MemberView.builder().externalId(UNKNOWN_ID).build()
        );

        // when
        Map<MemberView, Optional<User>> result = azureAdUserService.batchFindUserManagers(members);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.size()).isEqualTo(2);
        Optional<User> user1 = result.get(members.get(0));
        assertThat(user1.isPresent()).isTrue();
        assertThat(user1.get().getExternalId()).isNotNull();
        Optional<User> user2 = result.get(members.get(1));
        assertThat(user2.isEmpty()).isTrue();
    }

    @Test
    public void findManagerDirectReports() {
        List<User> result = azureAdUserService.findManagerDirectReports(MANAGER_ID);
        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    public void findUserEmailGroups() {
        // given
        Optional<User> user = azureAdUserService.findById(ID);
        assertThat(user.isPresent()).isTrue();

        List<Group> groups = azureAdUserService.findUserEmailGroups(user.get().getEmail());
        assertThat(groups).isNotNull();
    }

}
