// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
//DO NOT remove unused attributes, they are needed for Azure call
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class UserGroups {
    private String odataCount;
    private List<Group> value;
    private Map<String,Object> additionalData;
}
