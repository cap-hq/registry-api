// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class NameSearchFilter {
    private String name;
    private List<String> names;

    public List<String> getNameList() {
        List<String> list = null;
        if (name != null || (names != null && !names.isEmpty())) {
            list = new ArrayList<>();
            if (name != null) {
                list.add(name);
            }
            if (names != null && !names.isEmpty()) {
                list.addAll(names);
            }
        }
        return list;
    }
}
