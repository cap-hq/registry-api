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
public class ApplicationSearchFilter implements NameSearchFilter {
    private String name;
    private List<String> names;
    private Boolean partialNameMatch;
    private Boolean withExternalIdSet;
    private List<String> clouds;
    private List<String> tenants;
}
