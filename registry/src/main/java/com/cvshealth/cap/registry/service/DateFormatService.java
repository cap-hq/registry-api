// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.DateFormatterConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class DateFormatService {

    private final DateFormatterConfig dateFormatterConfig;

    private DateTimeFormatter dateTimeFormatter;
    private DateTimeFormatter dateFormatter;
    private DateTimeFormatter timeFormatter;

    @PostConstruct
    void init() {
        ZoneId appZoneId = ZoneId.of(dateFormatterConfig.getTimeZone());
        dateTimeFormatter = DateTimeFormatter.ofPattern(dateFormatterConfig.getDateFormat() + " " + dateFormatterConfig.getTimeFormat()).withZone(appZoneId);
        dateFormatter = DateTimeFormatter.ofPattern(dateFormatterConfig.getDateFormat()).withZone(appZoneId);
        timeFormatter = DateTimeFormatter.ofPattern(dateFormatterConfig.getTimeFormat()).withZone(appZoneId);
    }

    public String dateAtTimeFormat(Instant dateTime) {
        return dateFormat(dateTime) + " at " + timeFormat(dateTime);
    }

    public String dateTimeFormat(Instant dateTime) {
        return dateTimeFormatter.format(dateTime);
    }

    public String dateFormat(Instant dateTime) {
        return dateFormatter.format(dateTime);
    }

    public String timeFormat(Instant dateTime) {
        return timeFormatter.format(dateTime);
    }
}
