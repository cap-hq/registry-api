// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.model.job.JobRecordProcessingStats;
import com.cvshealth.cap.registry.persistent.*;
import com.cvshealth.cap.registry.repository.*;
import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;
import wiremock.com.fasterxml.jackson.databind.json.JsonMapper;
import wiremock.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MemberSyncServiceIntegrationTests extends RegistryIntegrationTests {

    @MockitoSpyBean
    private MemberSyncService service;

    @MockitoSpyBean
    private MemberOffboardingService memberOffboardingService;

    @MockitoSpyBean
    private MemberService memberService;

    @MockitoSpyBean
    private AzureAdUserService azureAdUserService;

    @MockitoSpyBean
    private NewMemberService newMemberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private AuthClientRepository authClientRepository;

    @Autowired
    private TeamAuthClientRepository teamAuthClientRepository;

    @Autowired
    private RollbackOnlyMarker rollbackOnlyMarker;

    protected static final String MEMBER1_EXTERNAL_ID = "00000000-0000-0000-0000-000000000001"; // "Tester, One" user id
    private static final AuthClientEntity PERSONAL_TEAM_AUTH_CLIENT_USER = AuthClientEntity.builder().clientId("auth-client-user").clientVersion(1).build();
    private static final AuthClientEntity PERSONAL_TEAM_AUTH_CLIENT_NONUSER = AuthClientEntity.builder().clientId("auth-client-non-user").clientVersion(1).build();
    private static final AuthClientEntity PERSONAL_TEAM_AUTH_CLIENT_NO_TYPE = AuthClientEntity.builder().clientId("auth-client-no-type").clientVersion(1).build();

    @Test
    public void deleteMemberWithPersonalTeamAuthClients() {
        // given
        String seedSuffix = uniqueName("seed");
        MemberEntity seededMember = memberRepository.save(MemberEntity.builder()
                .externalId("external-" + seedSuffix)
                .externalAlias("TEST456-" + seedSuffix)
                .name("Test, Registry")
                .email("registry.test." + seedSuffix + "@example.com")
                .build());
        String personalTeamName = uniqueName("Personal-C123456");
        TeamEntity seededPersonalTeam = teamRepository.save(TeamEntity.builder()
                .type(TeamType.PERSONAL)
                .name(personalTeamName)
                .nameNormalized(normalizeName(personalTeamName))
                .build());
        TeamMemberId personalTeamMemberId = TeamMemberId.builder().teamId(seededPersonalTeam.getId()).memberId(seededMember.getId()).build();
        teamMemberRepository.save(TeamMemberEntity.builder().role(TeamMemberRole.OWNER).id(personalTeamMemberId).build());

        WireMockServer authApiWiremock = new WireMockServer(options().port(8787));
        authApiWiremock.start();
        registerAuthApiMock(authApiWiremock);

        Member member = memberService.getMemberByExternalAlias(seededMember.getExternalAlias());
        assertThat(member).isNotNull();
        Optional<TeamEntity> personalTeam = teamRepository.findByName(seededPersonalTeam.getName());

        List<AuthClientEntity> authClients = List.of(PERSONAL_TEAM_AUTH_CLIENT_NO_TYPE,  PERSONAL_TEAM_AUTH_CLIENT_USER,  PERSONAL_TEAM_AUTH_CLIENT_NONUSER);
        if (personalTeam.isPresent()) {
            for (AuthClientEntity authClientEntity : authClients) {
                AuthClientEntity persistedAuthClientEntity = authClientRepository.save(authClientEntity);
                TeamAuthClientId teamAuthClientId = TeamAuthClientId.builder().authClientId(persistedAuthClientEntity.getId()).teamId(personalTeam.get().getId()).build();
                teamAuthClientRepository.save(TeamAuthClientEntity.builder().id(teamAuthClientId).build());
            }
        }

        try {
            // when
            memberOffboardingService.removeMemberFromRegistry(member.getId(), MEMBER1_EXTERNAL_ID);

            // then
            for (AuthClientEntity authClientEntity : authClients) {
                assertThat(authClientRepository.findAllByClientId(authClientEntity.getClientId())).isEmpty();
            }
            assertThrows(RecordNotFoundException.class, () -> memberService.getMemberByExternalAlias(seededMember.getExternalAlias()));
        } finally {
            authApiWiremock.stop();
        }
    }

    @Test
    public void syncAzureUsers_whenRemoveMemberFails_recordsDeleteErrorAndKeepsPriorMemberUpdate() {
        // given
        MemberEntity updateCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-update")
                .externalAlias("TEST321")
                .name("Original, Name")
                .email("original.name@example.com")
                .firstName("Original")
                .lastName("Name")
                .employeeId("1001")
                .build());

        MemberEntity removalCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-remove")
                .externalAlias("TEST322")
                .name("Delete, Candidate")
                .email("delete.candidate@example.com")
                .employeeId("1002")
                .build());

        String removalPersonalTeamName = uniqueName("Personal-C654322");
        TeamEntity removalPersonalTeam = teamRepository.save(TeamEntity.builder()
                .type(TeamType.PERSONAL)
                .name(removalPersonalTeamName)
                .nameNormalized(normalizeName(removalPersonalTeamName))
                .build());
        teamMemberRepository.save(TeamMemberEntity.builder()
                .role(TeamMemberRole.OWNER)
                .id(TeamMemberId.builder()
                        .teamId(removalPersonalTeam.getId())
                        .memberId(removalCandidate.getId())
                        .build())
                .build());

        MemberView updateCandidateView = memberService.getMemberView(updateCandidate.getId(), null);
        MemberView removalCandidateView = memberService.getMemberView(removalCandidate.getId(), null);
        PaginatedMemberResponse onePageResponse = PaginatedMemberResponse.builder()
                .members(List.of(updateCandidateView, removalCandidateView))
                .pageNumber(1)
                .pageSize(2)
                .totalPageCount(1)
                .totalRecordCount(2L)
                .build();

        User updatedUser = new User();
        updatedUser.setExternalId(updateCandidateView.getExternalId());
        updatedUser.setExternalAlias(updateCandidateView.getExternalAlias());
        updatedUser.setDisplayName("Updated, Name");
        updatedUser.setEmail("updated.name@example.com");
        updatedUser.setFirstName("Updated");
        updatedUser.setLastName("Name");
        updatedUser.setEmployeeId("1001");
        updatedUser.setAccountEnabled(true);

        Map<MemberView, Optional<User>> azureResults = new LinkedHashMap<>();
        azureResults.put(updateCandidateView, Optional.of(updatedUser));
        azureResults.put(removalCandidateView, Optional.empty());

        doReturn(onePageResponse).when(memberService).getAllMembers(any(PaginationInput.class));
        doReturn(azureResults).when(azureAdUserService).findMembersByIds(anyList());
        doReturn(true).when(newMemberService).hasPersonalTeam(any(MemberView.class));
        doThrow(new RuntimeException("intentional remove failure"))
                .when(memberOffboardingService)
                .removeMemberFromRegistry(any(MemberView.class), isNull());

        // when
        JobRecordProcessingStats jobStats = service.syncAzureUsers(true);

        // then
        assertThat(jobStats.getRecordsProcessed()).isEqualTo(2);
        assertThat(jobStats.getRecordsUpdated()).isEqualTo(1);
        assertThat(jobStats.getRecordDeleteErrors()).isEqualTo(1);
        assertThat(jobStats.getRecordsDeleted()).isZero();

        // Prior updates are committed in their own transaction even when offboarding fails later.
        MemberEntity reloadedUpdateCandidate = memberRepository.findById(updateCandidate.getId()).orElseThrow();
        assertThat(reloadedUpdateCandidate.getName()).isEqualTo("Updated, Name");
        assertThat(reloadedUpdateCandidate.getEmail()).isEqualTo("updated.name@example.com");
        assertThat(memberRepository.findById(removalCandidate.getId())).isPresent();

        verify(memberService, times(1)).getAllMembers(any(PaginationInput.class));
        verify(azureAdUserService, times(1)).findMembersByIds(anyList());
    }

    @Test
    public void syncAzureUsers_whenRemoveMemberFailsInSecondBatch_recordsDeleteErrorAndKeepsSuccessfulBatchUpdates() {
        // given
        MemberEntity firstBatchUpdateCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-batch-1")
                .externalAlias("TEST331")
                .name("First Batch, Original")
                .email("first.batch.original@example.com")
                .firstName("First")
                .lastName("Original")
                .employeeId("2001")
                .build());
        MemberEntity secondBatchUpdateCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-batch-2")
                .externalAlias("TEST332")
                .name("Second Batch, Original")
                .email("second.batch.original@example.com")
                .firstName("Second")
                .lastName("Original")
                .employeeId("2002")
                .build());
        MemberEntity secondBatchRemovalCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-batch-remove")
                .externalAlias("TEST333")
                .name("Second Batch, Remove")
                .email("second.batch.remove@example.com")
                .employeeId("2003")
                .build());

        MemberView firstBatchUpdateView = memberService.getMemberView(firstBatchUpdateCandidate.getId(), null);
        MemberView secondBatchUpdateView = memberService.getMemberView(secondBatchUpdateCandidate.getId(), null);
        MemberView secondBatchRemovalView = memberService.getMemberView(secondBatchRemovalCandidate.getId(), null);

        PaginatedMemberResponse firstPageResponse = PaginatedMemberResponse.builder()
                .members(List.of(firstBatchUpdateView))
                .pageNumber(1)
                .pageSize(1)
                .totalPageCount(2)
                .totalRecordCount(3L)
                .build();
        PaginatedMemberResponse secondPageResponse = PaginatedMemberResponse.builder()
                .members(List.of(secondBatchUpdateView, secondBatchRemovalView))
                .pageNumber(2)
                .pageSize(2)
                .totalPageCount(2)
                .totalRecordCount(3L)
                .build();

        User firstBatchUpdatedUser = new User();
        firstBatchUpdatedUser.setExternalId(firstBatchUpdateView.getExternalId());
        firstBatchUpdatedUser.setExternalAlias(firstBatchUpdateView.getExternalAlias());
        firstBatchUpdatedUser.setDisplayName("First Batch, Updated");
        firstBatchUpdatedUser.setEmail("first.batch.updated@example.com");
        firstBatchUpdatedUser.setFirstName("First");
        firstBatchUpdatedUser.setLastName("Updated");
        firstBatchUpdatedUser.setEmployeeId("2001");
        firstBatchUpdatedUser.setAccountEnabled(true);

        User secondBatchUpdatedUser = new User();
        secondBatchUpdatedUser.setExternalId(secondBatchUpdateView.getExternalId());
        secondBatchUpdatedUser.setExternalAlias(secondBatchUpdateView.getExternalAlias());
        secondBatchUpdatedUser.setDisplayName("Second Batch, Updated");
        secondBatchUpdatedUser.setEmail("second.batch.updated@example.com");
        secondBatchUpdatedUser.setFirstName("Second");
        secondBatchUpdatedUser.setLastName("Updated");
        secondBatchUpdatedUser.setEmployeeId("2002");
        secondBatchUpdatedUser.setAccountEnabled(true);

        Map<MemberView, Optional<User>> firstPageAzureResults = new LinkedHashMap<>();
        firstPageAzureResults.put(firstBatchUpdateView, Optional.of(firstBatchUpdatedUser));

        Map<MemberView, Optional<User>> secondPageAzureResults = new LinkedHashMap<>();
        secondPageAzureResults.put(secondBatchUpdateView, Optional.of(secondBatchUpdatedUser));
        secondPageAzureResults.put(secondBatchRemovalView, Optional.empty());

        doReturn(firstPageResponse, secondPageResponse).when(memberService).getAllMembers(any(PaginationInput.class));
        doReturn(firstPageAzureResults, secondPageAzureResults).when(azureAdUserService).findMembersByIds(anyList());
        doReturn(true).when(newMemberService).hasPersonalTeam(any(MemberView.class));
        doThrow(new RuntimeException("intentional second batch remove failure"))
                .when(memberOffboardingService)
                .removeMemberFromRegistry(any(MemberView.class), isNull());

        // when
        JobRecordProcessingStats jobStats = service.syncAzureUsers(true);

        // then
        assertThat(jobStats.getRecordsProcessed()).isEqualTo(3);
        assertThat(jobStats.getRecordsUpdated()).isEqualTo(2);
        assertThat(jobStats.getRecordDeleteErrors()).isEqualTo(1);
        assertThat(jobStats.getRecordsDeleted()).isZero();

        // Successful updates from prior processing stay committed.
        MemberEntity reloadedFirstBatchUpdateCandidate = memberRepository.findById(firstBatchUpdateCandidate.getId()).orElseThrow();
        assertThat(reloadedFirstBatchUpdateCandidate.getName()).isEqualTo("First Batch, Updated");
        assertThat(reloadedFirstBatchUpdateCandidate.getEmail()).isEqualTo("first.batch.updated@example.com");

        MemberEntity reloadedSecondBatchUpdateCandidate = memberRepository.findById(secondBatchUpdateCandidate.getId()).orElseThrow();
        assertThat(reloadedSecondBatchUpdateCandidate.getName()).isIn("Second Batch, Original", "Second Batch, Updated");
        assertThat(reloadedSecondBatchUpdateCandidate.getEmail()).isIn("second.batch.original@example.com", "second.batch.updated@example.com");
        assertThat(memberRepository.findById(secondBatchRemovalCandidate.getId())).isPresent();

        verify(memberService, times(2)).getAllMembers(any(PaginationInput.class));
        verify(azureAdUserService, times(2)).findMembersByIds(anyList());
    }

    @Test
    public void syncAzureUsers_whenFirstItemInNewBatchIsRemoveFailure_recordsDeleteErrorAndKeepsPreviousBatchUpdate() {
        // given
        MemberEntity firstBatchUpdateCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-boundary-1")
                .externalAlias("TEST341")
                .name("Boundary Batch, Original")
                .email("boundary.batch.original@example.com")
                .firstName("Boundary")
                .lastName("Original")
                .employeeId("3001")
                .build());
        MemberEntity secondBatchRemovalCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-boundary-remove")
                .externalAlias("TEST342")
                .name("Boundary Batch, Remove")
                .email("boundary.batch.remove@example.com")
                .employeeId("3002")
                .build());

        MemberView firstBatchUpdateView = memberService.getMemberView(firstBatchUpdateCandidate.getId(), null);
        MemberView secondBatchRemovalView = memberService.getMemberView(secondBatchRemovalCandidate.getId(), null);

        PaginatedMemberResponse firstPageResponse = PaginatedMemberResponse.builder()
                .members(List.of(firstBatchUpdateView))
                .pageNumber(1)
                .pageSize(1)
                .totalPageCount(2)
                .totalRecordCount(2L)
                .build();
        PaginatedMemberResponse secondPageResponse = PaginatedMemberResponse.builder()
                .members(List.of(secondBatchRemovalView))
                .pageNumber(2)
                .pageSize(1)
                .totalPageCount(2)
                .totalRecordCount(2L)
                .build();

        User firstBatchUpdatedUser = new User();
        firstBatchUpdatedUser.setExternalId(firstBatchUpdateView.getExternalId());
        firstBatchUpdatedUser.setExternalAlias(firstBatchUpdateView.getExternalAlias());
        firstBatchUpdatedUser.setDisplayName("Boundary Batch, Updated");
        firstBatchUpdatedUser.setEmail("boundary.batch.updated@example.com");
        firstBatchUpdatedUser.setFirstName("Boundary");
        firstBatchUpdatedUser.setLastName("Updated");
        firstBatchUpdatedUser.setEmployeeId("3001");
        firstBatchUpdatedUser.setAccountEnabled(true);

        Map<MemberView, Optional<User>> firstPageAzureResults = new LinkedHashMap<>();
        firstPageAzureResults.put(firstBatchUpdateView, Optional.of(firstBatchUpdatedUser));

        Map<MemberView, Optional<User>> secondPageAzureResults = new LinkedHashMap<>();
        secondPageAzureResults.put(secondBatchRemovalView, Optional.empty());

        doReturn(firstPageResponse, secondPageResponse).when(memberService).getAllMembers(any(PaginationInput.class));
        doReturn(firstPageAzureResults, secondPageAzureResults).when(azureAdUserService).findMembersByIds(anyList());
        doReturn(true).when(newMemberService).hasPersonalTeam(any(MemberView.class));
        doThrow(new RuntimeException("intentional boundary remove failure"))
                .when(memberOffboardingService)
                .removeMemberFromRegistry(any(MemberView.class), isNull());

        // when
        JobRecordProcessingStats jobStats = service.syncAzureUsers(true);

        // then
        assertThat(jobStats.getRecordsProcessed()).isEqualTo(2);
        assertThat(jobStats.getRecordsUpdated()).isEqualTo(1);
        assertThat(jobStats.getRecordDeleteErrors()).isEqualTo(1);
        assertThat(jobStats.getRecordsDeleted()).isZero();

        // First-batch update remains committed even when next batch fails immediately.
        MemberEntity reloadedFirstBatchUpdateCandidate = memberRepository.findById(firstBatchUpdateCandidate.getId()).orElseThrow();
        assertThat(reloadedFirstBatchUpdateCandidate.getName()).isEqualTo("Boundary Batch, Updated");
        assertThat(reloadedFirstBatchUpdateCandidate.getEmail()).isEqualTo("boundary.batch.updated@example.com");
        assertThat(memberRepository.findById(secondBatchRemovalCandidate.getId())).isPresent();

        verify(memberService, times(2)).getAllMembers(any(PaginationInput.class));
        verify(azureAdUserService, times(2)).findMembersByIds(anyList());
    }

    @Test
    public void syncAzureUsers_whenRemoveMarksRollbackOnly_recordsDeleteErrorAndContinues() {
        // Offboarding rollback-only markers are treated as member-level failures:
        // syncAzureUsers catches the offboarding exception path and records a delete error,
        // then continues and completes the batch.
        // given
        MemberEntity updateCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-rollback-1")
                .externalAlias("TEST351")
                .name("Rollback, Original")
                .email("rollback.original@example.com")
                .firstName("Rollback")
                .lastName("Original")
                .employeeId("4001")
                .build());
        MemberEntity removalCandidate = memberRepository.save(MemberEntity.builder()
                .externalId("external-rollback-remove")
                .externalAlias("TEST352")
                .name("Rollback, Remove")
                .email("rollback.remove@example.com")
                .employeeId("4002")
                .build());

        MemberView updateCandidateView = memberService.getMemberView(updateCandidate.getId(), null);
        MemberView removalCandidateView = memberService.getMemberView(removalCandidate.getId(), null);
        PaginatedMemberResponse onePageResponse = PaginatedMemberResponse.builder()
                .members(List.of(updateCandidateView, removalCandidateView))
                .pageNumber(1)
                .pageSize(2)
                .totalPageCount(1)
                .totalRecordCount(2L)
                .build();

        User updatedUser = new User();
        updatedUser.setExternalId(updateCandidateView.getExternalId());
        updatedUser.setExternalAlias(updateCandidateView.getExternalAlias());
        updatedUser.setDisplayName("Rollback, Updated");
        updatedUser.setEmail("rollback.updated@example.com");
        updatedUser.setFirstName("Rollback");
        updatedUser.setLastName("Updated");
        updatedUser.setEmployeeId("4001");
        updatedUser.setAccountEnabled(true);

        Map<MemberView, Optional<User>> azureResults = new LinkedHashMap<>();
        azureResults.put(updateCandidateView, Optional.of(updatedUser));
        azureResults.put(removalCandidateView, Optional.empty());

        doReturn(onePageResponse).when(memberService).getAllMembers(any(PaginationInput.class));
        doReturn(azureResults).when(azureAdUserService).findMembersByIds(anyList());
        doReturn(true).when(newMemberService).hasPersonalTeam(any(MemberView.class));
        doAnswer(invocation -> {
            rollbackOnlyMarker.markRollbackOnly();
            return false;
        }).when(memberOffboardingService).removeMemberFromRegistry(any(MemberView.class), isNull());

        // when
        JobRecordProcessingStats jobStats = service.syncAzureUsers(true);

        // then
        assertThat(jobStats.getRecordsProcessed()).isEqualTo(2);
        assertThat(jobStats.getRecordsUpdated()).isEqualTo(1);
        assertThat(jobStats.getRecordDeleteErrors()).isEqualTo(1);
        assertThat(jobStats.getRecordsDeleted()).isZero();
        verify(memberService, times(1)).getAllMembers(any(PaginationInput.class));
        verify(azureAdUserService, times(1)).findMembersByIds(anyList());
    }

    @Test
    public void removeMemberFromRegistry_whenManagerIsDisabled_doesNotRollbackAndDeletesMember() {
        // given
        String managerExternalId = "external-disabled-manager";
        MemberEntity memberToRemove = memberRepository.save(MemberEntity.builder()
                .externalId("external-direct-remove")
                .externalAlias("TEST362")
                .name("Direct, Remove")
                .email("direct.remove@example.com")
                .employeeId("5002")
                .managerExternalId(managerExternalId)
                .build());

        User disabledManager = new User();
        disabledManager.setExternalId(managerExternalId);
        disabledManager.setExternalAlias("TEST001");
        disabledManager.setDisplayName("Disabled, Manager");
        disabledManager.setEmail("disabled.manager@example.com");
        disabledManager.setFirstName("Disabled");
        disabledManager.setLastName("Manager");
        disabledManager.setEmployeeId("9002");
        disabledManager.setAccountEnabled(false);
        doReturn(Optional.of(disabledManager)).when(azureAdUserService).findByIdWithManager(managerExternalId);

        // when / then
        boolean deleted = org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> memberOffboardingService.removeMemberFromRegistry(memberToRemove.getId(), null));
        assertThat(deleted).isTrue();
        assertThat(memberRepository.findById(memberToRemove.getId())).isEmpty();
    }

    @TestConfiguration
    static class RollbackOnlyMarkerConfig {
        @Bean
        RollbackOnlyMarker rollbackOnlyMarker() {
            return new RollbackOnlyMarker();
        }
    }

    static class RollbackOnlyMarker {
        @Transactional
        public void markRollbackOnly() {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    private void registerAuthApiMock(WireMockServer wiremock) {
        wiremock.stubFor(get("/auth/v1/client/" + PERSONAL_TEAM_AUTH_CLIENT_USER.getClientId())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockClient(buildUserOAuthClient(PERSONAL_TEAM_AUTH_CLIENT_USER.getClientId())))));

        wiremock.stubFor(get("/auth/v1/client/" + PERSONAL_TEAM_AUTH_CLIENT_NONUSER.getClientId())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockClient(buildNonUserOAuthClient(PERSONAL_TEAM_AUTH_CLIENT_NONUSER.getClientId())))));

        wiremock.stubFor(get("/auth/v1/client/" + PERSONAL_TEAM_AUTH_CLIENT_NO_TYPE.getClientId())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withJsonBody(mockClient(buildNoTypeOAuthClient(PERSONAL_TEAM_AUTH_CLIENT_NO_TYPE.getClientId())))));

    }

    private OAuthClient buildUserOAuthClient(String clientId) {
        return OAuthClient.builder()
                .id(clientId)
                .clientId(clientId)
                .clientName(clientId)
                .clientSecret("*****")
                .type(AuthClientType.USER)
                .redirectUrls(List.of("http://localhost:8080/callback"))
                .accessTokenExpireMinutes(5L)
                .build();
    }

    private OAuthClient buildNonUserOAuthClient(String clientId) {
        return OAuthClient.builder()
                .id(clientId)
                .clientId(clientId)
                .clientName(clientId)
                .clientSecret("*****")
                .type(AuthClientType.NON_USER)
                .accessTokenExpireMinutes(5L)
                .build();
    }

    private OAuthClient buildNoTypeOAuthClient(String clientId) {
        return OAuthClient.builder()
                .id(clientId)
                .clientId(clientId)
                .clientName(clientId)
                .clientSecret("*****")
                .accessTokenExpireMinutes(5L)
                .build();
    }

    private JsonNode mockClient(OAuthClient oauthClient) {
        ObjectMapper mapper = createObjectMapper();
        return mapper.convertValue(oauthClient, JsonNode.class);
    }

    private ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }
}
