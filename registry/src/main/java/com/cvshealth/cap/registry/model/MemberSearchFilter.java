// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberSearchFilter implements NameSearchFilter {
    private String externalId;
    private List<String> externalIds;
    private String externalAlias;
    private List<String> externalAliases;
    private String email;
    private List<String> emails;
    private String employeeId;
    private List<String> employeeIds;
    private String name;
    private List<String> names;
    private String lastName;
    private List<String> lastNames;
    private Boolean partialNameMatch;
    private String managerExternalId;
    private List<String> managerExternalIds;
}
