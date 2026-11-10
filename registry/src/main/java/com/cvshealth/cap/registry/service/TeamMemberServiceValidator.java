// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.TeamMember;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.springframework.stereotype.Component;

@Component
public class TeamMemberServiceValidator {
    public TeamMember validate(TeamMember request) throws ApiException {
        if (null == request) {
            throw new BadRequestException(ExceptionReason.REQUEST_IS_EMPTY);
        }
        if (request.getTeamId() == null) {
            throw new BadRequestException(ExceptionReason.TEAMMEMBER_TEAM_ID_REQUIRED);
        }
        if (request.getMemberId() == null) {
            throw new BadRequestException(ExceptionReason.TEAMMEMBER_MEMBER_ID_REQUIRED);
        }
        if (request.getRole() == null) {
            throw new BadRequestException(ExceptionReason.TEAMMEMBER_ROLE_REQUIRED);
        }
        return request;
    }
}
