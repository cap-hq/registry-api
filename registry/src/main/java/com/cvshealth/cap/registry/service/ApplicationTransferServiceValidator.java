// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.Application;
import com.cvshealth.cap.registry.model.ApplicationTransfer;
import com.cvshealth.cap.registry.model.ApplicationTransferStatus;
import com.cvshealth.cap.registry.model.Team;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.ApplicationTransferEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationTransferServiceValidator {

    private final ApplicationServiceValidator applicationServiceValidator;

    public ApplicationTransfer validate(ApplicationTransfer request) throws ApiException {
        if (null == request) {
            throw new BadRequestException(ExceptionReason.REQUEST_IS_EMPTY);
        }
        if (request.getApplicationId() == null) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_APPLICATION_ID_REQUIRED);
        }
        if (request.getSourceTeamId() == null) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_ID_REQUIRED);
        }
        if (request.getReceiveTeamId() == null) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_ID_REQUIRED);
        }
        if (request.getSourceTeamId().equals(request.getReceiveTeamId())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_SAME_TEAM_IDS);
        }
        return request;
    }

    public ApplicationTransfer validateUpdate(ApplicationTransfer request, ApplicationTransferEntity entity) throws ApiException {
        if (request.getStatus() == null) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_STATUS_REQUIRED);
        }
        if (!entity.getApplicationId().equals(request.getApplicationId())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_APPLICATION_IMMUTABLE_ON_UPDATE);
        }
        if (!entity.getSourceTeamId().equals(request.getSourceTeamId())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_IMMUTABLE_ON_UPDATE);
        }
        if (!entity.getReceiveTeamId().equals(request.getReceiveTeamId())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_IMMUTABLE_ON_UPDATE);
        }
        if (ApplicationTransferStatus.COMPLETED.equals(entity.getStatus())) {
            throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_IMMUTABLE_ON_COMPLETED);
        }
        return request;
    }

    public void validateApplicationForTransfer(Application application, Team receiveTeam) {
        try {
            applicationServiceValidator.validateCloudTenant(application, receiveTeam);
        } catch (BadRequestException e) {
            if (ExceptionReason.APPLICATION_CLOUD_INVALID.equals(e.getReason())) {
                String message = String.format("Team is not authorized to use cloud '%s'", application.getCloud());
                throw new BadRequestException(ExceptionReason.APPLICATION_TRANSFER_INVALID_RECEIVE_TEAM,
                        String.format(ExceptionReason.APPLICATION_TRANSFER_INVALID_RECEIVE_TEAM.label, message));
            }
            throw e;
        }
    }
}
