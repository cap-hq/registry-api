// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExternalResourceResponse {
    private List<Resource> resources;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Resource {
        private String name;
    }
}
