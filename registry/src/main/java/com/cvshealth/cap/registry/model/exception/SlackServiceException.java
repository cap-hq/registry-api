// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Getter
@Setter
public class SlackServiceException extends Exception {
    private static final String baseType = "slack-client/";

    private HttpStatus status;
    private String type;
    private String message;
    private Integer retryableInSeconds;

    public SlackServiceException(HttpStatus status, String type, String message) {
        super(message);
        this.type = baseType + type;
        this.status = status;
        this.message = message;
    }

    public SlackServiceException(HttpStatus status, String type, String message, Integer retryableInSeconds) {
        this(status, type, message);
        this.retryableInSeconds = retryableInSeconds;
    }
}
