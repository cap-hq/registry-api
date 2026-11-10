// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.apiforge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors the ApiForge {@code ApiDefinitionEnvironment} type.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiForgeApiDefinitionEnvironment {
    private String id;
    private String environment;
    private String state;
}

