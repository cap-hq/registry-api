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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationTransferServiceValidatorTests {
    @InjectMocks
    private ApplicationTransferServiceValidator validator;
    @Mock
    private ApplicationServiceValidator applicationServiceValidator;

    @Test
    public void givenValidObject_whenValidate_thenReturnRequest() throws ApiException {
        var request = ApplicationTransfer.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .build();

        request = validator.validate(request);

        assertThat(request).isNotNull();
    }

    @Test
    public void givenMissingApplicationId_whenValidate_thenReturnException() throws ApiException {
        var request = ApplicationTransfer.builder()
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TRANSFER_APPLICATION_ID_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_APPLICATION_ID_REQUIRED);
    }

    @Test
    public void givenMissingSourceTeamId_whenValidate_thenReturnException() throws ApiException {
        var request = ApplicationTransfer.builder()
                .applicationId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_ID_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_ID_REQUIRED);
    }

    @Test
    public void givenMissingReceiveTeamId_whenValidate_thenReturnException() throws ApiException {
        var request = ApplicationTransfer.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(new Random().nextLong())
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validate(request);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_ID_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_ID_REQUIRED);
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
    public void givenValidObject_whenValidateUpdate_thenReturnRequest() throws ApiException {
        var entity = ApplicationTransferEntity.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .status(ApplicationTransferStatus.PENDING)
                .build();
        var request = ApplicationTransfer.builder()
                .applicationId(entity.getApplicationId())
                .sourceTeamId(entity.getSourceTeamId())
                .receiveTeamId(entity.getReceiveTeamId())
                .status(ApplicationTransferStatus.COMPLETED)
                .comments("Thank you")
                .build();

        request = validator.validateUpdate(request, entity);

        assertThat(request).isNotNull();
    }

    @Test
    public void givenMissingStatus_whenValidateUpdate_thenReturnException() throws ApiException {
        var entity = ApplicationTransferEntity.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .status(ApplicationTransferStatus.PENDING)
                .build();
        var request = ApplicationTransfer.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(entity.getSourceTeamId())
                .receiveTeamId(entity.getReceiveTeamId())
                .comments("Thank you")
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validateUpdate(request, entity);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TRANSFER_STATUS_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_STATUS_REQUIRED);
    }

    @Test
    public void givenModifiedApplicationId_whenValidateUpdate_thenReturnException() throws ApiException {
        var entity = ApplicationTransferEntity.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .status(ApplicationTransferStatus.PENDING)
                .build();
        var request = ApplicationTransfer.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(entity.getSourceTeamId())
                .receiveTeamId(entity.getReceiveTeamId())
                .status(ApplicationTransferStatus.COMPLETED)
                .comments("Thank you")
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validateUpdate(request, entity);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TRANSFER_APPLICATION_IMMUTABLE_ON_UPDATE.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_APPLICATION_IMMUTABLE_ON_UPDATE);
    }

    @Test
    public void givenModifiedSourceTeamId_whenValidateUpdate_thenReturnException() throws ApiException {
        var entity = ApplicationTransferEntity.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .status(ApplicationTransferStatus.PENDING)
                .build();
        var request = ApplicationTransfer.builder()
                .applicationId(entity.getApplicationId())
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(entity.getReceiveTeamId())
                .status(ApplicationTransferStatus.COMPLETED)
                .comments("Thank you")
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validateUpdate(request, entity);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_IMMUTABLE_ON_UPDATE.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_SOURCE_TEAM_IMMUTABLE_ON_UPDATE);
    }

    @Test
    public void givenModifiedReceiveTeamId_whenValidateUpdate_thenReturnException() throws ApiException {
        var entity = ApplicationTransferEntity.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .status(ApplicationTransferStatus.PENDING)
                .build();
        var request = ApplicationTransfer.builder()
                .applicationId(entity.getApplicationId())
                .sourceTeamId(entity.getSourceTeamId())
                .receiveTeamId(new Random().nextLong())
                .status(ApplicationTransferStatus.COMPLETED)
                .comments("Thank you")
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validateUpdate(request, entity);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_IMMUTABLE_ON_UPDATE.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_RECEIVE_TEAM_IMMUTABLE_ON_UPDATE);
    }

    @Test
    public void givenModifiedReceiveTeamId_whenComplete_thenReturnException() throws ApiException {
        var entity = ApplicationTransferEntity.builder()
                .applicationId(new Random().nextLong())
                .sourceTeamId(new Random().nextLong())
                .receiveTeamId(new Random().nextLong())
                .status(ApplicationTransferStatus.COMPLETED)
                .build();
        var request = ApplicationTransfer.builder()
                .applicationId(entity.getApplicationId())
                .sourceTeamId(entity.getSourceTeamId())
                .receiveTeamId(entity.getReceiveTeamId())
                .status(ApplicationTransferStatus.COMPLETED)
                .comments("Thank you")
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> {
            validator.validateUpdate(request, entity);
        });
        assertThat(ex.getMessage()).contains(ExceptionReason.APPLICATION_TRANSFER_IMMUTABLE_ON_COMPLETED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_IMMUTABLE_ON_COMPLETED);
    }

    @Test
    public void validateApplicationForTransfer() {
        // given
        Long teamId = 1L;
        Application application = Application.builder().name("app1").cloud("cloud1").teamId(teamId).build();
        Team receiveTeam = Team.builder().id(teamId).name("receiveTeam1").build();

        // when
        validator.validateApplicationForTransfer(application, receiveTeam);

        // then
        verify(applicationServiceValidator).validateCloudTenant(application, receiveTeam);
        verifyNoMoreInteractions(applicationServiceValidator);
    }

    @Test
    public void validateApplicationForTransfer_whenNotAuthorizedToCloud() {
        // given
        Long teamId = 1L;
        Application application = Application.builder().name("app1").cloud("cloud1").teamId(teamId).build();
        Team receiveTeam = Team.builder().id(teamId).name("receiveTeam1").build();

        BadRequestException badRequestException = new BadRequestException(ExceptionReason.APPLICATION_CLOUD_INVALID);
        doThrow(badRequestException).when(applicationServiceValidator).validateCloudTenant(application, receiveTeam);

        // when
        BadRequestException exception = assertThrows(BadRequestException.class, () -> validator.validateApplicationForTransfer(application, receiveTeam));

        // then
        assertThat(exception.getReason()).isEqualTo(ExceptionReason.APPLICATION_TRANSFER_INVALID_RECEIVE_TEAM);
        verifyNoMoreInteractions(applicationServiceValidator);
    }
}
