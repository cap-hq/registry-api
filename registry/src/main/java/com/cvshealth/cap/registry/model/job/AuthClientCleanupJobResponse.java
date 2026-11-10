// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.job;

import com.cvshealth.cap.registry.model.AuthClientView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthClientCleanupJobResponse {
    private boolean dryRun;
    private List<AuthClientView> candidates;
    private int deleted;
    private int failed;
}
