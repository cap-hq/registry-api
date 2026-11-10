// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class AzureAdUserServiceValidatorTests {
    @InjectMocks
    private AzureAdUserServiceValidator validator;

    @Test
    public void givenValidObject_whenValidate_thenReturnRequest() throws ApiException {
        String request = validator.validate("test");
        assertThat(request).isNotNull();
    }

    @Test
    public void givenNull_whenValidate_thenThrowException() {
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(null));
        assertThat(ex.getMessage()).contains(ExceptionReason.USER_SEARCH_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.USER_SEARCH_REQUIRED);
    }

    @Test
    public void givenEmpty_whenValidate_thenThrowException() {
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(""));
        assertThat(ex.getMessage()).contains(ExceptionReason.USER_SEARCH_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.USER_SEARCH_REQUIRED);
    }

    @Test
    public void givenMinLength_whenValidate_thenThrowException() {
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate("te"));
        assertThat(ex.getMessage()).contains(String.format(ExceptionReason.USER_SEARCH_INVALID_LENGTH.label, AzureAdUserServiceValidator.SEARCH_MIN_LENGTH));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.USER_SEARCH_INVALID_LENGTH);
    }

}
