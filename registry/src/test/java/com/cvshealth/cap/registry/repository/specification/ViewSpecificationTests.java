// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.repository.specification;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.cvshealth.cap.registry.repository.specification.ViewSpecification.toLowerCase;
import static com.cvshealth.cap.registry.repository.specification.ViewSpecification.toUpperCase;
import static org.assertj.core.api.Assertions.assertThat;

public class ViewSpecificationTests {

    @Test
    public void toLowerCase_String() {
        assertThat(toLowerCase((String)null)).isNull();
        assertThat(toLowerCase("")).isEqualTo("");
        assertThat(toLowerCase("team1")).isEqualTo("team1");
        assertThat(toLowerCase("App-1")).isEqualTo("app-1");
    }

    @Test
    public void toLowerCase_StringList() {
        assertThat(toLowerCase((List<String>)null)).isEmpty();
        assertThat(toLowerCase(Collections.emptyList())).isEmpty();
        assertThat(toLowerCase(List.of("", "Team-1", "APP2", "name3"))).contains("", "team-1", "app2", "name3");
    }

    @Test
    public void toUpperCase_String() {
        assertThat(toUpperCase((String)null)).isNull();
        assertThat(toUpperCase("")).isEqualTo("");
        assertThat(toUpperCase("team1")).isEqualTo("TEAM1");
        assertThat(toUpperCase("App-1")).isEqualTo("APP-1");
    }

    @Test
    public void toUpperCase_StringList() {
        assertThat(toUpperCase((List<String>)null)).isEmpty();
        assertThat(toUpperCase(Collections.emptyList())).isEmpty();
        assertThat(toUpperCase(List.of("", "Team-1", "APP2", "name3"))).contains("", "TEAM-1", "APP2", "NAME3");
    }
}
