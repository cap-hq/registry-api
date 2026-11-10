// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.TeamMember;
import com.cvshealth.cap.registry.model.TeamMemberRole;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class TeamMemberServiceValidatorTests {
    @InjectMocks
    private TeamMemberServiceValidator validator;

    @Test
    public void givenValidObject_whenValidate_thenReturnRequest() throws ApiException {
        TeamMember request = TeamMember.builder()
                .teamId(new Random().nextLong())
                .memberId(new Random().nextLong())
                .role(TeamMemberRole.OWNER)
                .build();

        request = validator.validate(request);

        assertThat(request).isNotNull();
    }

    @Test
    public void givenNull_whenValidate_thenThrowException() {
        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(null);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.REQUEST_IS_EMPTY.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.REQUEST_IS_EMPTY);
    }

    @Test
    public void givenMissingTeamId_whenValidate_thenThrowException() {
        TeamMember request = TeamMember.builder()
                .teamId(null)
                .memberId(new Random().nextLong())
                .role(TeamMemberRole.OWNER)
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAMMEMBER_TEAM_ID_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAMMEMBER_TEAM_ID_REQUIRED);
    }

    @Test
    public void givenMissingMemberId_whenValidate_thenThrowException() {
        TeamMember request = TeamMember.builder()
                .teamId(new Random().nextLong())
                .memberId(null)
                .role(TeamMemberRole.OWNER)
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAMMEMBER_MEMBER_ID_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAMMEMBER_MEMBER_ID_REQUIRED);
    }

    @Test
    public void givenMissingRole_whenValidate_thenThrowException() {
        TeamMember request = TeamMember.builder()
                .teamId(new Random().nextLong())
                .memberId(new Random().nextLong())
                .role(null)
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAMMEMBER_ROLE_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAMMEMBER_ROLE_REQUIRED);
    }

}
