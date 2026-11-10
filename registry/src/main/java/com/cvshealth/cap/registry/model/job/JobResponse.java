// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response DTO for async job submission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse implements Serializable {
    private String jobId;
    private JobStatus status;
    private String message;
}

