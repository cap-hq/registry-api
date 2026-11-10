// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.DateFormatterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DateFormatServiceTests {

    @InjectMocks
    private DateFormatService dateFormatService;

    @Mock
    private DateFormatterConfig dateFormatterConfig;

    private static final Instant DATE_TIME1 = Instant.parse("2025-12-17T10:15:30.00Z");

    @BeforeEach
    public void init() {
        when(dateFormatterConfig.getDateFormat()).thenReturn("yyyy-MM-dd");
        when(dateFormatterConfig.getTimeFormat()).thenReturn("h:mm a z");
        when(dateFormatterConfig.getTimeZone()).thenReturn("America/New_York");
        dateFormatService.init();
    }

    @Test
    public void dateAtTimeFormat() {
        // given
        String expected = "2025-12-17 at 5:15 AM EST";

        // when
        String result = dateFormatService.dateAtTimeFormat(DATE_TIME1);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void dateTimeFormat() {
        // given
        String expected = "2025-12-17 5:15 AM EST";

        // when
        String result = dateFormatService.dateTimeFormat(DATE_TIME1);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void dateFormat() {
        // given
        String expected = "2025-12-17";

        // when
        String result = dateFormatService.dateFormat(DATE_TIME1);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void timeFormat() {
        // given
        String expected = "5:15 AM EST";

        // when
        String result = dateFormatService.timeFormat(DATE_TIME1);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expected);
    }
}
