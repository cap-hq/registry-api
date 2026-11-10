// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class User {
    private String externalId;
    private String externalAlias;
    private String employeeId;
    private String displayName;
    private String firstName;
    private String lastName;
    private String email;
    private String title;
    private User manager;
}
