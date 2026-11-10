// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.mapper.ManagerRollupMapperDecorator;
import com.cvshealth.cap.registry.rollup.mapper.MapperDecoratorProvider;
import com.cvshealth.cap.registry.rollup.mapper.MemberMapper;
import com.cvshealth.cap.registry.rollup.mapper.UserMapper;
import com.cvshealth.cap.registry.rollup.model.MemberRollupView;
import com.cvshealth.cap.registry.rollup.model.MemberSearchFilter;
import com.cvshealth.cap.registry.rollup.model.MemberView;
import com.cvshealth.cap.registry.rollup.model.PaginatedMemberRollupResponse;
import com.cvshealth.cap.registry.rollup.model.PaginatedMemberView;
import com.cvshealth.cap.registry.rollup.model.PaginationInput;
import com.cvshealth.cap.registry.rollup.model.User;
import com.cvshealth.cap.registry.rollup.model.UserSearchFilter;
import com.cvshealth.cap.registry.rollup.model.UserSearchType;
import com.cvshealth.cap.registry.rollup.model.UserView;
import com.cvshealth.cap.registry.rollup.model.exception.BadRequestException;
import com.microsoft.kiota.RequestInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberRollupServiceTests {
    @InjectMocks
    private MemberRollupService memberRollupService;
    @Mock
    private MemberService memberService;
    @Mock
    private RollupService rollupService;
    @Mock
    private AzureAdUserService azureAdUserService;
    @Mock
    ManagerRollupMapperDecorator managerRollupMapperDecorator;
    @Spy
    private List<MapperDecoratorProvider> mapperDecoratorProviders = new ArrayList<>();

    @Spy
    private MemberMapper mapper = Mappers.getMapper(MemberMapper.class);
    @Spy
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Mock
    private RequestInformation requestInformation;
    @Mock
    private PaginationInput paginationInput;

    @BeforeEach
    public void setUp() {
        mapperDecoratorProviders.clear();
        mapperDecoratorProviders.add(managerRollupMapperDecorator);
    }

    @Test
    public void getMemberRollup_defaultSearch_usesRegistryMembersOnly() {
        // given
        MemberView member1 = MemberView.builder()
                .externalId("1234")
                .name("Member1, User1")
                .email("user1.member1@example.com")
                .build();
        when(memberService.getMembers(any(), any())).thenReturn(PaginatedMemberView.builder()
                .members(List.of(member1))
                .pageNumber(1)
                .pageSize(100)
                .totalRecordCount(1L)
                .totalPageCount(1)
                .build());
        when(rollupService.getMemberManagers(member1)).thenReturn(List.of(UserView.builder()
                .displayName("Bob")
                .title("Super Manager")
                .email("bob@no-reply.com")
                .externalAlias("C12345")
                .build()));
        MemberSearchFilter filter = MemberSearchFilter.builder().build();
        when(managerRollupMapperDecorator.supports(MemberRollupView.class)).thenReturn(true);

        // when
        PaginatedMemberRollupResponse response = memberRollupService.getMemberRollup(paginationInput, filter);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(100);
        assertThat(response.getTotalRecordCount()).isEqualTo(1);
        assertThat(response.getTotalPageCount()).isEqualTo(1);
        List<MemberRollupView> memberRollups = response.getMembers();
        assertThat(memberRollups).hasSize(1);
        MemberRollupView memberRollupView = memberRollups.get(0);
        assertThat(memberRollupView.getExternalId()).isEqualTo("1234");
        assertThat(memberRollupView.isAppRegistryMember()).isTrue();
        assertThat(memberRollupView.getManagers()).hasSize(1);
        assertThat(memberRollupView.getManagers().get(0).getExternalAlias()).isEqualTo("C12345");

        verifyNoInteractions(paginationInput);
        verifyNoInteractions(azureAdUserService);
        verifyNoMoreInteractions(rollupService);
        verifyNoMoreInteractions(memberService);
        verify(managerRollupMapperDecorator).map(any(MemberRollupView.class));
    }

    @Test
    public void getMemberRollup_forRegistryMembersOnly() {
        // given
        MemberView member1 = MemberView.builder()
                .externalId("1234")
                .name("Member1, User1")
                .email("user1.member1@example.com")
                .build();
        when(memberService.getMembers(any(), any())).thenReturn(PaginatedMemberView.builder()
                .members(List.of(member1))
                .pageNumber(1)
                .pageSize(100)
                .totalRecordCount(1L)
                .totalPageCount(1)
                .build());
        when(rollupService.getMemberManagers(member1)).thenReturn(List.of(UserView.builder()
                .displayName("Bob")
                .title("Super Manager")
                .email("bob@no-reply.com")
                .externalAlias("C12345")
                .build()));
        MemberSearchFilter filter = MemberSearchFilter.builder().appRegistryMembersOnly(true).build();

        // when
        PaginatedMemberRollupResponse response = memberRollupService.getMemberRollup(paginationInput, filter);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(100);
        assertThat(response.getTotalRecordCount()).isEqualTo(1);
        assertThat(response.getTotalPageCount()).isEqualTo(1);
        List<MemberRollupView> memberRollups = response.getMembers();
        assertThat(memberRollups).hasSize(1);
        MemberRollupView memberRollupView = memberRollups.get(0);
        assertThat(memberRollupView.getExternalId()).isEqualTo("1234");
        assertThat(memberRollupView.isAppRegistryMember()).isTrue();
        assertThat(memberRollupView.getManagers()).hasSize(1);
        assertThat(memberRollupView.getManagers().get(0).getExternalAlias()).isEqualTo("C12345");

        verifyNoInteractions(paginationInput);
        verifyNoInteractions(azureAdUserService);
        verifyNoMoreInteractions(rollupService);
        verifyNoMoreInteractions(memberService);
    }

    @Test
    public void getMemberRollup_userSearch() {
        // given
        MemberView member1 = MemberView.builder()
                .externalId("1234")
                .name("Member1, User1")
                .email("user1.member1@example.com")
                .build();
        when(memberService.getMembers(any(), any())).thenReturn(PaginatedMemberView.builder()
                .members(Collections.emptyList())
                .pageNumber(1)
                .pageSize(100)
                .totalRecordCount(0L)
                .totalPageCount(1)
                .build());
        when(rollupService.getMemberManagers(any(MemberView.class))).thenReturn(List.of(UserView.builder()
                .displayName("Bob")
                .title("Super Manager")
                .email("bob@no-reply.com")
                .externalId("externalId1")
                .externalAlias("C12345")
                .build()));
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .email("user1.member1@example.com")
                .appRegistryMembersOnly(false).build();
        User user = new User();
        user.setExternalId(member1.getExternalId());
        user.setEmail(member1.getEmail());
        user.setDisplayName(member1.getName());
        List<User> users = List.of(user);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);
        when(azureAdUserService.getBatchSearchLimit()).thenReturn(25);
        when(azureAdUserService.getByUserNameOrEmailRequestInformation(filter.getEmail())).thenReturn(requestInformation);
        when(azureAdUserService.batchFindUserCollections(any(List.class))).thenReturn(users);

        // when
        PaginatedMemberRollupResponse response = memberRollupService.getMemberRollup(PaginationInput.builder().pageSize(25).pageNumber(1).build(), filter);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(25);
        assertThat(response.getTotalRecordCount()).isEqualTo(1);
        assertThat(response.getTotalPageCount()).isEqualTo(1);
        List<MemberRollupView> memberRollups = response.getMembers();
        assertThat(memberRollups).hasSize(1);
        MemberRollupView memberRollupView = memberRollups.get(0);
        assertThat(memberRollupView.getExternalId()).isEqualTo("1234");
        assertThat(memberRollupView.isAppRegistryMember()).isFalse();
        assertThat(memberRollupView.getManagers()).hasSize(1);
        assertThat(memberRollupView.getManagers().get(0).getExternalAlias()).isEqualTo("C12345");

        verifyNoMoreInteractions(azureAdUserService);
        verifyNoMoreInteractions(rollupService);
        verifyNoMoreInteractions(memberService);
    }

    @Test
    public void buildUserSearchFilter_filterIsRequired() {
        // given
        MemberSearchFilter filter = null;

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Filter is required");
    }

    @Test
    public void buildUserSearchFilter_filterValueIsRequired() {
        // given
        MemberSearchFilter filter = MemberSearchFilter.builder().build();

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Filter is required");
    }

    @Test
    public void buildUserSearchFilter_throwError_whenPageNumberIsOverLimit() {
        // given
        PaginationInput paginationInput = PaginationInput.builder().pageSize(25).pageNumber(2).build();
        MemberSearchFilter filter = mock(MemberSearchFilter.class);

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        // then
        assertThat(thrown).isNotNull();
        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Page limit is 1 for non Registry members only rollups");

        verifyNoInteractions(filter);
    }

    @Test
    public void buildUserSearchFilter_throwError_whenOverRequestLimit() {
        // given
        PaginationInput paginationInput = PaginationInput.builder().pageSize(10).pageNumber(1).build();
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .emails(List.of(
                        "user1@example.com", "user2@example.com",
                        "user3@example.com", "user4@example.com",
                        "user5@example.com", "user6@example.com",
                        "user7@example.com", "user8@example.com",
                        "user9@example.com", "user10@example.com",
                        "user11@example.com"
                ))
                .appRegistryMembersOnly(false).build();
        when(azureAdUserService.getSearchLimit()).thenReturn(100);
        when(azureAdUserService.getBatchSearchLimit()).thenReturn(10);

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Filter search is limited to 10 requests for non Registry members only rollups");

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_throwError_whenPageSizeIsOverLimit() {
        // given
        PaginationInput paginationInput = PaginationInput.builder().pageSize(1000).pageNumber(1).build();
        MemberSearchFilter filter = mock(MemberSearchFilter.class);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Page size is limited to 100 for non Registry members only rollups");

        verifyNoInteractions(filter);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_filterValuesAreRequired() {
        // given
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .build();
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Filter is required");

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_externalId() {
        // given
        String externalId = "externalId";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .externalId(externalId)
                .build();
        when(azureAdUserService.getBatchSearchLimit()).thenReturn(25);
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        UserSearchFilter searchFilter = memberRollupService.buildUserSearchFilter(paginationInput, filter);

        assertThat(searchFilter).isNotNull();
        assertThat(searchFilter.getSearchType()).isEqualTo(UserSearchType.EXTERNAL_ID);
        assertThat(searchFilter.getSearchValues()).hasSize(1);

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_externalId() {
        // given
        String externalId = "externalId";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.EXTERNAL_ID)
                .searchValues(List.of(externalId))
                .build();
        when(azureAdUserService.getByUserIdRequestInformation(externalId)).thenReturn(requestInformation);
        List<User> users = List.of(createUser(externalId, null, null, null, null, null, null));
        when(azureAdUserService.batchFindUsers(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(1);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_externalIds() {
        // given
        String externalId1 = "externalId1";
        String externalId2 = "externalId2";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .externalIds(List.of(externalId1, externalId2))
                .build();
        when(azureAdUserService.getBatchSearchLimit()).thenReturn(25);
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        UserSearchFilter searchFilter = memberRollupService.buildUserSearchFilter(paginationInput, filter);

        assertThat(searchFilter).isNotNull();
        assertThat(searchFilter.getSearchType()).isEqualTo(UserSearchType.EXTERNAL_ID);
        assertThat(searchFilter.getSearchValues()).hasSize(2);

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_externalIds() {
        // given
        String externalId1 = "externalId1";
        String externalId2 = "externalId2";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.EXTERNAL_ID)
                .searchValues(List.of(externalId1, externalId2))
                .build();
        when(azureAdUserService.getByUserIdRequestInformation(externalId1)).thenReturn(requestInformation);
        when(azureAdUserService.getByUserIdRequestInformation(externalId2)).thenReturn(requestInformation);
        List<User> users = List.of(
                createUser(externalId1, null, null, null, null, null, null),
                createUser(externalId2, null, null, null, null, null, null)
        );
        when(azureAdUserService.batchFindUsers(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(2);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_externalAlias() {
        // given
        String externalAlias = "C123456";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .externalAlias(externalAlias)
                .build();
        when(azureAdUserService.getBatchSearchLimit()).thenReturn(25);
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        UserSearchFilter searchFilter = memberRollupService.buildUserSearchFilter(paginationInput, filter);

        assertThat(searchFilter).isNotNull();
        assertThat(searchFilter.getSearchType()).isEqualTo(UserSearchType.EXTERNAL_ALIAS);
        assertThat(searchFilter.getSearchValues()).hasSize(1);

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_externalAlias() {
        // given
        String externalAlias = "C123456";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.EXTERNAL_ALIAS)
                .searchValues(List.of(externalAlias))
                .build();
        when(azureAdUserService.getByUserAliasRequestInformation(externalAlias)).thenReturn(requestInformation);
        List<User> users = List.of(createUser(null, externalAlias, null, null, null, null, null));
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(1);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_externalAliases() {
        // given
        String externalAlias1 = "C111111";
        String externalAlias2 = "C222222";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .externalAliases(List.of(externalAlias1, externalAlias2))
                .build();
        when(azureAdUserService.getBatchSearchLimit()).thenReturn(25);
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        UserSearchFilter searchFilter = memberRollupService.buildUserSearchFilter(paginationInput, filter);

        assertThat(searchFilter).isNotNull();
        assertThat(searchFilter.getSearchType()).isEqualTo(UserSearchType.EXTERNAL_ALIAS);
        assertThat(searchFilter.getSearchValues()).hasSize(2);

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_externalAliases() {
        // given
        String externalAlias1 = "C111111";
        String externalAlias2 = "C222222";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.EXTERNAL_ALIAS)
                .searchValues(List.of(externalAlias1, externalAlias2))
                .build();
        when(azureAdUserService.getByUserAliasRequestInformation(externalAlias1)).thenReturn(requestInformation);
        when(azureAdUserService.getByUserAliasRequestInformation(externalAlias2)).thenReturn(requestInformation);
        List<User> users = List.of(
                createUser(null, externalAlias1, null, null, null, null, null),
                createUser(null, externalAlias2, null, null, null, null, null)
        );
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(2);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_email() {
        // given
        String email = "user@example.com";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .email(email)
                .build();
        when(azureAdUserService.getBatchSearchLimit()).thenReturn(25);
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        UserSearchFilter searchFilter = memberRollupService.buildUserSearchFilter(paginationInput, filter);

        assertThat(searchFilter).isNotNull();
        assertThat(searchFilter.getSearchType()).isEqualTo(UserSearchType.NAME_OR_EMAIL);
        assertThat(searchFilter.getSearchValues()).hasSize(1);

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_email() {
        // given
        String email = "user@example.com";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.NAME_OR_EMAIL)
                .searchValues(List.of(email))
                .build();
        when(azureAdUserService.getByUserNameOrEmailRequestInformation(email)).thenReturn(requestInformation);
        List<User> users = List.of(createUser(null, null, email, null, null, null, null));
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(1);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_emails() {
        // given
        String email1 = "user1@example.com";
        String email2 = "user2@example.com";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .emails(List.of(email1, email2))
                .build();
        when(azureAdUserService.getBatchSearchLimit()).thenReturn(25);
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        UserSearchFilter searchFilter = memberRollupService.buildUserSearchFilter(paginationInput, filter);

        assertThat(searchFilter).isNotNull();
        assertThat(searchFilter.getSearchType()).isEqualTo(UserSearchType.NAME_OR_EMAIL);
        assertThat(searchFilter.getSearchValues()).hasSize(2);

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_emails() {
        // given
        String email1 = "user1@example.com";
        String email2 = "user2@example.com";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.NAME_OR_EMAIL)
                .searchValues(List.of(email1, email2))
                .build();
        when(azureAdUserService.getByUserNameOrEmailRequestInformation(email1)).thenReturn(requestInformation);
        when(azureAdUserService.getByUserNameOrEmailRequestInformation(email2)).thenReturn(requestInformation);
        List<User> users = List.of(
                createUser(null, null, email1, null, null, null, null),
                createUser(null, null, email2, null, null, null, null)
        );
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(2);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_lastName() {
        // given
        String lastName = "Lastname";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .lastName(lastName)
                .build();
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Filter search type 'lastName' is not supported for non Registry members only rollups");

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_lastName() {
        // given
        String lastName = "Lastname";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.NAME_OR_EMAIL)
                .searchValues(List.of(lastName))
                .build();
        when(azureAdUserService.getByUserNameOrEmailRequestInformation(lastName)).thenReturn(requestInformation);
        List<User> users = List.of(createUser(null, null, null, lastName, null, null, null));
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(1);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_lastNames() {
        // given
        String lastName1 = "Lastname1";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .lastNames(List.of(lastName1))
                .build();
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Filter search type 'lastNames' is not supported for non Registry members only rollups");

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_lastNames() {
        // given
        String lastName1 = "Lastname1";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.NAME_OR_EMAIL)
                .searchValues(List.of(lastName1))
                .build();
        when(azureAdUserService.getByUserNameOrEmailRequestInformation(lastName1)).thenReturn(requestInformation);
        List<User> users = List.of(
                createUser(null, null, null, lastName1, null, null, null)
        );
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(1);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_name() {
        // given
        String name = "Lastname, Firstname";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .name(name)
                .build();
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        UserSearchFilter searchFilter = memberRollupService.buildUserSearchFilter(paginationInput, filter);

        assertThat(searchFilter).isNotNull();
        assertThat(searchFilter.getSearchType()).isEqualTo(UserSearchType.NAME_OR_EMAIL);
        assertThat(searchFilter.getSearchValues()).hasSize(1);

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_name() {
        // given
        String name = "Lastname, Firstname";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.NAME_OR_EMAIL)
                .searchValues(List.of(name))
                .build();
        when(azureAdUserService.getByUserNameOrEmailRequestInformation(name)).thenReturn(requestInformation);
        List<User> users = List.of(createUser(null, null, null, null, null, name, null));
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(1);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_employeeId() {
        // given
        String employeeId = "01234567";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.EMPLOYEE_ID)
                .searchValues(List.of(employeeId))
                .build();
        when(azureAdUserService.getByUserEmployeeIdRequestInformation(employeeId)).thenReturn(requestInformation);
        List<User> users = List.of(createUser(null, null, null, null, null, null, employeeId));
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(1);

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_names() {
        // given
        String name1 = "Lastname1, Firstname1";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .names(List.of(name1))
                .build();
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        UserSearchFilter searchFilter = memberRollupService.buildUserSearchFilter(paginationInput, filter);

        assertThat(searchFilter).isNotNull();
        assertThat(searchFilter.getSearchType()).isEqualTo(UserSearchType.NAME_OR_EMAIL);
        assertThat(searchFilter.getSearchValues()).hasSize(1);

        verifyNoMoreInteractions(paginationInput);
        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void buildUserSearchFilter_names_throwsErrorWhenOverSearchLimit() {
        // given
        String name1 = "Lastname1, Firstname1";
        String name2 = "Lastname1, Firstname2";
        MemberSearchFilter filter = MemberSearchFilter.builder()
                .names(List.of(name1, name2))
                .build();
        when(paginationInput.getPageNumber()).thenReturn(1);
        when(paginationInput.getPageSize()).thenReturn(100);
        when(azureAdUserService.getSearchLimit()).thenReturn(100);

        // when
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> memberRollupService.buildUserSearchFilter(paginationInput, filter));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo("Filter search is limited to 1 requests for non Registry members only rollups");

        verifyNoMoreInteractions(azureAdUserService);
    }

    @Test
    public void getMembersFromUserSearch_names() {
        // given
        String name1 = "Lastname1, Firstname1";
        UserSearchFilter searchFilter = UserSearchFilter.builder()
                .searchType(UserSearchType.NAME_OR_EMAIL)
                .searchValues(List.of(name1))
                .build();
        when(azureAdUserService.getByUserNameOrEmailRequestInformation(name1)).thenReturn(requestInformation);
        List<User> users = List.of(
                createUser(null, null, null, null, null, name1, null)
        );
        when(azureAdUserService.batchFindUserCollections(anyList())).thenReturn(users);

        // when
        List<MemberView> members = memberRollupService.getMembersFromUserSearch(searchFilter);

        assertThat(members).isNotNull();
        assertThat(members).hasSize(1);

        verifyNoMoreInteractions(azureAdUserService);
    }

    private User createUser(String externalId, String externalAlias, String email, String lastName, String firstName, String displayName, String employeeId) {
        User user = new User();
        user.setExternalId(externalId);
        user.setExternalAlias(externalAlias);
        user.setEmail(email);
        user.setLastName(lastName);
        user.setFirstName(firstName);
        user.setDisplayName(displayName);
        user.setEmployeeId(employeeId);
        return user;
    }
}
