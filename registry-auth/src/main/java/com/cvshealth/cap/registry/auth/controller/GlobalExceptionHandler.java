// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.controller;

import com.cvshealth.cap.registry.auth.model.exception.ApiException;
import com.cvshealth.cap.registry.auth.model.exception.ExceptionReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

import java.net.URI;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Provides handling for exceptions throughout this service. */
    @ExceptionHandler({ ApiException.class, ResponseStatusException.class, RuntimeException.class })
    public final ResponseEntity<ProblemDetail> handleException(Exception ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();

        if (ex instanceof ApiException) {
            return handleApiException((ApiException)ex, headers, request);
        } else if (ex instanceof AccessDeniedException) {
            return handleAccessDeniedException((AccessDeniedException)ex, headers, request);
        } else {
            return handleExceptionInternal(ex, null, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
    }

    protected ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpHeaders headers, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setType(ex.getType());
        pd.setProperty("reason", ex.getReason());
        return handleExceptionInternal(ex, pd, headers, ex.getStatus(), request);
    }

    protected ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, HttpHeaders headers, WebRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        pd.setType(URI.create(ApiException.baseType + "access-denied"));
        pd.setProperty("reason", ExceptionReason.ACCESS_DENIED);
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    /** A single place to customize the response body of all Exception types. */
    protected ResponseEntity<ProblemDetail> handleExceptionInternal(Exception ex, ProblemDetail body, HttpHeaders headers, HttpStatus status, WebRequest request) {
        if (HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
            log.error("Unexpected error", ex);
            request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
            body = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
            body.setType(URI.create(ApiException.baseType + "unexpected-error"));
            body.setProperty("reason", ExceptionReason.UNEXPECTED_ERROR);
            body.setProperty("stackTrace", stringifyStackTrace(ex));
        }
        return new ResponseEntity<>(body, headers, status);
    }

    private String stringifyStackTrace(Exception ex) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : ex.getStackTrace()) {
            sb.append(element.toString());
            sb.append("\n");
        }
        return sb.toString();
    }
}