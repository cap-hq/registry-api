// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.mapper.LineBusinessViewMapper;
import com.cvshealth.cap.registry.model.LineBusinessManagerView;
import com.cvshealth.cap.registry.model.LineBusinessView;
import com.cvshealth.cap.registry.model.audit.AuditEntry;
import com.cvshealth.cap.registry.model.audit.ChangeEventDetails;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.RecordNotFoundException;
import com.cvshealth.cap.registry.persistent.LineBusinessManagerEntity;
import com.cvshealth.cap.registry.persistent.LineBusinessViewEntity;
import com.cvshealth.cap.registry.repository.LineBusinessViewRepository;
import com.cvshealth.cap.registry.service.audit.AuditService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LineBusinessServiceTests {
    @InjectMocks
    private LineBusinessService service;

    @Mock
    private LineBusinessViewRepository repository;
    @Mock
    private LineBusinessServiceValidator validator;
    @Mock
    private LineBusinessViewMapper mapper;
    @Mock
    private AuditService auditService;

    private LineBusinessManagerEntity MANAGER_ENTITY;
    private LineBusinessManagerView MANAGER_VIEW;
    private LineBusinessViewEntity LOB_ENTITY;
    private LineBusinessView LOB_VIEW;

    @BeforeEach
    void setUp() {
        MANAGER_ENTITY = LineBusinessManagerEntity.builder()
                .managerExternalId("managerExternalId").managerName("managerName").build();

        MANAGER_VIEW = LineBusinessManagerView.builder()
                .managerExternalId("managerExternalId").managerName("managerName").build();

        LOB_ENTITY = LineBusinessViewEntity.builder().id(1L)
                .name("name").managers(new ArrayList<>(List.of(MANAGER_ENTITY))).build();

        LOB_VIEW = LineBusinessView.builder().id(1L)
                .name("name").managers(List.of(MANAGER_VIEW)).build();
    }

    @Test
    public void given_existingId_whenGetLineBusiness_byId_thenReturnLOB() throws ApiException {
        // given
        Long id = 1L;
        when(repository.findById(id)).thenReturn(Optional.of(LOB_ENTITY));
        when(mapper.toDto(LOB_ENTITY)).thenReturn(LOB_VIEW);

        // when
        LineBusinessView result = service.getLineBusiness(id);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void given_unknownId_whenGetLineBusiness_byId_thenThrowError() throws ApiException {
        // given
        Long id = 1L;
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when
        ApiException exception = assertThrows(RecordNotFoundException.class, () -> service.getLineBusiness(id));

        // then
        assertThat(exception).isNotNull();
    }

    @Test
    public void given_existingId_whenGetLineBusinessByName_thenReturnLOB() throws ApiException {
        // given
        String name = LOB_ENTITY.getName();
        when(repository.findByName(name)).thenReturn(Optional.of(LOB_ENTITY));
        when(mapper.toDto(LOB_ENTITY)).thenReturn(LOB_VIEW);

        // when
        LineBusinessView result = service.getLineBusinessByName(name);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void given_unknown_whenGetLineBusinessByName_thenThrowError() {
        // given
        String name = LOB_ENTITY.getName();
        when(repository.findByName(name)).thenReturn(Optional.empty());

        // when
        ApiException exception = assertThrows(RecordNotFoundException.class, () -> service.getLineBusinessByName(name));

        // then
        assertThat(exception).isNotNull();
    }

    @Test
    public void whenGetLinesOfBusiness_thenReturnAll() throws ApiException {
        // given
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(LOB_ENTITY));
        when(mapper.toDto(LOB_ENTITY)).thenReturn(LOB_VIEW);

        // when
        List<LineBusinessView> results = service.getLinesOfBusiness();

        // then
        assertThat(results).isNotEmpty();
    }

    @Test
    public void givenExistingLOB_whenCreateLineOfBusiness_thenThrowError() throws ApiException {
        // given
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findByName(request.getName())).thenReturn(Optional.of(LOB_ENTITY));

        // when
        ApiException exception = assertThrows(BadRequestException.class, () -> service.createLineOfBusiness(request));

        // then
        assertThat(exception).isNotNull();
    }

    @Test
    public void givenNewLOB_whenCreateLineOfBusiness_thenReturnLOB() throws ApiException {
        // given
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findByName(request.getName())).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(LOB_ENTITY);
        when(repository.saveAndFlush(LOB_ENTITY)).thenReturn(LOB_ENTITY);
        when(mapper.toDto(LOB_ENTITY)).thenReturn(LOB_VIEW);

        // when
        LineBusinessView result = service.createLineOfBusiness(request);

        // then
        assertThat(result).isNotNull();
        verify(validator).validateManagerExternalIds(request);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void givenLOBNotFound_whenUpdateLineOfBusiness_thenThrowError() throws ApiException {
        // given
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(request.getId())).thenReturn(Optional.empty());

        // when
        ApiException exception = assertThrows(RecordNotFoundException.class, () -> service.updateLineOfBusiness(request));

        // then
        assertThat(exception).isNotNull();
    }

    @Test
    public void givenUnchangedManagers_whenUpdateLineOfBusiness_thenUpdateFieldsOnly() throws ApiException {
        // given — same managers: only line_business table fields are updated, no intermediate flush
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(request.getId())).thenReturn(Optional.of(LOB_ENTITY));
        when(validator.validateUpdate(request, LOB_ENTITY)).thenReturn(request);
        when(repository.saveAndFlush(LOB_ENTITY)).thenReturn(LOB_ENTITY);
        when(mapper.toDto(LOB_ENTITY)).thenReturn(LOB_VIEW);

        // when
        LineBusinessView result = service.updateLineOfBusiness(request);

        // then
        assertThat(result).isNotNull();
        verify(repository).saveAndFlush(LOB_ENTITY);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void givenChangedManagers_whenUpdateLineOfBusiness_thenApplyDelta() throws ApiException {
        // given — different manager IDs: old manager is removed, new one is added without touching unaffected rows
        String newManagerId = "new-manager-id";
        LineBusinessView request = LineBusinessView.builder().id(1L).name("name")
                .managers(List.of(LineBusinessManagerView.builder().managerExternalId(newManagerId).managerName("New Manager").build()))
                .build();
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(1L)
                .name("name").managers(new ArrayList<>(List.of(MANAGER_ENTITY))).build();
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(request.getId())).thenReturn(Optional.of(entity));
        when(validator.validateUpdate(request, entity)).thenReturn(request);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);

        // when
        LineBusinessView result = service.updateLineOfBusiness(request);

        // then — old manager removed, new manager added
        assertThat(result).isNotNull();
        assertThat(entity.getManagers()).hasSize(1);
        assertThat(entity.getManagers().get(0).getManagerExternalId()).isEqualTo(newManagerId);
        verify(repository).saveAndFlush(entity);
        verify(repository, never()).save(any());
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void givenAddingSecondManager_whenUpdateLineOfBusiness_thenExistingManagerRetained() throws ApiException {        // given — existing manager is kept; only the new manager is added (delta, no clear+re-insert)
        LineBusinessManagerView secondManagerView = LineBusinessManagerView.builder()
                .managerExternalId("secondManagerId").managerName("Second Manager").build();
        LineBusinessView request = LineBusinessView.builder().id(1L).name("name")
                .managers(List.of(MANAGER_VIEW, secondManagerView))
                .build();
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(1L)
                .name("name").managers(new ArrayList<>(List.of(MANAGER_ENTITY))).build();
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(request.getId())).thenReturn(Optional.of(entity));
        when(validator.validateUpdate(request, entity)).thenReturn(request);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);

        // when
        LineBusinessView result = service.updateLineOfBusiness(request);

        // then — original manager still present (same object, not re-created), second manager added
        assertThat(result).isNotNull();
        assertThat(entity.getManagers()).hasSize(2);
        assertThat(entity.getManagers()).contains(MANAGER_ENTITY);
        assertThat(entity.getManagers().stream().map(LineBusinessManagerEntity::getManagerExternalId))
                .containsExactlyInAnyOrder("managerExternalId", "secondManagerId");
        verify(repository).saveAndFlush(entity);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void givenManagerNameChanged_whenUpdateLineOfBusiness_thenManagerNameRefreshedInPlace() throws ApiException {
        // given — same manager ID, but with an updated display name
        String updatedName = "Updated Manager Name";
        LineBusinessView request = LineBusinessView.builder().id(1L).name("name")
                .managers(List.of(LineBusinessManagerView.builder()
                        .managerExternalId("managerExternalId").managerName(updatedName).build()))
                .build();
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(1L)
                .name("name").managers(new ArrayList<>(List.of(MANAGER_ENTITY))).build();
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(request.getId())).thenReturn(Optional.of(entity));
        when(validator.validateUpdate(request, entity)).thenReturn(request);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);

        // when
        LineBusinessView result = service.updateLineOfBusiness(request);

        // then — size unchanged, same entity object reused (not removed+re-added), name updated
        assertThat(result).isNotNull();
        assertThat(entity.getManagers()).hasSize(1);
        assertThat(entity.getManagers().get(0)).isSameAs(MANAGER_ENTITY);
        assertThat(MANAGER_ENTITY.getManagerName()).isEqualTo(updatedName);
        verify(repository).saveAndFlush(entity);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void givenManagerNameChangedWithDifferentCase_whenUpdateLineOfBusiness_thenManagerNameRefreshedInPlace() throws ApiException {
        // given — same manager ID in a different case, with an updated display name
        String updatedName = "Updated Manager Name";
        LineBusinessView request = LineBusinessView.builder().id(1L).name("name")
                .managers(List.of(LineBusinessManagerView.builder()
                        .managerExternalId("MANAGEREXTERNALID").managerName(updatedName).build()))
                .build();
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(1L)
                .name("name").managers(new ArrayList<>(List.of(MANAGER_ENTITY))).build();
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(request.getId())).thenReturn(Optional.of(entity));
        when(validator.validateUpdate(request, entity)).thenReturn(request);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(request);

        // when
        service.updateLineOfBusiness(request);

        // then — IDs matched case-insensitively; name updated in-place, no new entity added
        assertThat(entity.getManagers()).hasSize(1);
        assertThat(entity.getManagers().get(0)).isSameAs(MANAGER_ENTITY);
        assertThat(MANAGER_ENTITY.getManagerName()).isEqualTo(updatedName);
    }

    @Test
    public void givenManagerNameUnchanged_whenUpdateLineOfBusiness_thenManagerEntityNotMutated() throws ApiException {
        // given — same manager ID and same name; entity should not be mutated
        LineBusinessManagerEntity originalEntity = LineBusinessManagerEntity.builder()
                .managerExternalId("managerExternalId").managerName("managerName").build();
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(1L)
                .name("name").managers(new ArrayList<>(List.of(originalEntity))).build();
        when(validator.validate(LOB_VIEW)).thenReturn(LOB_VIEW);
        when(repository.findById(LOB_VIEW.getId())).thenReturn(Optional.of(entity));
        when(validator.validateUpdate(LOB_VIEW, entity)).thenReturn(LOB_VIEW);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(LOB_VIEW);

        // when
        service.updateLineOfBusiness(LOB_VIEW);

        // then — manager list unchanged, name still the same
        assertThat(entity.getManagers()).hasSize(1);
        assertThat(entity.getManagers().get(0)).isSameAs(originalEntity);
        assertThat(originalEntity.getManagerName()).isEqualTo("managerName");
    }

    @Test
    public void givenLobId_whenDeleteLineOfBusiness_thenSuccess() throws ApiException {
        // given
        Long id = LOB_VIEW.getId();
        when(repository.findById(id)).thenReturn(Optional.of(LOB_ENTITY));

        // when
        service.deleteLineOfBusiness(id);

        // then
        verify(repository).deleteById(id);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void givenUnknownLobId_whenDeleteLineOfBusiness_thenThrowError() throws ApiException {
        // given
        Long id = LOB_VIEW.getId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when
        ApiException exception = assertThrows(RecordNotFoundException.class, () -> service.deleteLineOfBusiness(id));

        // then
        assertThat(exception).isNotNull();
    }

    @Test
    public void giveLobName_whenDeleteLineOfBusinessByName_thenSuccess() throws ApiException {
        // given
        String name = LOB_VIEW.getName();
        when(repository.findByName(name)).thenReturn(Optional.of(LOB_ENTITY));

        // when
        service.deleteLineOfBusinessByName(name);

        // then
        verify(repository).delete(LOB_ENTITY);
        verify(auditService).publish(any(AuditEntry.class), any(ChangeEventDetails.class));
    }

    @Test
    public void givenUnknownLobName_whenDeleteLineOfBusinessByName_throwThrowError() throws ApiException {
        // given
        String name = LOB_VIEW.getName();
        when(repository.findByName(name)).thenReturn(Optional.empty());

        // when
        ApiException exception = assertThrows(RecordNotFoundException.class, () -> service.deleteLineOfBusinessByName(name));

        // then
        assertThat(exception).isNotNull();
    }

    // --- DataIntegrityViolationException handling: createLineOfBusiness ---

    @Test
    public void givenManagerConstraintViolationByConstraintName_whenCreateLineOfBusiness_thenThrowBadRequestException() throws ApiException {
        // given — ConstraintViolationException whose constraint name contains "lob_manager"
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findByName(request.getName())).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(LOB_ENTITY);
        ConstraintViolationException cve = new ConstraintViolationException("constraint", new SQLException(), "LOB_MANAGER_EXTERNAL_ID_UK");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("violation", cve));

        // when / then
        assertThrows(BadRequestException.class, () -> service.createLineOfBusiness(request));
    }

    @Test
    public void givenManagerConstraintViolationBySqlExceptionMessage_whenCreateLineOfBusiness_thenThrowBadRequestException() throws ApiException {
        // given — ConstraintViolationException with no constraint name but matching SQLException message
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findByName(request.getName())).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(LOB_ENTITY);
        SQLException sqlEx = new SQLException("Duplicate manager external line constraint");
        ConstraintViolationException cve = new ConstraintViolationException("constraint", sqlEx, null);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("violation", cve));

        // when / then
        assertThrows(BadRequestException.class, () -> service.createLineOfBusiness(request));
    }

    @Test
    public void givenManagerConstraintViolationBySqlException_whenCreateLineOfBusiness_thenThrowBadRequestException() throws ApiException {
        // given — plain SQLException (not wrapped in ConstraintViolationException) with matching message
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findByName(request.getName())).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(LOB_ENTITY);
        SQLException sqlEx = new SQLException("Duplicate manager external line constraint");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("violation", sqlEx));

        // when / then
        assertThrows(BadRequestException.class, () -> service.createLineOfBusiness(request));
    }

    @Test
    public void givenManagerConstraintViolationByThrowableMessage_whenCreateLineOfBusiness_thenThrowBadRequestException() throws ApiException {
        // given — generic RuntimeException with matching message in the cause chain
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findByName(request.getName())).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(LOB_ENTITY);
        RuntimeException cause = new RuntimeException("Duplicate manager external line value");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("violation", cause));

        // when / then
        assertThrows(BadRequestException.class, () -> service.createLineOfBusiness(request));
    }

    @Test
    public void givenUnrelatedDataIntegrityViolation_whenCreateLineOfBusiness_thenRethrowException() throws ApiException {
        // given — DataIntegrityViolationException unrelated to manager constraint
        LineBusinessView request = LOB_VIEW;
        when(validator.validate(request)).thenReturn(request);
        when(repository.findByName(request.getName())).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(LOB_ENTITY);
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("some other db error");
        when(repository.saveAndFlush(any())).thenThrow(unrelated);

        // when / then — the original exception is rethrown, not wrapped
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.createLineOfBusiness(request));
        assertThat(thrown).isSameAs(unrelated);
    }

    // --- DataIntegrityViolationException handling: updateLineOfBusiness ---

    @Test
    public void givenManagerConstraintViolationByConstraintName_whenUpdateLineOfBusiness_thenThrowBadRequestException() throws ApiException {
        // given
        LineBusinessView request = LOB_VIEW;
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(1L)
                .name("name").managers(new ArrayList<>(List.of(MANAGER_ENTITY))).build();
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(request.getId())).thenReturn(Optional.of(entity));
        when(validator.validateUpdate(request, entity)).thenReturn(request);
        ConstraintViolationException cve = new ConstraintViolationException("constraint", new SQLException(), "LOB_MANAGER_EXTERNAL_ID_UK");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("violation", cve));

        // when / then
        assertThrows(BadRequestException.class, () -> service.updateLineOfBusiness(request));
    }

    @Test
    public void givenUnrelatedDataIntegrityViolation_whenUpdateLineOfBusiness_thenRethrowException() throws ApiException {
        // given
        LineBusinessView request = LOB_VIEW;
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(1L)
                .name("name").managers(new ArrayList<>(List.of(MANAGER_ENTITY))).build();
        when(validator.validate(request)).thenReturn(request);
        when(repository.findById(request.getId())).thenReturn(Optional.of(entity));
        when(validator.validateUpdate(request, entity)).thenReturn(request);
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("some other db error");
        when(repository.saveAndFlush(any())).thenThrow(unrelated);

        // when / then
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.updateLineOfBusiness(request));
        assertThat(thrown).isSameAs(unrelated);
    }

}
