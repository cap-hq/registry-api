// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    public BadRequestException(ExceptionReason reason) {
        this(reason, reason.label);
    }

    public BadRequestException(ExceptionReason reason, String message) {
        super(HttpStatus.BAD_REQUEST, "bad-request", reason, message);
    }

}
