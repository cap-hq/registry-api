// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ManagerTitleUtil {

    public static Pattern createJobTitleLevelPattern(List<String> jobTitlesLevels) {
        if (CollectionUtils.isEmpty(jobTitlesLevels)) {
            return null;
        }
        List<String> patterns = new ArrayList<>();
        for (String level : jobTitlesLevels) {
            patterns.add("(?i)^.*" + level + "[ ,].*$");
        }
        return Pattern.compile(String.join("|", patterns));
    }
}
