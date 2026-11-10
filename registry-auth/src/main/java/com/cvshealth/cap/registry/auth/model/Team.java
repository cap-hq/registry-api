// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Team {
    // team types
    public static String PERSONAL = "PERSONAL";
    public static String SHARED = "SHARED";
    // member roles
    public static String OWNER = "OWNER";
    public static String MEMBER = "MEMBER";
    public static String VIEWER = "VIEWER";

    private String name;
    private String role;
    private String type;
    private List<Application> applications;
}
