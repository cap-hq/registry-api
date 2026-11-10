// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.LineBusinessManagerView;
import com.cvshealth.cap.registry.model.LineBusinessView;
import com.cvshealth.cap.registry.model.User;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.LineBusinessManagerEntity;
import com.cvshealth.cap.registry.persistent.LineBusinessViewEntity;
import com.cvshealth.cap.registry.repository.LineBusinessManagerRepository;
import com.cvshealth.cap.registry.repository.LineBusinessViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LineBusinessServiceValidatorTests {
    @InjectMocks
    private LineBusinessServiceValidator validator;

    @Mock
    private AzureAdUserService azureAdUserService;
    @Mock
    private LineBusinessViewRepository repository;
    @Mock
    private LineBusinessManagerRepository managerRepository;

    private static final String MANAGER_ID = "managerExternalId";

    private LineBusinessView lobViewWithManagers(Long id, String name, String... managerIds) {
        List<LineBusinessManagerView> managers = new ArrayList<>();
        for (String mid : managerIds) {
            managers.add(LineBusinessManagerView.builder().managerExternalId(mid).build());
        }
        return LineBusinessView.builder().id(id).name(name).managers(managers).build();
    }

    private User userWithDisplayName(String externalId, String displayName) {
        User user = new User();
        user.setExternalId(externalId);
        user.setDisplayName(displayName);
        return user;
    }

    @Test
    public void givenValidObject_whenValidate_thenReturnRequest() throws ApiException {
        // given
        LineBusinessView request = lobViewWithManagers(1L, "name", MANAGER_ID);

        // when
        LineBusinessView result = validator.validate(request);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void givenNullObject_whenValidate_thenReturnError() throws ApiException {
        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validate(null));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.REQUEST_IS_EMPTY.label);
    }

    @Test
    public void givenNoName_whenValidate_thenReturnError() throws ApiException {
        // given
        LineBusinessView request = lobViewWithManagers(1L, null, MANAGER_ID);

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validate(request));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.BUSINESS_LINE_NAME_REQUIRED.label);
    }

    @Test
    public void validateName() throws ApiException {
        // when
        assertDoesNotThrow(() -> validator.validateName("Lob-Test, _0123"));
        assertThrows(BadRequestException.class, () -> validator.validateName("Lob."));
        assertThrows(BadRequestException.class, () -> validator.validateName("Lob!"));
        assertThrows(BadRequestException.class, () -> validator.validateName("Lob;"));
        assertThrows(BadRequestException.class, () -> validator.validateName("Lob:"));
        assertThrows(BadRequestException.class, () -> validator.validateName("Lob#"));
        assertThrows(BadRequestException.class, () -> validator.validateName("L&ob"));
    }

    @Test
    public void validateDescription() throws ApiException {
        // when
        assertDoesNotThrow(() -> validator.validateDescription("A&b.-Test_123; (#tag):"));
        assertThrows(BadRequestException.class, () -> validator.validateDescription("Lob@1"));
        assertThrows(BadRequestException.class, () -> validator.validateDescription("[Lob]"));
        assertThrows(BadRequestException.class, () -> validator.validateDescription("9Lob"));
        assertThrows(BadRequestException.class, () -> validator.validateDescription("Lob!"));
    }

    @Test
    public void givenNameWithInvalidSpecialChars_whenValidate_thenReturnError() throws ApiException {
        // given
        LineBusinessView request = lobViewWithManagers(1L, "Lob(Test)!", MANAGER_ID);

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validate(request));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.BUSINESS_LINE_NAME_INVALID.label);
    }

    @Test
    public void givenEmptyManagerList_whenValidate_thenReturnError() throws ApiException {
        // given
        LineBusinessView request = LineBusinessView.builder().id(1L).name("name").managers(List.of()).build();

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validate(request));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.BUSINESS_LINE_MANAGER_EXTERNAL_ID_REQUIRED.label);
    }

    @Test
    public void givenNullManagerList_whenValidate_thenReturnError() throws ApiException {
        // given
        LineBusinessView request = LineBusinessView.builder().id(1L).name("name").managers(null).build();

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validate(request));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.BUSINESS_LINE_MANAGER_EXTERNAL_ID_REQUIRED.label);
    }

    @Test
    public void givenValidRequest_whenValidateManagerExternalIds_thenSuccess() throws ApiException {
        // given
        LineBusinessView request = lobViewWithManagers(1L, "name", MANAGER_ID);
        when(managerRepository.findByManagerExternalIdIgnoreCase(MANAGER_ID)).thenReturn(Optional.empty());
        when(azureAdUserService.findById(MANAGER_ID)).thenReturn(Optional.of(userWithDisplayName(MANAGER_ID, "Manager Name")));

        // when
        validator.validateManagerExternalIds(request);

        // then
        assertThat(request.getManagers().get(0).getManagerName()).isEqualTo("Manager Name");
    }

    @Test
    public void givenMultipleValidManagers_whenValidateManagerExternalIds_thenSuccess() throws ApiException {
        // given
        LineBusinessView request = lobViewWithManagers(1L, "name", "mgr-1", "mgr-2");
        when(managerRepository.findByManagerExternalIdIgnoreCase("mgr-1")).thenReturn(Optional.empty());
        when(managerRepository.findByManagerExternalIdIgnoreCase("mgr-2")).thenReturn(Optional.empty());
        when(azureAdUserService.findById("mgr-1")).thenReturn(Optional.of(userWithDisplayName("mgr-1", "Manager One")));
        when(azureAdUserService.findById("mgr-2")).thenReturn(Optional.of(userWithDisplayName("mgr-2", "Manager Two")));

        // when
        validator.validateManagerExternalIds(request);

        // then
        assertThat(request.getManagers()).hasSize(2);
        assertThat(request.getManagers().get(0).getManagerName()).isEqualTo("Manager One");
        assertThat(request.getManagers().get(1).getManagerName()).isEqualTo("Manager Two");
    }

    @Test
    public void givenDuplicateManagerInRequest_whenValidateManagerExternalIds_thenThrowError() throws ApiException {
        // given
        LineBusinessView request = lobViewWithManagers(1L, "name", MANAGER_ID, MANAGER_ID);

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validateManagerExternalIds(request));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getReason()).isEqualTo(ExceptionReason.BUSINESS_LINE_MANAGER_DUPLICATE_IN_REQUEST);
    }

    @Test
    public void givenManagerAlreadyAssignedToAnotherLob_whenValidateManagerExternalIds_thenThrowError() throws ApiException {
        // given
        LineBusinessView request = lobViewWithManagers(1L, "name", MANAGER_ID);
        LineBusinessViewEntity otherLob = LineBusinessViewEntity.builder().id(2L).name("Other LOB").build();
        LineBusinessManagerEntity existingAssignment = LineBusinessManagerEntity.builder()
                .managerExternalId(MANAGER_ID).managerName("Manager Name").lineBusiness(otherLob).build();
        when(managerRepository.findByManagerExternalIdIgnoreCase(MANAGER_ID)).thenReturn(Optional.of(existingAssignment));

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validateManagerExternalIds(request));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getReason()).isEqualTo(ExceptionReason.BUSINESS_LINE_MANAGER_EXTERNAL_ID_EXISTS);
    }

    @Test
    public void givenManagerAlreadyAssignedToSameLob_whenValidateManagerExternalIds_thenSuccess() throws ApiException {
        // given - update scenario: manager already belongs to same LOB (id=1)
        LineBusinessView request = lobViewWithManagers(1L, "name", MANAGER_ID);
        LineBusinessViewEntity sameLob = LineBusinessViewEntity.builder().id(1L).name("name").build();
        LineBusinessManagerEntity existingAssignment = LineBusinessManagerEntity.builder()
                .managerExternalId(MANAGER_ID).managerName("Manager Name").lineBusiness(sameLob).build();
        when(managerRepository.findByManagerExternalIdIgnoreCase(MANAGER_ID)).thenReturn(Optional.of(existingAssignment));
        when(azureAdUserService.findById(MANAGER_ID)).thenReturn(Optional.of(userWithDisplayName(MANAGER_ID, "Manager Name")));

        // when
        assertDoesNotThrow(() -> validator.validateManagerExternalIds(request));
    }

    @Test
    public void givenManagerExternalIdNotFound_whenValidateManagerExternalIds_thenThrowError() throws ApiException {
        // given
        LineBusinessView request = lobViewWithManagers(1L, "name", MANAGER_ID);
        when(managerRepository.findByManagerExternalIdIgnoreCase(MANAGER_ID)).thenReturn(Optional.empty());
        when(azureAdUserService.findById(MANAGER_ID)).thenReturn(Optional.empty());

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validateManagerExternalIds(request));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.BUSINESS_LINE_MANAGER_EXTERNAL_ID_NOT_FOUND.label);
    }

    @Test
    public void givenNullManagerEntryInList_whenValidateManagerExternalIds_thenThrowError() throws ApiException {
        // given — list contains a null element
        List<LineBusinessManagerView> managers = new ArrayList<>();
        managers.add(null);
        LineBusinessView request = LineBusinessView.builder().id(1L).name("name").managers(managers).build();

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validateManagerExternalIds(request));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.BUSINESS_LINE_MANAGER_EXTERNAL_ID_REQUIRED.label);
    }

    @Test
    public void givenNoNameOrManagerChange_whenValidateUpdate_thenReturnRequest() {
        // given
        Long id = 1L;
        LineBusinessView request = lobViewWithManagers(id, "name", MANAGER_ID);
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(id).name("name")
                .managers(new ArrayList<>(List.of(
                        LineBusinessManagerEntity.builder().managerExternalId(MANAGER_ID).build())))
                .build();
        LineBusinessViewEntity sameLob = LineBusinessViewEntity.builder().id(id).name("name").build();
        LineBusinessManagerEntity existingAssignment = LineBusinessManagerEntity.builder()
                .managerExternalId(MANAGER_ID).managerName("Manager Name").lineBusiness(sameLob).build();
        when(managerRepository.findByManagerExternalIdIgnoreCase(MANAGER_ID)).thenReturn(Optional.of(existingAssignment));
        when(azureAdUserService.findById(MANAGER_ID)).thenReturn(Optional.of(userWithDisplayName(MANAGER_ID, "Manager Name")));

        // when
        LineBusinessView result = validator.validateUpdate(request, entity);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void givenNameChangeToExisting_whenValidateUpdate_thenReturnError() {
        // given
        Long id = 1L;
        LineBusinessView request = lobViewWithManagers(id, "name", MANAGER_ID);
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(id).name("oldName")
                .managers(new ArrayList<>()).build();
        when(repository.findByName("name")).thenReturn(Optional.of(LineBusinessViewEntity.builder().build()));

        // when
        ApiException thrown = assertThrows(BadRequestException.class, () -> validator.validateUpdate(request, entity));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getMessage()).isEqualTo(String.format(ExceptionReason.BUSINESS_LINE_DUPLICATE_FOUND.label, "name"));
    }

    @Test
    public void givenManagerChange_whenValidateUpdate_thenSuccess() {
        // given
        Long id = 1L;
        String newManagerId = "new-manager-id";
        LineBusinessView request = lobViewWithManagers(id, "name", newManagerId);
        LineBusinessViewEntity entity = LineBusinessViewEntity.builder().id(id).name("name")
                .managers(new ArrayList<>(List.of(
                        LineBusinessManagerEntity.builder().managerExternalId(MANAGER_ID).build())))
                .build();
        when(managerRepository.findByManagerExternalIdIgnoreCase(newManagerId)).thenReturn(Optional.empty());
        when(azureAdUserService.findById(newManagerId)).thenReturn(Optional.of(userWithDisplayName(newManagerId, "New Manager")));

        // when
        LineBusinessView result = validator.validateUpdate(request, entity);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getManagers().get(0).getManagerName()).isEqualTo("New Manager");
    }
}
