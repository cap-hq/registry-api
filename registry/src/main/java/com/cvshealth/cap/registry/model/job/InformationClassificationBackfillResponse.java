// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Result of the information classification backfill. Applications whose compliance types contain a tag
 * with no mapping are left untouched and listed in {@code skipped}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformationClassificationBackfillResponse implements Serializable {
    private long scanned;
    private long updated;
    private List<SkippedApplication> skipped;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkippedApplication implements Serializable {
        private Long id;
        private String name;
        private List<String> complianceTypes;
    }
}
