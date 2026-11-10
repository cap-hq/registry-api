// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.TeamAuthClient;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.springframework.stereotype.Component;

@Component
public class TeamAuthClientServiceValidator {
    public TeamAuthClient validate(TeamAuthClient request) throws ApiException {
        if (null == request) {
            throw new BadRequestException(ExceptionReason.REQUEST_IS_EMPTY);
        }
        if (request.getTeamId() == null) {
            throw new BadRequestException(ExceptionReason.TEAMAUTHCLIENT_TEAM_ID_REQUIRED);
        }
        if (request.getAuthClientId() == null) {
            throw new BadRequestException(ExceptionReason.TEAMAUTHCLIENT_AUTHCLIENT_ID_REQUIRED);
        }
        return request;
    }
}
