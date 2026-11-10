// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagerTitleUtilTests {

    @Test
    public void createTitlePattern() {
        // given
        List<String> titles = List.of("SVP", "Senior Vice President");

        // when
        Pattern pattern = ManagerTitleUtil.createJobTitleLevelPattern(titles);

        // then
        assertThat(pattern.matcher("SVP of Software").matches()).isTrue();
        assertThat(pattern.matcher("SVP, Software").matches()).isTrue();
        assertThat(pattern.matcher("svp, Software").matches()).isTrue();
        assertThat(pattern.matcher("Senior Vice President of Software").matches()).isTrue();
        assertThat(pattern.matcher("senior vice president of software").matches()).isTrue();
        assertThat(pattern.matcher("Senior Vice President, Software").matches()).isTrue();
        assertThat(pattern.matcher("VP of Software").matches()).isFalse();
        assertThat(pattern.matcher("VP, Software").matches()).isFalse();
        assertThat(pattern.matcher("Vice President, Software").matches()).isFalse();
    }
}
