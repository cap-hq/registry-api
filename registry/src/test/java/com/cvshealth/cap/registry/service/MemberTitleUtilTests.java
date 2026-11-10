// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class MemberTitleUtilTests {

    @Test
    public void createTitlePattern() {
        // given
        List<String> titles = List.of("SVP", "Senior Vice President",
                "EVP", "Executive Vice President");

        // when
        Pattern pattern = MemberTitleUtil.createJobTitleLevelPattern(titles);

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
        assertThat(pattern.matcher("EVP of Software").matches()).isTrue();
        assertThat(pattern.matcher("EVP, Software").matches()).isTrue();
        assertThat(pattern.matcher("evp, Software").matches()).isTrue();
        assertThat(pattern.matcher("Executive Vice President of Software").matches()).isTrue();
        assertThat(pattern.matcher("executive vice president of software").matches()).isTrue();
        assertThat(pattern.matcher("Executive Vice President, Software").matches()).isTrue();
    }
}
