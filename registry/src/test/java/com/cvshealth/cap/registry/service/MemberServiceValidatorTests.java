// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class MemberServiceValidatorTests {
    @InjectMocks
    private MemberServiceValidator validator;

    @Test
    public void givenValidObject_whenValidate_thenReturnRequest() throws ApiException {
        Member request = Member.builder()
                .externalId("login-id-1")
                .email("email-1")
                .name("user-1")
                .build();

        request = validator.validate(request);

        assertThat(request).isNotNull();
    }

    @Test
    public void givenNull_whenValidate_thenThrowException() {
        Member request = null;

        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).contains(ExceptionReason.REQUEST_IS_EMPTY.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.REQUEST_IS_EMPTY);
    }

    @Test
    public void givenMissingexternalId_whenValidate_thenThrowException() {
        Member request = Member.builder()
                .externalId(null)
                .email("email-1")
                .name("user-1")
                .build();

        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).contains(ExceptionReason.MEMBER_EXTERNAL_ID_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.MEMBER_EXTERNAL_ID_REQUIRED);
    }

}
