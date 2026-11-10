// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.model.MemberView;
import com.cvshealth.cap.registry.model.User;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.job.JobRecordProcessingStats;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberProfileSyncService {

    private final MemberService memberService;
    private final NewMemberService newMemberService;
    private final EntityManager entityManager;

    @Transactional(value = REQUIRES_NEW)
    public void syncProfilesBatch(List<Map.Entry<MemberView, User>> members, JobRecordProcessingStats batchJobStats) {
        members.forEach(entry -> syncProfile(entry.getKey(), entry.getValue(), batchJobStats));
        entityManagerFlushAndClear();
    }

    private void syncProfile(MemberView member, User user, JobRecordProcessingStats batchJobStats) {
        if (userInfoUpdated(user, member)) {
            if (log.isDebugEnabled()) {
                log.debug("syncAzureUsers - member[{} ({})] info has changed", member.getName(), member.getExternalId());
            }
            memberService.updateMember(buildMemberFromUser(user, member));
            batchJobStats.recordUpdated();
        }

        // Ensure personal team exists for member; cleanup users that had personal team deleted or not created with member
        // Should be able to remove this check after verifying this cleanup and that personal teams can no longer be deleted.
        try {
            if (!newMemberService.hasPersonalTeam(member) && newMemberService.createPersonalTeam(member, user)) {
                log.info("Member[{}] personal team recreated", member.getExternalAlias());
            }
        } catch (ApiException e) {
            log.error("syncAzureUsers - error creating personal team", e);
        }
    }

    private void entityManagerFlushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static boolean userInfoUpdated(User user, MemberView member) {
        return (!Strings.CS.equals(user.getEmail(), member.getEmail()) ||
                !Strings.CS.equals(user.getDisplayName(), member.getName()) ||
                !Strings.CS.equals(user.getLastName(), member.getLastName()) ||
                !Strings.CS.equals(user.getFirstName(), member.getFirstName()) ||
                !Strings.CS.equals(user.getEmployeeId(), member.getEmployeeId())
        );
    }

    private static Member buildMemberFromUser(User user, MemberView member) {
        return Member.builder()
                .id(member.getId())
                .externalId(member.getExternalId())
                .externalAlias(member.getExternalAlias())
                .email(user.getEmail())
                .name(user.getDisplayName())
                .lastName(user.getLastName())
                .firstName(user.getFirstName())
                .employeeId(user.getEmployeeId())
                .build();
    }
}
