// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthenticationErrorMessage {
    String code;
    String message;
}
