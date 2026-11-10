// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.job.JobRecordProcessingStats;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AzureSyncServiceTests {

    @InjectMocks
    private MemberSyncService service;

    @Mock
    private AzureAdUserService azureAdUserService;

    @Mock
    private MemberService memberService;

    @Mock
    private EntityManager entityManager;

    @BeforeEach
    public void init() {
        service.syncUsersManagerFetchBatchSize = 10;
        service.syncUsersManagerUpdateBatchSize = 10;
        service.syncUsersManagerAzureBatchSize = 10;
    }

    @Test
    public void syncUsersManager() {
        // given
        List<MemberView> members = List.of(
                MemberView.builder().id(1L).externalId("external-id1").managerExternalId("external-id2").build(),
                MemberView.builder().id(2L).externalId("external-id2").build(),
                MemberView.builder().id(3L).externalId("external-id3").build()
        );
        PaginatedMemberResponse paginatedResponse = mock(PaginatedMemberResponse.class);
        when(paginatedResponse.getTotalRecordCount()).thenReturn((long) members.size());
        when(paginatedResponse.getPageNumber()).thenReturn(1);
        when(paginatedResponse.getTotalPageCount()).thenReturn(1);
        when(paginatedResponse.getMembers()).thenReturn(members);
        when(memberService.getAllMembers(any(PaginationInput.class), any(Specification.class)))
                .thenReturn(paginatedResponse);

        Map<MemberView, Optional<User>> memberManagerMap = Map.of(
                members.get(0), Optional.of(buildUser("external-id2")),
                members.get(1), Optional.of(buildUser("external-id3")),
                members.get(2), Optional.empty()
        );
        when(azureAdUserService.batchFindUserManagers(any(List.class))).thenReturn(memberManagerMap);

        // when
        service.syncAzureUsersFetchBatchSize = 100;
        JobRecordProcessingStats stats = service.syncUsersManager();

        // then
        assertThat(stats).isNotNull();
        assertThat(stats.getRecordsProcessed()).isEqualTo(3);
        assertThat(stats.getRecordsUpdated()).isEqualTo(1);

        verify(memberService).updateMemberManager(members.get(1), "external-id3");
        verifyNoMoreInteractions(memberService);
        verify(entityManager).flush();
        verify(entityManager).clear();
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    public void syncUsersManagerByExistingManager() {
        // given
        String MEMBER_1 = "external-id1";
        String MEMBER_2 = "external-id2";
        String MEMBER_3 = "external-id3";

        // registry member state
        MemberView member1 = MemberView.builder().externalId(MEMBER_1).managerExternalId(MEMBER_2).build();
        MemberView member2 = MemberView.builder().externalId(MEMBER_2).managerExternalId(MEMBER_3).build();
        when(memberService.findAllByManagerExternalId(MEMBER_3)).thenReturn(List.of(member2));
        when(memberService.findAllByManagerExternalId(MEMBER_2)).thenReturn(List.of(member1));

        List<String> existingManagers = List.of(MEMBER_2, MEMBER_3);
        Page<String> existingManagersPage = new PageImpl<>(existingManagers);
        when(memberService.findAllManagerExternalIds(any(PaginationInput.class), anyString())).thenReturn(existingManagersPage);

        // azure direct report state, where member 2 is no longer a direct report of member 3
        Map<String, List<String>> managerDirectReportsMap = Map.of(
                MEMBER_3, new ArrayList<>(),
                MEMBER_2, List.of(MEMBER_1)
        );
        when(azureAdUserService.batchFindManagerDirectReports(any(List.class))).thenReturn(managerDirectReportsMap);

        // when
        service.syncAzureUsersFetchBatchSize = 100;
        JobRecordProcessingStats stats = service.syncUsersManagerByExistingManager();

        // then
        assertThat(stats).isNotNull();
        assertThat(stats.getRecordsProcessed()).isEqualTo(2);
        assertThat(stats.getRecordsUpdated()).isEqualTo(1);

        verify(memberService).updateMemberManager(member2, null);   // verify member2 manager is removed
        verifyNoMoreInteractions(memberService);
        verify(entityManager).flush();
        verify(entityManager).clear();
        verifyNoMoreInteractions(entityManager);
    }

    private User buildUser(String externalId) {
        User user = new User();
        user.setExternalId(externalId);
        return user;
    }
}
