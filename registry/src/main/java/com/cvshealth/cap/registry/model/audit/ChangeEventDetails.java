// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ChangeEventDetails implements Serializable {

    private boolean deltasOnly;
    private boolean metadataExcluded;
    private Map<String, Object> context;
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
}
