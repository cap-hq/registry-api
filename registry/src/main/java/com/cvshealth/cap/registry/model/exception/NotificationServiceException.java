// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class NotificationServiceException extends Exception {
    private static final String baseType = "notification-service/";

    private HttpStatus status;
    private String type;
    private String message;

    public NotificationServiceException(HttpStatus status, String type, String message) {
        super(message);
        this.type = baseType + type;
        this.status = status;
        this.message = message;
    }
}
