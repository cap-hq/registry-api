// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AuthenticationEvent {
    private String result;
    private String actor;
    private String sourceClass;
    private String clientId;
    private String user;
    private AuthenticationErrorMessage errorMessage;
    private Exception exception;
    private String jwt;
    private String redirectUri;
}
