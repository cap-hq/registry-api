// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.MemberView;
import com.cvshealth.cap.registry.model.PaginatedMemberResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.User;
import com.cvshealth.cap.registry.model.job.JobRecordProcessingStats;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberSyncServiceTests {

    @InjectMocks
    private MemberSyncService service;

    @Mock
    private AzureAdUserService azureAdUserService;

    @Mock
    private MemberService memberService;

    @Mock
    private MemberOffboardingService memberOffboardingService;

    @Mock
    private MemberProfileSyncService memberProfileSyncService;

    @Mock
    private EntityManager entityManager;

    @Test
    void testSyncAzureUsers() {
        User activeUser = new User();
        activeUser.setExternalId("external-id-2");
        activeUser.setExternalAlias("external-alias-2");
        activeUser.setDisplayName("Last2, First2");
        activeUser.setEmail("first2.last2@example.com");
        activeUser.setLastName("Last2");
        activeUser.setFirstName("First2");
        activeUser.setAccountEnabled(true);
        activeUser.setEmployeeId("employee-id2");

        User disabledUser = new User();
        disabledUser.setExternalId("external-id-3");
        disabledUser.setExternalAlias("external-alias-3");
        disabledUser.setDisplayName("Last3, First3");
        disabledUser.setEmail("first3.last3@example.com");
        disabledUser.setLastName("Last3");
        disabledUser.setFirstName("First3");
        disabledUser.setAccountEnabled(false);
        disabledUser.setEmployeeId("employee-id3");

        User noEmployeeIdUser = new User();
        noEmployeeIdUser.setExternalId("external-id-4");
        noEmployeeIdUser.setExternalAlias("external-alias-4");
        noEmployeeIdUser.setDisplayName("Last4, First4");
        noEmployeeIdUser.setEmail("first4.last4@example.com");
        noEmployeeIdUser.setLastName("Last4");
        noEmployeeIdUser.setFirstName("First4");
        noEmployeeIdUser.setAccountEnabled(true);
        noEmployeeIdUser.setEmployeeId(null);

        List<MemberView> records = List.of(
                MemberView.builder().id(1L).externalId("external-id-1").externalAlias("external-alias-1").build(),
                MemberView.builder().id(2L).externalId(activeUser.getExternalId()).externalAlias(activeUser.getExternalAlias()).build(),
                MemberView.builder().id(3L).externalId(disabledUser.getExternalId()).externalAlias(disabledUser.getExternalAlias()).build(),
                MemberView.builder().id(4L).externalId(noEmployeeIdUser.getExternalId()).externalAlias(noEmployeeIdUser.getExternalAlias()).build()
        );
        Map<MemberView, Optional<User>> memberUserMap = Map.of(
                records.get(0), Optional.empty(),
                records.get(1), Optional.of(activeUser),
                records.get(2), Optional.of(disabledUser),
                records.get(3), Optional.of(noEmployeeIdUser)
        );
        PaginatedMemberResponse response = PaginatedMemberResponse.builder()
                .members(records)
                .pageNumber(1)
                .pageSize(20)
                .totalPageCount(1)
                .totalRecordCount(4L)
                .build();
        when(memberService.getAllMembers(any(PaginationInput.class))).thenReturn(response);
        when(azureAdUserService.findMembersByIds(records)).thenReturn(memberUserMap);
        when(memberOffboardingService.removeMemberFromRegistry(any(MemberView.class), any())).thenReturn(true);

        service.syncAzureUsersFetchBatchSize = 20;
        JobRecordProcessingStats jobStats = service.syncAzureUsers(true);

        assertThat(jobStats.getRecordsProcessed()).isEqualTo(4);
        assertThat(jobStats.getRecordsDeleted()).isEqualTo(3);
        assertThat(jobStats.getRecordDeleteErrors()).isEqualTo(0);
        verify(memberProfileSyncService, times(1)).syncProfilesBatch(anyList(), any(JobRecordProcessingStats.class));
    }

    @Test
    void syncAzureUsers_whenOffboardingReturnsFalse_recordsDeleteErrorAndContinues() {
        MemberView first = MemberView.builder().id(1L).externalId("ext-1").build();
        MemberView second = MemberView.builder().id(2L).externalId("ext-2").build();
        List<MemberView> records = List.of(first, second);

        Map<MemberView, Optional<User>> memberUserMap = Map.of(
                first, Optional.empty(),
                second, Optional.empty()
        );
        PaginatedMemberResponse response = PaginatedMemberResponse.builder()
                .members(records)
                .pageNumber(1)
                .pageSize(20)
                .totalPageCount(1)
                .totalRecordCount(2L)
                .build();

        when(memberService.getAllMembers(any(PaginationInput.class))).thenReturn(response);
        when(azureAdUserService.findMembersByIds(records)).thenReturn(memberUserMap);
        when(memberOffboardingService.removeMemberFromRegistry(any(MemberView.class), any())).thenReturn(false);

        service.syncAzureUsersFetchBatchSize = 20;
        JobRecordProcessingStats jobStats = service.syncAzureUsers(true);

        assertThat(jobStats.getRecordsProcessed()).isEqualTo(2);
        assertThat(jobStats.getRecordsDeleted()).isEqualTo(0);
        assertThat(jobStats.getRecordDeleteErrors()).isEqualTo(2);
        verify(memberOffboardingService, times(2)).removeMemberFromRegistry(any(MemberView.class), any());
    }
}
