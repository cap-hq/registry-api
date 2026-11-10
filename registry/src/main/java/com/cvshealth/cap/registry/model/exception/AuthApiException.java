// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class AuthApiException extends ApiException {
    public AuthApiException(HttpStatus status, String message) {
        super(status, "registry-auth", ExceptionReason.AUTH_API_ERROR, message);
    }
}
