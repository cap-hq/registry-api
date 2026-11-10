// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
//DO NOT remove unused attributes, they are needed for Azure call
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Group {
    private String odata_type;
    private String mail;
    private String displayName;
    private String mailEnabled;
    private String securityEnabled;
}

