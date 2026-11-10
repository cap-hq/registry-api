// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.net.URI;

@Getter
@Setter
public class ApiException extends RuntimeException {
    protected HttpStatus status;
    protected URI type;
    protected ExceptionReason reason;
    public static String baseType = "http://registry.cap.cvshealth.com/";

    public ApiException(HttpStatus status, String type, ExceptionReason reason, String message) {
        super(message);
        this.type = URI.create(baseType + type);
        this.status = status;
        this.reason = reason;
    }
}
