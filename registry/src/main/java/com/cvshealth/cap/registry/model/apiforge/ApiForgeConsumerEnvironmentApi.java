// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.apiforge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors the ApiForge {@code ConsumerEnvironmentApi} type.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiForgeConsumerEnvironmentApi {
    private ApiForgeApi api;
    private String approvalState;
}

