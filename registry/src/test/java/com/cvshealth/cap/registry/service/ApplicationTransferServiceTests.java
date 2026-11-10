// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.ApplicationTransferMapper;
import com.cvshealth.cap.registry.mapper.ApplicationTransferViewMapper;
import com.cvshealth.cap.registry.model.Application;
import com.cvshealth.cap.registry.model.ApplicationTransfer;
import com.cvshealth.cap.registry.model.ApplicationTransferStatus;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.AuditAction;
import com.cvshealth.cap.registry.model.audit.AuditEntity;
import com.cvshealth.cap.registry.model.audit.AuditEntityType;
import com.cvshealth.cap.registry.model.audit.AuditEntry;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.persistent.ApplicationTransferEntity;
import com.cvshealth.cap.registry.persistent.ApplicationTransferViewEntity;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.repository.ApplicationRepository;
import com.cvshealth.cap.registry.repository.ApplicationTransferRepository;
import com.cvshealth.cap.registry.repository.ApplicationTransferViewRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationTransferServiceTests {
    @InjectMocks
    private ApplicationTransferService service;
    @Mock
    private ApplicationTransferServiceValidator validator;
    @Mock
    private ApplicationTransferRepository repository;
    @Mock
    private ApplicationTransferViewRepository viewRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TeamService teamService;
    @Mock
    private ApplicationService applicationService;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private AuditService auditService;
    @Spy
    private ApplicationTransferMapper mapper = Mappers.getMapper(ApplicationTransferMapper.class);
    @Spy
    private ApplicationTransferViewMapper viewMapper = Mappers.getMapper(ApplicationTransferViewMapper.class);
    @Captor
    private ArgumentCaptor<AuditEntry> auditEntryArgCaptor;

    @Test
    public void whenGetApplicationTransfer_ThenReturnResponse() {
        Long id = new Random().nextLong();
        when(repository.findById(id)).thenReturn(Optional.of(ApplicationTransferEntity.builder().id(id).applicationId(new Random().nextLong()).sourceTeamId(new Random().nextLong()).receiveTeamId(new Random().nextLong()).createTimestamp(Instant.now()).build()));

        var response = service.getApplicationTransfer(id);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetApplicationTransferNotFound_ThenReturnException() {
        Long id = new Random().nextLong();

        ApiException thrown = assertThrows(ApiException.class, () -> service.getApplicationTransfer(id));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetApplicationTransfersForTeam_ThenReturnResponse() {
        var teamId = new Random().nextLong();
        when(repository.findAllBySourceTeamIdOrReceiveTeamId(teamId, teamId)).thenReturn(List.of(
                ApplicationTransferEntity.builder().id(new Random().nextLong()).applicationId(new Random().nextLong()).sourceTeamId(teamId).receiveTeamId(new Random().nextLong()).createTimestamp(Instant.now()).build(),
                ApplicationTransferEntity.builder().id(new Random().nextLong()).applicationId(new Random().nextLong()).sourceTeamId(new Random().nextLong()).receiveTeamId(teamId).createTimestamp(Instant.now()).build()));

        List<ApplicationTransfer> response = service.getApplicationTransfersForTeam(teamId);

        assertThat(response).isNotNull();
        assertThat(response.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    public void whenGetApplicationTransfersForTeamByName_ThenReturnResponse() {
        var teamId = new Random().nextLong();
        var teamName = "team-1";
        when(teamService.getTeamByName(teamName)).thenReturn(Team.builder().id(teamId).name(teamName).build());
        when(viewRepository.findAllBySourceTeamIdOrReceiveTeamId(teamId, teamId)).thenReturn(List.of(
                ApplicationTransferViewEntity.builder().id(new Random().nextLong())
                        .application(ApplicationEntity.builder().id(new Random().nextLong()).name("app-1").build())
                        .sourceTeam(TeamEntity.builder().id(teamId).name(teamName).build())
                        .receiveTeam(TeamEntity.builder().id(new Random().nextLong()).name("team-2").build())
                        .createTimestamp(Instant.now()).build(),
                ApplicationTransferViewEntity.builder().id(new Random().nextLong())
                        .application(ApplicationEntity.builder().id(new Random().nextLong()).name("app-2").build())
                        .sourceTeam(TeamEntity.builder().id(new Random().nextLong()).name("team-3").build())
                        .receiveTeam(TeamEntity.builder().id(teamId).name(teamName).build())
                        .createTimestamp(Instant.now()).build()));

        List<ApplicationTransferView> response = service.getApplicationTransfersForTeamName(teamName);

        assertThat(response).isNotNull();
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    public void whenCreateApplicationTransfer_ThenReturnResponse() throws NotificationServiceException {
        Long id = new Random().nextLong();
        var applicationId = new Random().nextLong();
        var sourceTeamId = new Random().nextLong();
        var receiveTeamId = new Random().nextLong();
        Team sourceTeam = Team.builder().id(sourceTeamId).name("Source Team Name").build();
        Team receiveTeam = Team.builder().id(receiveTeamId).name("Receive Team Name").build();
        Application application = Application.builder().id(applicationId).name("Application Name").build();
        when(repository.save(any(ApplicationTransferEntity.class))).thenReturn(ApplicationTransferEntity.builder().id(id).applicationId(applicationId).sourceTeamId(sourceTeamId).receiveTeamId(receiveTeamId).createTimestamp(Instant.now()).build());
        when(teamService.getTeam(sourceTeamId)).thenReturn(sourceTeam);
        when(teamService.getTeam(receiveTeamId)).thenReturn(receiveTeam);
        when(applicationService.getApplication(applicationId)).thenReturn(application);

        // when
        var response = service.createApplicationTransfer(ApplicationTransfer.builder().applicationId(applicationId).sourceTeamId(sourceTeamId).receiveTeamId(receiveTeamId).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);

        verify(notificationService, times(2)).sendTeamNotification(any(NotificationRequest.class));
        verifyAuditEntity(AuditAction.CREATE_TRANSFER, application, sourceTeam, receiveTeam);
    }

    @Test
    public void whenCreateApplicationTransfer_AlreadyExists_ThenReturnException() {
        Long id = new Random().nextLong();
        var entity = ApplicationTransferEntity.builder().id(id).applicationId(new Random().nextLong()).sourceTeamId(new Random().nextLong()).receiveTeamId(new Random().nextLong()).createTimestamp(Instant.now()).build();
        when(repository.findFirstByApplicationIdAndStatus(entity.getApplicationId(), ApplicationTransferStatus.PENDING)).thenReturn(Optional.of(entity));

        ApiException thrown = assertThrows(ApiException.class, () -> service.createApplicationTransfer(ApplicationTransfer.builder().applicationId(entity.getApplicationId()).build()));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void whenUpdateApplicationTransfer_ThenReturnResponse() throws NotificationServiceException {
        Long id = new Random().nextLong();
        var applicationId = new Random().nextLong();
        var sourceTeamId = new Random().nextLong();
        var receiveTeamId = new Random().nextLong();
        Team sourceTeam = Team.builder().id(sourceTeamId).name("Source Team Name").build();
        Team receiveTeam = Team.builder().id(receiveTeamId).name("Receive Team Name").build();
        Application application = Application.builder().id(applicationId).name("Application Name").build();
        var entity = ApplicationTransferEntity.builder().id(id).applicationId(applicationId).sourceTeamId(sourceTeamId).receiveTeamId(receiveTeamId).createTimestamp(Instant.now()).build();
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(ApplicationTransferEntity.class))).thenReturn(entity);
        when(applicationService.getApplication(any())).thenReturn(Application.builder().build());

        when(teamService.getTeam(sourceTeamId)).thenReturn(sourceTeam);
        when(teamService.getTeam(receiveTeamId)).thenReturn(receiveTeam);
        when(applicationService.getApplication(applicationId)).thenReturn(application);

        // when
        var response = service.updateApplicationTransfer(ApplicationTransfer.builder().id(id).applicationId(applicationId).sourceTeamId(sourceTeamId).receiveTeamId(receiveTeamId).status(ApplicationTransferStatus.CANCELLED).build());

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);

        verify(notificationService, times(2)).sendTeamNotification(any(NotificationRequest.class));
        verifyAuditEntity(AuditAction.UPDATE_TRANSFER, application, sourceTeam, receiveTeam);
    }

    @Test
    public void whenUpdateApplicationTransferNotFound_ThenReturnException() {
        Long id = new Random().nextLong();

        ApiException thrown = assertThrows(ApiException.class, () -> service.updateApplicationTransfer(ApplicationTransfer.builder().id(id).build()));

        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenForceApplicationTransfer() {
        var applicationId = new Random().nextLong();
        var sourceTeamId = new Random().nextLong();
        var receiveTeamId = new Random().nextLong();
        ApplicationTransfer transferRequest = ApplicationTransfer.builder()
                .applicationId(applicationId)
                .sourceTeamId(sourceTeamId)
                .receiveTeamId(receiveTeamId)
                .build();
        Application application = Application.builder().id(applicationId).name("Application Name").build();

        when(repository.findAllByApplicationId(applicationId)).thenReturn(Collections.emptyList());
        when(applicationService.getApplication(applicationId)).thenReturn(application);

        // when
        service.forceApplicationTransfer(transferRequest);

        verify(applicationService, times(1)).transferApplication(application);
    }

    @Test
    public void whenGetApplicationTransfersForTeamsByName_ThenReturnResponse() {
        // given
        List<String> teamNames = List.of("Team-1", "Team-2", "Team-3");
        TeamEntity team1 = TeamEntity.builder().id(1L).name("Team-1").build();
        TeamEntity team2 = TeamEntity.builder().id(2L).name("Team-2").build();
        TeamEntity team4 = TeamEntity.builder().id(4L).name("Team-4").build();
        ApplicationTransferSearchFilter filter = ApplicationTransferSearchFilter.builder().teamNames(teamNames).build();
        PaginationInput paginationInput = PaginationInput.builder().pageNumber(1).pageSize(10).build();
        PaginatedTeamResponse paginatedTeams = PaginatedTeamResponse.builder()
                .teams(List.of(
                        TeamView.builder().id(team1.getId()).name(team1.getName()).build(),
                        TeamView.builder().id(team2.getId()).name(team2.getName()).build()
                ))
                .build();

        when(teamService.getTeams(any(PaginationInput.class), any(TeamSearchFilter.class), any())).thenReturn(paginatedTeams);
        when(viewRepository.findAllBySourceTeamIdsOrReceiveTeamIds(any(), any(), any())).thenReturn(new PageImpl<>(List.of(
                ApplicationTransferViewEntity.builder().id(10L).createTimestamp(Instant.now().minusSeconds(120))
                        .application(ApplicationEntity.builder().id(100L).name("app1").build())
                        .sourceTeam(team1).receiveTeam(team2).build(),
                ApplicationTransferViewEntity.builder().id(11L).createTimestamp(Instant.now().minusSeconds(90))
                        .application(ApplicationEntity.builder().id(101L).name("app2").build())
                        .sourceTeam(team1).receiveTeam(team2).build(),
                ApplicationTransferViewEntity.builder().id(12L).createTimestamp(Instant.now().minusSeconds(60))
                        .application(ApplicationEntity.builder().id(102L).name("app3").build())
                        .sourceTeam(team4).receiveTeam(team2).build())));

        // when
        PaginatedApplicationTransferResponse response = service.getApplicationTransfersForTeamsByName(paginationInput, filter);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getApplicationTransfers()).size().isEqualTo(3);
    }

    @Test
    public void whenGetApplicationTransfersForTeamsByName_teamsSizeInvalid_ThenReturnException() {
        // given
        List<String> teamNames = new ArrayList<>(1001);
        for (int i = 0; i < 1001; i++) {
            teamNames.add("Team Name " + i);
        }
        ApplicationTransferSearchFilter filter = ApplicationTransferSearchFilter.builder().teamNames(teamNames).build();
        PaginationInput paginationInput = PaginationInput.builder().build();

        // exception is thrown when teamNames size is greater than max allowed
        BadRequestException thrown = assertThrows(BadRequestException.class, () -> service.getApplicationTransfersForTeamsByName(paginationInput, filter));
        assertThat(thrown.getMessage()).isEqualTo("Number of teams is limited to 1000");
    }

    @Test
    public void buildMessageModel_whenTransferIsPending() {
        // given
        Team sourceTeam = mock(Team.class);
        Team receiveTeam = mock(Team.class);
        Application application = mock(Application.class);
        ApplicationTransfer request = mock(ApplicationTransfer.class);
        when(sourceTeam.getName()).thenReturn("Team-1");
        when(receiveTeam.getName()).thenReturn("Team-2");
        when(application.getName()).thenReturn("CoolApplication");
        when(request.getStatus()).thenReturn(ApplicationTransferStatus.PENDING);
        when(request.getComments()).thenReturn(null);
        when(notificationService.registryOrCapUI()).thenReturn("in the Registry or CAP UI");

        // when
        Map<String, Object> messageModel = service.buildMessageModel(request, application, sourceTeam, receiveTeam);

        // then
        assertThat(messageModel).isNotNull();
        assertThat(messageModel.get("applicationName")).isEqualTo("CoolApplication");
        assertThat(messageModel.get("sourceTeamName")).isEqualTo("Team-1");
        assertThat(messageModel.get("receiveTeamName")).isEqualTo("Team-2");
        assertThat(messageModel.get("transferStatus")).isEqualTo(ApplicationTransferStatus.PENDING);
        assertThat(messageModel.get("transferComments")).isEqualTo("");
        assertThat(messageModel.get("inRegistryOrCapUI")).isEqualTo("in the Registry or CAP UI");
        verifyNoMoreInteractions(sourceTeam);
        verifyNoMoreInteractions(receiveTeam);
        verifyNoMoreInteractions(application);
        verifyNoMoreInteractions(request);
    }

    @Test
    public void buildMessageModel_whenTransferIsComplete() {
        // given
        Team sourceTeam = mock(Team.class);
        Team receiveTeam = mock(Team.class);
        Application application = mock(Application.class);
        ApplicationTransfer request = mock(ApplicationTransfer.class);
        when(sourceTeam.getName()).thenReturn("Team-1");
        when(receiveTeam.getName()).thenReturn("Team-2");
        when(application.getName()).thenReturn("CoolApplication");
        when(request.getStatus()).thenReturn(ApplicationTransferStatus.COMPLETED);
        when(request.getComments()).thenReturn("accept");
        when(notificationService.registryOrCapUI()).thenReturn("in the Registry or CAP UI");

        // when
        Map<String, Object> messageModel = service.buildMessageModel(request, application, sourceTeam, receiveTeam);

        // then
        assertThat(messageModel).isNotNull();
        assertThat(messageModel.get("applicationName")).isEqualTo("CoolApplication");
        assertThat(messageModel.get("sourceTeamName")).isEqualTo("Team-1");
        assertThat(messageModel.get("receiveTeamName")).isEqualTo("Team-2");
        assertThat(messageModel.get("transferStatus")).isEqualTo(ApplicationTransferStatus.COMPLETED);
        assertThat(messageModel.get("transferComments")).isEqualTo("accept");
        assertThat(messageModel.get("inRegistryOrCapUI")).isEqualTo("in the Registry or CAP UI");
        verifyNoMoreInteractions(sourceTeam);
        verifyNoMoreInteractions(receiveTeam);
        verifyNoMoreInteractions(application);
        verifyNoMoreInteractions(request);
    }

    @Test
    public void testCreateApplicationTransfer() {
        // given
        ApplicationTransferStatus status = ApplicationTransferStatus.PENDING;
        ApplicationTransfer request = mock(ApplicationTransfer.class);
        when(request.getStatus()).thenReturn(status);
        when(request.getApplicationId()).thenReturn(1L);
        when(request.getSourceTeamId()).thenReturn(10L);
        when(request.getReceiveTeamId()).thenReturn(11L);
        Application application = mock(Application.class);
        Team receiveTeam = mock(Team.class);
        Team sourceTeam = mock(Team.class);
        ApplicationTransferEntity entity = mock(ApplicationTransferEntity.class);
        when(applicationService.getApplication(1L)).thenReturn(application);
        when(teamService.getTeam(11L)).thenReturn(receiveTeam);
        when(teamService.getTeam(10L)).thenReturn(sourceTeam);
        when(validator.validate(request)).thenReturn(request);
        when(repository.findFirstByApplicationIdAndStatus(request.getApplicationId(), status)).thenReturn(Optional.empty());
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);

        // when
        ApplicationTransfer result = service.createApplicationTransfer(request);

        // then
        assertThat(result).isNotNull();
        verify(validator).validateApplicationForTransfer(application, receiveTeam);
        verify(auditService).publish(any(AuditEntry.class));
        verifyNoMoreInteractions(applicationService);
        verifyNoMoreInteractions(validator);
        verifyNoMoreInteractions(teamService);
        verifyNoMoreInteractions(auditService);
        verifyNoMoreInteractions(repository);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    public void testCompleteApplicationTransfer() {
        // given
        ApplicationTransferStatus status = ApplicationTransferStatus.COMPLETED;
        ApplicationTransfer request = mock(ApplicationTransfer.class);
        when(request.getStatus()).thenReturn(status);
        when(request.getApplicationId()).thenReturn(1L);
        when(request.getSourceTeamId()).thenReturn(10L);
        when(request.getReceiveTeamId()).thenReturn(11L);
        Application application = mock(Application.class);
        Team receiveTeam = mock(Team.class);
        Team sourceTeam = mock(Team.class);
        ApplicationTransferEntity entity = mock(ApplicationTransferEntity.class);
        when(applicationService.getApplication(1L)).thenReturn(application);
        when(teamService.getTeam(11L)).thenReturn(receiveTeam);
        when(teamService.getTeam(10L)).thenReturn(sourceTeam);
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);
        when(validator.validate(request)).thenReturn(request);
        when(validator.validateUpdate(request, entity)).thenReturn(request);

        // when
        ApplicationTransfer result = service.updateApplicationTransfer(request);

        // then
        assertThat(result).isNotNull();
        verify(validator).validate(request);
        verify(validator).validateApplicationForTransfer(application, receiveTeam);
        verify(auditService).publish(any(AuditEntry.class));
        verifyNoMoreInteractions(applicationService);
        verifyNoMoreInteractions(validator);
        verifyNoMoreInteractions(teamService);
        verifyNoMoreInteractions(auditService);
        verifyNoMoreInteractions(repository);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    public void testRejectApplicationTransfer() {
        // given
        ApplicationTransferStatus status = ApplicationTransferStatus.REJECTED;
        ApplicationTransfer request = mock(ApplicationTransfer.class);
        when(request.getStatus()).thenReturn(status);
        when(request.getApplicationId()).thenReturn(1L);
        when(request.getSourceTeamId()).thenReturn(10L);
        when(request.getReceiveTeamId()).thenReturn(11L);
        Application application = mock(Application.class);
        Team receiveTeam = mock(Team.class);
        Team sourceTeam = mock(Team.class);
        ApplicationTransferEntity entity = mock(ApplicationTransferEntity.class);
        when(applicationService.getApplication(1L)).thenReturn(application);
        when(teamService.getTeam(11L)).thenReturn(receiveTeam);
        when(teamService.getTeam(10L)).thenReturn(sourceTeam);
        when(repository.findById(any())).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);
        when(validator.validate(request)).thenReturn(request);
        when(validator.validateUpdate(request, entity)).thenReturn(request);

        // when
        ApplicationTransfer result = service.updateApplicationTransfer(request);

        // then
        assertThat(result).isNotNull();
        verify(validator).validate(request);
        verify(auditService).publish(any(AuditEntry.class));
        verifyNoMoreInteractions(applicationService);
        verifyNoMoreInteractions(validator);
        verifyNoMoreInteractions(teamService);
        verifyNoMoreInteractions(auditService);
        verifyNoMoreInteractions(repository);
        verifyNoMoreInteractions(mapper);
    }

    private void verifyAuditEntity(AuditAction action, Application application, Team sourceTeam, Team receiveTeam) {
        verify(auditService).publish(auditEntryArgCaptor.capture());
        AuditEntry auditEntry = auditEntryArgCaptor.getValue();
        assertThat(auditEntry.getActionType()).isEqualTo(action);
        assertThat(auditEntry.getSubjectEntity()).isNotNull();
        assertThat(auditEntry.getSubjectEntity())
                .extracting(AuditEntity::getEntityType, AuditEntity::getId, AuditEntity::getName)
                .containsExactly(AuditEntityType.APPLICATION, application.getId(), application.getName());
        assertThat(auditEntry.getSourceEntity())
                .extracting(AuditEntity::getEntityType, AuditEntity::getId, AuditEntity::getName)
                .containsExactly(AuditEntityType.TEAM, sourceTeam.getId(), sourceTeam.getName());
        assertThat(auditEntry.getTargetEntity())
                .extracting(AuditEntity::getEntityType, AuditEntity::getId, AuditEntity::getName)
                .containsExactly(AuditEntityType.TEAM, receiveTeam.getId(), receiveTeam.getName());
    }
}
