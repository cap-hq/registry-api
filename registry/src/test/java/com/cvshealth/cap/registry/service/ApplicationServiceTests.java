// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.ApplicationViewMapper;
import com.cvshealth.cap.registry.mapper.ApplicationMapper;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.model.exception.ResourceConstraintException;
import com.cvshealth.cap.registry.persistent.ApplicationViewEntity;
import com.cvshealth.cap.registry.persistent.ApplicationEntity;
import com.cvshealth.cap.registry.persistent.TeamViewEntity;
import com.cvshealth.cap.registry.repository.ApplicationViewRepository;
import com.cvshealth.cap.registry.repository.ApplicationRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.authClient;
import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.capturedClientIds;
import static com.cvshealth.cap.registry.service.AuthClientTestFixtures.teamWithClients;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicationServiceTests {
    @InjectMocks
    private ApplicationService service;
    @Mock
    private ApplicationServiceValidator validator;
    @Mock
    private ApplicationRepository repository;
    @Mock
    private ApplicationViewRepository viewRepository;
    @Mock
    private RegistryAuthService registryAuthService;
    @Mock
    private List<ApplicationPriority> applicationPriorities;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationMapper mapper;
    @Mock
    private ApplicationViewMapper viewMapper;
    @Mock
    private TeamService teamService;
    @Mock
    private CloudTenantService cloudTenantService;
    @Mock
    private ApiResourceConstraintService apiResourceConstraintService;
    @Captor
    private ArgumentCaptor<AuditEntry> auditEntryArgCaptor;
    @Captor
    private ArgumentCaptor<ChangeEventDetails> changeEventDetailsCaptor;

    @Test
    public void givenRequest_whenCreateApplication_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Long teamId = new Random().nextLong();
        Application mockApplication = Application.builder().teamId(teamId).id(id).name("App1").teamId(1L).build();
        Team mockTeam = Team.builder().name("Team-1").id(teamId).build();
        ApplicationEntity mockApplicationEntity = ApplicationEntity.builder().id(id).name("App1").build();
        when(teamService.getTeam(mockApplication.getTeamId())).thenReturn(Team.builder().name("Team-1").build());
        when(validator.validate(any())).thenReturn(mockApplication);
        when(validator.validateIfComplianceTypeHasBeenSet(mockApplication, null)).thenReturn(true);
        when(validator.validateIfExternalIdHasBeenSet(mockApplication, null)).thenReturn(false);
        when(cloudTenantService.getDefaultCloud()).thenReturn("gcp");
        when(cloudTenantService.getDefaultTenant()).thenReturn("general");
        when(repository.save(any())).thenReturn(mockApplicationEntity);
        when(applicationPriorities.stream()).thenReturn(Stream.of(
                new ApplicationPriority(1,"Tier 1 (Mission critical applications)", false),
                new ApplicationPriority(2,"Tier 2 (Business critical applications)", false),
                new ApplicationPriority(3,"Tier 3 (Business operational applications)", true)
        ));
        when(mapper.toEntity(mockApplication)).thenReturn(mockApplicationEntity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(mockApplication);
        when(teamService.getTeamByName(any())).thenReturn(mockTeam);

        Application response = service.createApplication(mockApplication);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCloud()).isEqualTo("gcp");
        assertThat(response.getTenant()).isEqualTo("general");
        verify(validator).validate(any());
        verify(validator).validateNewName(any());
        verify(validator).validateIfComplianceTypeHasBeenSet(any(), any());
        // with the tags on, a level above the default can only come from tags, so the tag check already gates it
        verify(validator, never()).validateIfInformationClassificationHasBeenSet(any());
        verify(validator).validateIfExternalIdHasBeenSet(any(), any());
        verify(cloudTenantService).getDefaultCloud();
        verify(cloudTenantService).getDefaultTenant();
        verifyAuditEntity(AuditAction.CREATE, mockApplicationEntity);
    }

    @Test
    public void givenTooShortName_whenCreateApplication_thenMinLengthRejectionPropagates() {
        // given the create path applies the new-name minimum length rule
        Application request = Application.builder().name("ab").teamId(1L).build();
        willThrow(new BadRequestException(ExceptionReason.APPLICATION_NAME_INVALID_MIN_LENGTH))
                .given(validator).validateNewName(request);

        // when/then it rejects before any team lookup or persistence
        ApiException ex = assertThrows(ApiException.class, () -> service.createApplication(request, "Team-1"));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.APPLICATION_NAME_INVALID_MIN_LENGTH);
        verifyNoInteractions(repository, auditService);
    }

    @Test
    public void givenRequest_whenCreateApplication_withComplianceTypes_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Long teamId = new Random().nextLong();
        Application mockApplication = Application.builder().teamId(teamId).id(id).complianceTypes(Set.of("pci")).build();
        ApplicationEntity mockApplicationEntity = ApplicationEntity.builder().id(id).build();
        Team mockTeam = Team.builder().name("Team-1").id(teamId).build();

        when(validator.validate(any())).thenReturn(mockApplication);
        when(validator.validateIfComplianceTypeHasBeenSet(mockApplication, null)).thenReturn(true);
        when(cloudTenantService.getDefaultCloud()).thenReturn("gcp");
        when(cloudTenantService.getDefaultTenant()).thenReturn("general");
        when(repository.save(any())).thenReturn(mockApplicationEntity);
        when(applicationPriorities.stream()).thenReturn(Stream.of(
                new ApplicationPriority(1,"Tier 1 (Mission critical applications)", false),
                new ApplicationPriority(2,"Tier 2 (Business critical applications)", false),
                new ApplicationPriority(3,"Tier 3 (Business operational applications)", true)
        ));
        when(mapper.toEntity(mockApplication)).thenReturn(mockApplicationEntity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(mockApplication);
        when(teamService.getTeamByName(any())).thenReturn(mockTeam);

        // when
        Application response = service.createApplication(mockApplication);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCloud()).isEqualTo("gcp");
        assertThat(response.getTenant()).isEqualTo("general");
        verify(validator).validate(any());
        verify(validator).validateIfComplianceTypeHasBeenSet(any(), any());
        verify(cloudTenantService).getDefaultCloud();
        verify(cloudTenantService).getDefaultTenant();
    }

    @Test
    public void givenRequest_whenCreateApplication_withPriority_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Long teamId = new Random().nextLong();
        Application mockApplication = Application.builder().name("App1").priority(1).teamId(teamId).id(id).complianceTypes(Set.of("pci")).build();
        ApplicationEntity mockApplicationEntity = ApplicationEntity.builder().id(id).build();
        Team mockTeam = Team.builder().name("Team-1").id(teamId).build();

        when(validator.validate(any())).thenReturn(mockApplication);
        when(validator.validateIfPriorityHasBeenSet(mockApplication, null)).thenReturn(true);
        when(cloudTenantService.getDefaultCloud()).thenReturn("gcp");
        when(cloudTenantService.getDefaultTenant()).thenReturn("general");
        when(repository.save(any())).thenReturn(mockApplicationEntity);
        when(mapper.toEntity(mockApplication)).thenReturn(mockApplicationEntity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(mockApplication);
        when(teamService.getTeamByName(any())).thenReturn(mockTeam);

        // when
        Application response = service.createApplication(mockApplication);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCloud()).isEqualTo("gcp");
        assertThat(response.getTenant()).isEqualTo("general");
        verify(validator).validate(any());
        verify(validator).validateIfPriorityHasBeenSet(any(), any());
        verify(cloudTenantService).getDefaultCloud();
        verify(cloudTenantService).getDefaultTenant();
    }

    @Test
    public void givenRequest_whenCreateApplication_withExternalId_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Long teamId = new Random().nextLong();
        Application mockApplication = Application.builder().teamId(teamId).id(id).name("App1").teamId(1L).externalId("APPID-123").build();
        ApplicationEntity mockApplicationEntity = ApplicationEntity.builder().id(id).build();
        Team mockTeam = Team.builder().name("Team-1").id(teamId).build();

        when(teamService.getTeam(mockApplication.getTeamId())).thenReturn(Team.builder().name("Team-1").build());
        when(applicationPriorities.stream()).thenReturn(Stream.of(
                new ApplicationPriority(3,"Tier 3 (Business operational applications)", true)));
        when(validator.validate(any())).thenReturn(mockApplication);
        when(validator.validateIfPriorityHasBeenSet(mockApplication, null)).thenReturn(false);
        when(validator.validateIfExternalIdHasBeenSet(mockApplication, null)).thenReturn(true);
        when(cloudTenantService.getDefaultCloud()).thenReturn("gcp");
        when(cloudTenantService.getDefaultTenant()).thenReturn("general");
        when(repository.save(any())).thenReturn(mockApplicationEntity);
        when(mapper.toEntity(mockApplication)).thenReturn(mockApplicationEntity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(mockApplication);
        when(teamService.getTeamByName(any())).thenReturn(mockTeam);

        // when
        Application response = service.createApplication(mockApplication);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getCloud()).isEqualTo("gcp");
        assertThat(response.getTenant()).isEqualTo("general");
        verify(validator).validate(any());
        verify(validator).validateIfPriorityHasBeenSet(any(), any());
        verify(validator).validateIfExternalIdHasBeenSet(any(), any());
        verify(validator).validateExternalId(any(), any());
        verify(cloudTenantService).getDefaultCloud();
        verify(cloudTenantService).getDefaultTenant();
    }

    @Test
    public void givenRequest_whenUpdateApplication_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Application mockApplication = Application.builder().id(id).build();
        ApplicationEntity mockApplicationEntity = ApplicationEntity.builder().id(id).build();

        when(repository.findById(id)).thenReturn(Optional.of(mockApplicationEntity));
        when(validator.validate(any())).thenReturn(mockApplication);
        when(validator.validateUpdate(any(), any())).thenReturn(mockApplication);
        when(validator.validateIfComplianceTypeHasBeenSet(mockApplication, mockApplicationEntity)).thenReturn(false);
        when(validator.validateIfPriorityHasBeenSet(mockApplication, mockApplicationEntity)).thenReturn(false);
        when(repository.save(any())).thenReturn(mockApplicationEntity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(mockApplication);

        // when
        Application response = service.updateApplication(mockApplication);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        verify(validator).validate(any());
        verify(validator).validateUpdate(any(), any());
        verify(validator).validateIfComplianceTypeHasBeenSet(any(), any());
        verify(validator).validateIfPriorityHasBeenSet(any(), any());
        verifyAuditEntity(AuditAction.UPDATE, mockApplicationEntity);
    }

    @Test
    public void givenRequest_whenUpdateApplication_withComplianceTypes_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Application mockApplication = Application.builder().id(id).complianceTypes(Set.of("pci")).build();
        ApplicationEntity mockApplicationEntity = ApplicationEntity.builder().id(id).build();

        when(repository.findById(id)).thenReturn(Optional.of(mockApplicationEntity));
        when(validator.validate(any())).thenReturn(mockApplication);
        when(validator.validateUpdate(any(), any())).thenReturn(mockApplication);
        when(validator.validateIfComplianceTypeHasBeenSet(any(), any())).thenReturn(true);
        when(repository.save(any())).thenReturn(mockApplicationEntity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(mockApplication);

        // when
        Application response = service.updateApplication(mockApplication);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        verify(validator).validate(any());
        verify(validator).validateUpdate(any(), any());
        verify(validator).validateIfComplianceTypeHasBeenSet(any(), any());
    }

    @Test
    public void givenRequest_whenUpdateApplication_withPriorityThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Application mockApplication = Application.builder().id(id).priority(1).build();
        ApplicationEntity mockApplicationEntity = ApplicationEntity.builder().id(id).build();

        when(repository.findById(id)).thenReturn(Optional.of(mockApplicationEntity));
        when(validator.validate(any())).thenReturn(mockApplication);
        when(validator.validateUpdate(any(), any())).thenReturn(mockApplication);
        when(validator.validateIfPriorityHasBeenSet(any(), any())).thenReturn(true);
        when(repository.save(any())).thenReturn(mockApplicationEntity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(mockApplication);

        // when
        Application response = service.updateApplication(mockApplication);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        verify(validator).validate(any());
        verify(validator).validateUpdate(any(), any());
        verify(validator).validateIfPriorityHasBeenSet(any(), any());
    }

    @Test
    public void givenRequest_whenUpdateApplication_withExternalIdThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        Application mockApplication = Application.builder().id(id).teamId(1L).externalId("APPID-123").build();
        ApplicationEntity mockApplicationEntity = ApplicationEntity.builder().id(id).build();

        when(teamService.getTeam(mockApplication.getTeamId())).thenReturn(Team.builder().name("Team-1").build());
        when(repository.findById(id)).thenReturn(Optional.of(mockApplicationEntity));
        when(validator.validate(any())).thenReturn(mockApplication);
        when(validator.validateUpdate(any(), any())).thenReturn(mockApplication);
        when(validator.validateIfPriorityHasBeenSet(any(), any())).thenReturn(false);
        when(validator.validateIfExternalIdHasBeenSet(any(), any())).thenReturn(true);
        when(repository.save(any())).thenReturn(mockApplicationEntity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(mockApplication);

        // when
        Application response = service.updateApplication(mockApplication);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
        verify(validator).validate(any());
        verify(validator).validateUpdate(any(), any());
        verify(validator).validateIfPriorityHasBeenSet(any(), any());
        verify(validator).validateIfExternalIdHasBeenSet(any(), any());
        verify(validator).validateExternalId(any(), any());
    }

    @Test
    public void givenRequest_whenUpdateApplicationNotFound_ThenReturnException() {
        // given
        Long id = new Random().nextLong();

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> service.updateApplication(Application.builder().id(id).build()));

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetPaginatedApplications_ThenReturnResponse() {
        Long id1 = new Random().nextLong();
        Long id2 = new Random().nextLong();
        when(viewRepository.findAll(ArgumentMatchers.<Specification<ApplicationViewEntity>>any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                ApplicationViewEntity.builder().id(id1).name("App1").build(),
                ApplicationViewEntity.builder().id(id2).name("App2").build())));
        when(viewMapper.toDto(any(ApplicationViewEntity.class), any(), any()))
                .thenReturn(ApplicationView.builder().id(id1).name("App1").build())
                .thenReturn(ApplicationView.builder().id(id2).name("App2").build());

        // when
        PaginatedApplicationResponse response = service.getApplications(PaginationInput.builder().pageNumber(1).pageSize(2).build(),
                ApplicationSearchFilter.builder().name("App1").names(List.of("App2")).build(), null);

        assertThat(response).isNotNull();
        assertThat(response.getApplications().size()).isEqualTo(2);
        MatcherAssert.assertThat(response.getApplications(), contains(
                hasProperty("name", is("App1")),
                hasProperty("name", is("App2"))
        ));
    }

    @Test
    public void whenGetApplication_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        when(repository.findById(any())).thenReturn(Optional.of(ApplicationEntity.builder().id(id).name("App1").build()));
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(Application.builder().id(id).name("App1").build());

        // when
        Application response = service.getApplication(id);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetApplicationNotFound_ThenReturnException() {
        // given
        Long id = new Random().nextLong();

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> service.getApplication(id));

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetApplicationView_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        when(viewRepository.findById(any())).thenReturn(Optional.of(ApplicationViewEntity.builder().id(id).name("App1").build()));
        when(viewMapper.toDto(any(ApplicationViewEntity.class), any(), any())).thenReturn(ApplicationView.builder().id(id).name("App1").build());

        // when
        ApplicationView response = service.getApplicationView(id, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetApplicationViewNotFound_ThenReturnException() {
        // given
        Long id = new Random().nextLong();

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> service.getApplicationView(id, null));

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetApplicationViewByFilter_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        when(viewRepository.findOne(ArgumentMatchers.<Specification<ApplicationViewEntity>>any())).thenReturn(Optional.of(ApplicationViewEntity.builder().id(id).name("App1").build()));
        when(viewMapper.toDto(any(ApplicationViewEntity.class), any(), any())).thenReturn(ApplicationView.builder().id(id).name("App1").build());

        // when
        ApplicationView response = service.getApplicationView(ApplicationSearchFilter.builder().name("App1").build(), null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    public void whenGetApplicationViewByFilterNotFound_ThenReturnException() {
        // when
        ApiException thrown = assertThrows(ApiException.class, () -> service.getApplicationView(ApplicationSearchFilter.builder().name("App1").build(), null));

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenDeleteApplication_ThenReturnResponse() throws ApiException {
        // given
        Long id = new Random().nextLong();
        ApplicationEntity applicationEntity = ApplicationEntity.builder().id(id).name("App1").build();
        when(repository.findById(any())).thenReturn(Optional.of(applicationEntity));

        // when
        service.deleteApplication(id);

        // then
        verifyAuditEntity(AuditAction.DELETE, applicationEntity);
    }

    @Test
    public void whenDeleteApplicationNotFound_ThenReturnException() {
        // given
        Long id = new Random().nextLong();

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> service.deleteApplication(id));

        // then
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void whenGetApplicationsForTeam_ThenReturnResponse() {
        // given
        Long teamId = new Random().nextLong();
        Long id1 = new Random().nextLong();
        Long id2 = new Random().nextLong();
        when(repository.findAllByTeamId(teamId)).thenReturn(List.of(ApplicationEntity.builder().id(id1).name("App1").build(),
                ApplicationEntity.builder().id(id2).name("App2").build()));
        when(mapper.toDto(any(ApplicationEntity.class)))
                .thenReturn(Application.builder().id(id1).name("App1").build())
                .thenReturn(Application.builder().id(id2).name("App2").build());

        // when
        List<Application> response = service.getApplicationsForTeam(teamId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.size()).isGreaterThan(1);
    }

    @Test
    public void whenGetApplicationViewsByExternalId_ThenReturnResponse() {
        // given
        String externalId = "externalId";
        List<ApplicationViewEntity> viewEntities = List.of(
                ApplicationViewEntity.builder().id(1L).name("App1").externalId(externalId).build(),
                ApplicationViewEntity.builder().id(2L).name("App2").externalId(externalId).build()
        );
        when(viewRepository.findAllByApplicationExternalId(externalId)).thenReturn(viewEntities);
        when(viewMapper.toDto(any(ApplicationViewEntity.class), any(), any()))
                .thenReturn(ApplicationView.builder().id(1L).name("App1").externalId(externalId).build())
                .thenReturn(ApplicationView.builder().id(2L).name("App2").externalId(externalId).build());

        // when
        List<ApplicationView> result = service.getApplicationViewsByExternalId(externalId, null);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void givenEmptyExternalId_whenGetApplicationViewsByExternalId_ThenReturnException() {
        // when
        BadRequestException exception = assertThrows(BadRequestException.class, () -> service.getApplicationViewsByExternalId(null, null));

        assertThat(exception).isNotNull();
        assertThat(exception.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_REQUIRED);
    }

    @Test
    public void createApplication_thenReturnSuccess() {
        // given
        String applicationName = "App1";
        Long teamId = new Random().nextLong();
        Application request = Application.builder().name(applicationName).teamId(teamId).build();
        String teamName = "Team1";
        when(validator.validate(request)).thenReturn(request);
        when(validator.validateIfComplianceTypeHasBeenSet(request, null)).thenReturn(false);
        when(validator.validateIfPriorityHasBeenSet(request, null)).thenReturn(false);
        when(validator.validateIfExternalIdHasBeenSet(request, null)).thenReturn(false);
        ;when(validator.validateIfAiEnabledHasBeenSet(request, null)).thenReturn(false);
        when(repository.findAllByName(applicationName)).thenReturn(Collections.emptyList());
        ApplicationPriority defaultPriority = new ApplicationPriority(3,"Tier 3 (Business operational applications)", true);
        when(applicationPriorities.stream()).thenReturn(Stream.of(defaultPriority));
        ApplicationEntity entity = ApplicationEntity.builder().name(applicationName).teamId(teamId)
                .priority(defaultPriority.priority).build();
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(any(ApplicationEntity.class))).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);
        Team team = Team.builder().name(teamName).build();
        when(teamService.getTeamByName(teamName)).thenReturn(team);

        // when
        Application application = service.createApplication(request, teamName);

        // then
        assertThat(application).isNotNull();
        assertThat(application.getPriority()).isEqualTo(defaultPriority.priority);
        verify(validator).validateCloudTenant(request, team);
        verify(validator).validateNewName(request);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
        verifyNoMoreInteractions(teamService);
        verifyNoMoreInteractions(validator);
    }

    @Test
    public void createApplication_withExternalIdValidation_thenReturnSuccess() {
        // given
        String applicationName = "App1";
        Long teamId = new Random().nextLong();
        String externalId = "ExternalId1";
        Application request = Application.builder().name(applicationName).teamId(teamId).externalId(externalId).build();
        String teamName = "Team1";
        when(validator.validate(request)).thenReturn(request);
        when(validator.validateIfComplianceTypeHasBeenSet(request, null)).thenReturn(false);
        when(validator.validateIfPriorityHasBeenSet(request, null)).thenReturn(false);
        when(validator.validateIfExternalIdHasBeenSet(request, null)).thenReturn(true);
        when(validator.validateIfAiEnabledHasBeenSet(request, null)).thenReturn(false);
        Team team = Team.builder().id(teamId).name(teamName).build();
        when(teamService.getTeamByName(teamName)).thenReturn(team);
        when(repository.findAllByName(applicationName)).thenReturn(Collections.emptyList());
        ApplicationPriority defaultPriority = new ApplicationPriority(3,"Tier 3 (Business operational applications)", true);
        when(applicationPriorities.stream()).thenReturn(Stream.of(defaultPriority));
        ApplicationEntity entity = ApplicationEntity.builder().name(applicationName).teamId(teamId)
                .priority(defaultPriority.priority).build();
        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(any(ApplicationEntity.class))).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);
        when(teamService.getTeamByName(teamName)).thenReturn(team);

        // when
        Application application = service.createApplication(request, teamName);

        // then
        assertThat(application).isNotNull();
        assertThat(application.getPriority()).isEqualTo(defaultPriority.priority);
        verify(validator).validateCloudTenant(request, team);
        verify(validator).validateNewName(request);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
        verify(validator).validateExternalId(request, team);
        verifyNoMoreInteractions(teamService);
        verifyNoMoreInteractions(validator);
    }

    @Test
    public void updateApplication_thenReturnSuccess() {
        // given
        String applicationName = "App1";
        Long teamId = new Random().nextLong();
        Long applicationId = new Random().nextLong();
        String externalId = "ExternalId1";
        Application request = Application.builder().id(applicationId).name(applicationName).teamId(teamId).externalId(externalId).build();
        String teamName = "Team1";
        when(validator.validate(request)).thenReturn(request);
        ApplicationPriority defaultPriority = new ApplicationPriority(3,"Tier 3 (Business operational applications)", true);
        ApplicationEntity entity = ApplicationEntity.builder().id(applicationId).name(applicationName).teamId(teamId)
                .priority(defaultPriority.priority).build();
        when(repository.findById(applicationId)).thenReturn(Optional.of(entity));
        when(repository.findAllByName(applicationName)).thenReturn(List.of(entity));

        when(validator.validateUpdate(request, entity)).thenReturn(request);
        when(validator.validateIfComplianceTypeHasBeenSet(request, entity)).thenReturn(false);
        when(validator.validateIfPriorityHasBeenSet(request, entity)).thenReturn(false);
        when(validator.validateIfExternalIdHasBeenSet(request, entity)).thenReturn(false);
        when(validator.validateIfAiEnabledHasBeenSet(request, entity)).thenReturn(false);
        when(repository.save(any(ApplicationEntity.class))).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);
        Team team = Team.builder().name(teamName).build();
        when(teamService.getTeamByName(teamName)).thenReturn(team);

        // when
        Application application = service.updateApplication(request, teamName);

        // then
        assertThat(application).isNotNull();
        verify(validator).validateIfInformationClassificationHasChanged(request, entity);
        verify(validator).validateCloudTenant(request, team);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
        verifyNoMoreInteractions(validator);
        verifyNoInteractions(applicationPriorities);
    }

    @Test
    public void updateApplication_withExternalIdValidation_thenReturnSuccess() {
        // given
        String applicationName = "App1";
        Long teamId = new Random().nextLong();
        Long applicationId = new Random().nextLong();
        Application request = Application.builder().id(applicationId).name(applicationName).teamId(teamId).build();
        String teamName = "Team1";
        when(validator.validate(request)).thenReturn(request);
        ApplicationPriority defaultPriority = new ApplicationPriority(3,"Tier 3 (Business operational applications)", true);
        ApplicationEntity entity = ApplicationEntity.builder().id(applicationId).name(applicationName).teamId(teamId)
                .priority(defaultPriority.priority).build();
        when(repository.findById(applicationId)).thenReturn(Optional.of(entity));
        when(repository.findAllByName(applicationName)).thenReturn(List.of(entity));

        when(validator.validateUpdate(request, entity)).thenReturn(request);
        when(validator.validateIfComplianceTypeHasBeenSet(request, entity)).thenReturn(false);
        when(validator.validateIfPriorityHasBeenSet(request, entity)).thenReturn(false);
        when(validator.validateIfAiEnabledHasBeenSet(request, entity)).thenReturn(false);
        when(validator.validateIfExternalIdHasBeenSet(request, entity)).thenReturn(true);
        Team team = Team.builder().id(teamId).name(teamName).build();
        when(teamService.getTeamByName(teamName)).thenReturn(team);
        when(repository.save(any(ApplicationEntity.class))).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);
        when(teamService.getTeamByName(teamName)).thenReturn(team);

        // when
        Application application = service.updateApplication(request, teamName);

        // then
        assertThat(application).isNotNull();
        verify(validator).validateCloudTenant(request, team);
        verify(validator).validateExternalId(request, team);
        verify(validator).validateIfInformationClassificationHasChanged(request, entity);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
        verifyNoInteractions(applicationPriorities);
        verifyNoMoreInteractions(validator);
    }

    @Test
    public void updateApplication_withExternalIdChange_whenApiForgeResourcesExist_thenReturnException() {
        // given
        String applicationName = "App1";
        Long teamId = new Random().nextLong();
        Long applicationId = new Random().nextLong();
        Application request = Application.builder().id(applicationId).name(applicationName).teamId(teamId).externalId("ExternalId1").build();
        String teamName = "Team1";
        ApplicationEntity entity = ApplicationEntity.builder().id(applicationId).name(applicationName).teamId(teamId).build();
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(applicationId)).thenReturn(Optional.of(entity));
        when(repository.findAllByName(applicationName)).thenReturn(List.of(entity));
        when(validator.validateUpdate(request, entity)).thenReturn(request);
        when(validator.validateIfExternalIdHasBeenSet(request, entity)).thenReturn(true);
        when(teamService.getTeamByName(teamName)).thenReturn(Team.builder().id(teamId).name(teamName).build());
        willThrow(new ResourceConstraintException(ExceptionReason.APPLICATION_EXTERNAL_ID_API_FORGE_OWNERSHIP_CONSTRAINT, List.of("owns API:api1")))
                .given(apiResourceConstraintService).validateNoApiResourcesExistForExternalIdChange(applicationName);

        // when
        ApiException thrown = assertThrows(ApiException.class, () -> service.updateApplication(request, teamName));

        // then
        assertThat(thrown.getReason()).isEqualTo(ExceptionReason.APPLICATION_EXTERNAL_ID_API_FORGE_OWNERSHIP_CONSTRAINT);
        verify(apiResourceConstraintService).validateNoApiResourcesExistForExternalIdChange(applicationName);
        verify(repository, org.mockito.Mockito.never()).save(any(ApplicationEntity.class));
        verifyNoInteractions(auditService);
    }

    @Test
    public void forceUpdateExternalId() {
        // given
        String externalId = "externalId";
        Application request = Application.builder().id(100L).name("app1").externalId(externalId).build();
        Team team = Team.builder().id(1L).name("Team-1").build();
        ApplicationEntity existingEntity = ApplicationEntity.builder().id(100L).name("app1").externalId("externalId-old").build();
        when(repository.findById(request.getId())).thenReturn(Optional.of(existingEntity));
        ApplicationEntity updatedEntity = ApplicationEntity.builder().id(100L).name("app1").externalId(externalId).build();
        when(repository.save(existingEntity)).thenReturn(updatedEntity);
        Application updatedApplication = Application.builder().id(100L).name("app1").externalId(externalId).build();
        when(mapper.toDto(updatedEntity)).thenReturn(updatedApplication);

        // when
        Application result = service.forceUpdateExternalId(request, team);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getExternalId()).isEqualTo(externalId);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
        verify(validator).validateExternalId(request, team);
    }

    @Test
    public void forceUpdateExternalId_appNotFound() {
        // given
        Application request = Application.builder().id(100L).name("app1").externalId("externalId").build();
        Team team = Team.builder().id(1L).name("Team-1").build();
        when(repository.findById(request.getId())).thenReturn(Optional.empty());

        // when
        assertThrows(RecordNotFoundException.class, () -> service.forceUpdateExternalId(request, team));

        // then
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(validator);
        verifyNoInteractions(auditService);
        verifyNoInteractions(mapper);
    }

    @Test
    public void getApplications_requestsVersionedClientIdsFromTeams() {
        ApplicationViewEntity app1 = ApplicationViewEntity.builder().id(1L).name("App1")
                .team(teamWithClients(authClient("client-1", 1))).build();
        ApplicationViewEntity app2 = ApplicationViewEntity.builder().id(2L).name("App2")
                .team(teamWithClients(authClient("client-2", 2))).build();
        when(viewRepository.findAll(ArgumentMatchers.<Specification<ApplicationViewEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(app1, app2)));

        service.getApplications(PaginationInput.builder().pageNumber(1).pageSize(2).build(),
                ApplicationSearchFilter.builder().build(), null);

        assertThat(capturedClientIds(registryAuthService, null)).containsExactly("client-1", "client-2-v2");
    }

    @Test
    public void getOAuthClients_skipsApplicationsWithoutTeamOrClients() {
        ApplicationViewEntity noTeam = ApplicationViewEntity.builder().id(1L).build();
        ApplicationViewEntity noClients = ApplicationViewEntity.builder().id(2L)
                .team(TeamViewEntity.builder().build()).build();
        ApplicationViewEntity withClient = ApplicationViewEntity.builder().id(3L)
                .team(teamWithClients(authClient("client-3", 1))).build();

        service.getOAuthClients(List.of(noTeam, noClients, withClient), null);

        assertThat(capturedClientIds(registryAuthService, null)).containsExactly("client-3");
    }

    @Test
    public void mapToView_whenEntityNull_returnsNullWithoutFetchingClients() {
        assertThat(service.mapToView((ApplicationViewEntity) null, null)).isNull();
        verifyNoInteractions(registryAuthService, viewMapper);
    }

    // --- legacy compliance types turned off ---

    private void givenLegacyComplianceTypesAreOff() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "legacyComplianceTypesEnabled", false);
    }

    private ApplicationEntity givenStoredApplicationWithTags(Long id, InformationClassification stored) {
        ApplicationEntity entity = ApplicationEntity.builder().id(id).complianceTypes(List.of("pci"))
                .informationClassification(stored).build();
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(validator.validate(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(validator.validateUpdate(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(Application.builder().id(id).build());
        return entity;
    }

    @Test
    public void legacyComplianceTypesOff_createStoresTheSentClassificationWithoutCheckingItAgainstTags() {
        givenLegacyComplianceTypesAreOff();
        Application request = Application.builder().name("App1").teamId(1L)
                .informationClassification(InformationClassification.RESTRICTED).build();
        ApplicationEntity entity = ApplicationEntity.builder().name("App1").complianceTypes(List.of("pci")).build();
        when(teamService.getTeam(1L)).thenReturn(Team.builder().name("Team-1").build());
        when(teamService.getTeamByName(any())).thenReturn(Team.builder().id(1L).name("Team-1").build());
        when(validator.validate(any())).thenReturn(request);
        when(cloudTenantService.getDefaultCloud()).thenReturn("gcp");
        when(cloudTenantService.getDefaultTenant()).thenReturn("general");
        when(applicationPriorities.stream()).thenReturn(Stream.of(new ApplicationPriority(3, "Tier 3", true)));
        when(mapper.toEntity(any(Application.class))).thenReturn(entity);
        when(repository.save(any())).thenReturn(entity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(request);

        service.createApplication(request);

        // RESTRICTED with no tags would be rejected as a mismatch while the tags are on
        ArgumentCaptor<Application> mapped = ArgumentCaptor.forClass(Application.class);
        verify(mapper).toEntity(mapped.capture());
        assertThat(mapped.getValue().getInformationClassification()).isEqualTo(InformationClassification.RESTRICTED);
        assertThat(entity.getComplianceTypes()).as("the tags are never written").isNull();
        // with the tags off, the classification is what asks for more than the default, so it needs the owner check
        verify(validator).validateIfInformationClassificationHasBeenSet(any());
    }

    @Test
    public void legacyComplianceTypesOff_createDefaultsAnOmittedClassificationToProprietary() {
        givenLegacyComplianceTypesAreOff();
        Application request = Application.builder().name("App1").teamId(1L).build();
        ApplicationEntity entity = ApplicationEntity.builder().name("App1").build();
        when(teamService.getTeam(1L)).thenReturn(Team.builder().name("Team-1").build());
        when(teamService.getTeamByName(any())).thenReturn(Team.builder().id(1L).name("Team-1").build());
        when(validator.validate(any())).thenReturn(request);
        when(cloudTenantService.getDefaultCloud()).thenReturn("gcp");
        when(cloudTenantService.getDefaultTenant()).thenReturn("general");
        when(applicationPriorities.stream()).thenReturn(Stream.of(new ApplicationPriority(3, "Tier 3", true)));
        when(mapper.toEntity(any(Application.class))).thenReturn(entity);
        when(repository.save(any())).thenReturn(entity);
        when(mapper.toDto(any(ApplicationEntity.class))).thenReturn(request);

        service.createApplication(request);

        ArgumentCaptor<Application> mapped = ArgumentCaptor.forClass(Application.class);
        verify(mapper).toEntity(mapped.capture());
        assertThat(mapped.getValue().getInformationClassification()).isEqualTo(InformationClassification.PROPRIETARY);
    }

    @Test
    public void legacyComplianceTypesOff_anUpdateWithoutTagsLeavesTheStoredTagsAndClassificationAlone() {
        givenLegacyComplianceTypesAreOff();
        Long id = 7L;
        ApplicationEntity stored = givenStoredApplicationWithTags(id, InformationClassification.RESTRICTED);
        // the hidden field arrives empty, which with the tags on would mean "clear them"
        Application request = Application.builder().id(id).description("changed").build();

        service.updateApplication(request);

        assertThat(stored.getDescription()).isEqualTo("changed");
        assertThat(stored.getComplianceTypes()).containsExactly("pci");
        assertThat(stored.getInformationClassification()).isEqualTo(InformationClassification.RESTRICTED);
        // missing tags would look like a change to the stored ones and demand the owner-only permission
        verify(validator, never()).validateIfComplianceTypeHasBeenSet(any(), any());
    }

    @Test
    public void legacyComplianceTypesOff_anUpdateStoresTheSentClassificationAndKeepsTheTagsFrozen() {
        givenLegacyComplianceTypesAreOff();
        Long id = 7L;
        ApplicationEntity stored = givenStoredApplicationWithTags(id, InformationClassification.RESTRICTED);
        Application request = Application.builder().id(id)
                .informationClassification(InformationClassification.PUBLIC).build();

        service.updateApplication(request);

        // PUBLIC cannot come from the tags, so with them on this would have been a mismatch
        assertThat(stored.getInformationClassification()).isEqualTo(InformationClassification.PUBLIC);
        assertThat(stored.getComplianceTypes()).containsExactly("pci");
        verify(validator).validateIfInformationClassificationHasChanged(any(), any());
    }

    private void verifyAuditEntity(AuditAction action, ApplicationEntity application) {
        verify(auditService).publish(auditEntryArgCaptor.capture(), changeEventDetailsCaptor.capture());
        AuditEntry auditEntry = auditEntryArgCaptor.getValue();
        assertThat(auditEntry.getActionType()).isEqualTo(action);
        assertThat(auditEntry.getSubjectEntity()).isNotNull();
        assertThat(auditEntry.getSubjectEntity())
                .extracting(AuditEntity::getEntityType, AuditEntity::getId, AuditEntity::getName)
                .containsExactly(AuditEntityType.APPLICATION, application.getId(), application.getName());
        ChangeEventDetails changeEventDetails = changeEventDetailsCaptor.getValue();
        assertThat(changeEventDetails).isNotNull();
        assertThat(changeEventDetails.getOldValues()).isNotNull();
        assertThat(changeEventDetails.getNewValues()).isNotNull();
        if (AuditAction.UPDATE == action) {
            assertThat(changeEventDetails.getContext()).isNotNull();
        } else {
            assertThat(changeEventDetails.getContext()).isNull();
        }
    }
}
