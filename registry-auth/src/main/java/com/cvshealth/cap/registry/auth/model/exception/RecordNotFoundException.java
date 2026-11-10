// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model.exception;

import org.springframework.http.HttpStatus;

public class RecordNotFoundException extends ApiException {

    public RecordNotFoundException(ExceptionReason reason) {
        super(HttpStatus.NOT_FOUND, "record-not-found", reason, reason.label);
    }

}
