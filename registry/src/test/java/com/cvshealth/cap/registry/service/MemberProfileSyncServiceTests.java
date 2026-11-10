// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.MemberView;
import com.cvshealth.cap.registry.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberProfileSyncServiceTests {

    @InjectMocks
    private MemberProfileSyncService service;

    @Mock
    private MemberService memberService;
    @Mock
    private NewMemberService newMemberService;
    @Mock
    private EntityManager entityManager;

    private User baseUser() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setDisplayName("Jane Doe");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setEmployeeId("E123");
        return user;
    }

    private MemberView baseMember() {
        return MemberView.builder()
                .id(1L)
                .externalId("ext-1")
                .externalAlias("jdoe")
                .email("user@example.com")
                .name("Jane Doe")
                .firstName("Jane")
                .lastName("Doe")
                .employeeId("E123")
                .build();
    }

    @Test
    void noUpdateWhenAllFieldsMatch() throws Exception {
        service.syncProfilesBatch(List.of(Map.entry(baseMember(), baseUser())), new com.cvshealth.cap.registry.model.job.JobRecordProcessingStats());
        verify(memberService, never()).updateMember(any());
    }

    @Test
    void updatesWhenEmailDiffers() throws Exception {
        User user = baseUser();
        user.setEmail("other@example.com");
        service.syncProfilesBatch(List.of(Map.entry(baseMember(), user)), new com.cvshealth.cap.registry.model.job.JobRecordProcessingStats());
        verify(memberService).updateMember(any());
    }

    @Test
    void updatesWhenEmailDiffersInCase() throws Exception {
        User user = baseUser();
        user.setEmail("User@Example.com");
        service.syncProfilesBatch(List.of(Map.entry(baseMember(), user)), new com.cvshealth.cap.registry.model.job.JobRecordProcessingStats());
        verify(memberService).updateMember(any());
    }

    @Test
    void updatesWhenDisplayNameDiffersInCase() throws Exception {
        User user = baseUser();
        user.setDisplayName("JANE DOE");
        service.syncProfilesBatch(List.of(Map.entry(baseMember(), user)), new com.cvshealth.cap.registry.model.job.JobRecordProcessingStats());
        verify(memberService).updateMember(any());
    }

    @Test
    void updatesWhenFirstNameDiffersInCase() throws Exception {
        User user = baseUser();
        user.setFirstName("jane");
        service.syncProfilesBatch(List.of(Map.entry(baseMember(), user)), new com.cvshealth.cap.registry.model.job.JobRecordProcessingStats());
        verify(memberService).updateMember(any());
    }

    @Test
    void updatesWhenLastNameDiffersInCase() throws Exception {
        User user = baseUser();
        user.setLastName("doe");
        service.syncProfilesBatch(List.of(Map.entry(baseMember(), user)), new com.cvshealth.cap.registry.model.job.JobRecordProcessingStats());
        verify(memberService).updateMember(any());
    }

    @Test
    void updatesWhenEmployeeIdDiffersInCase() throws Exception {
        User user = baseUser();
        user.setEmployeeId("e123");
        service.syncProfilesBatch(List.of(Map.entry(baseMember(), user)), new com.cvshealth.cap.registry.model.job.JobRecordProcessingStats());
        verify(memberService).updateMember(any());
    }
}
