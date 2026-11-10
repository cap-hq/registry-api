// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model.exception;

public enum ExceptionReason {
    // General
    UNEXPECTED_ERROR(null),
    REQUEST_IS_EMPTY("Request is empty"),
    ACCESS_DENIED("Access denied"),

    // Client
    CLIENT_NOT_FOUND("Client record not found"),
    CLIENT_DUPLICATE_FOUND("Duplicate client found"),
    CLIENT_REDIRECT_NOT_ALLOWED("Redirect urls are not valid for client type"),
    CLIENT_REDIRECT_NOT_VALID("Redirect url cannot be empty or blank"),
    CLIENT_REFRESH_NOT_ALLOWED("Refresh token is not valid for client type"),
    CLIENT_SECRET_EXPIRES_AT_NOT_ALLOWED("Client Secret expires at is not valid for client type"),
    CLIENT_SECRET_EXPIRES_AT_INVALID("Client Secret expires at date/time is not valid"),
    CLIENT_SECRET_EXPIRES_AT_OVER_MAX("Client Secret expires at date/time cannot be more than %s days"),
    CLIENT_IDS_OVER_MAX("Client id list cannot exceed %s entries");

    public final String label;

    ExceptionReason(String label) {
        this.label = label;
    }
}
