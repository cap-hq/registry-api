// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.apiforge;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
/**
 * Mirrors the ApiForge {@code ApiDefinition} type.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiForgeApiDefinition {
    private String id;
    private Integer majorVersion;
    private String state;
    private List<ApiForgeApiDefinitionEnvironment> environments;
}
