// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ResourceConstraintException extends ApiException {

    public ResourceConstraintException(ExceptionReason reason, List<String> constraints) {
        super(HttpStatus.BAD_REQUEST, "resource-constraint", reason, String.format(reason.label, String.join("; ", constraints)));
    }

}
