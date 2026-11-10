// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CloudTenant implements Serializable {
    private String name;
    private List<String> tenants;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> authorizedTeams;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Set<String> authorizedManagerExternalIds;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> regions;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String defaultRegion;
}