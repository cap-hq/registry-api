// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.audit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuditLogFilter extends Filter<ILoggingEvent> {

    private String loggerName;
    private String onMismatch;
    private String onMatch;

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event.getLoggerName().equals(loggerName)) {
            return FilterReply.valueOf(onMatch);
        } else {
            return FilterReply.valueOf(onMismatch);
        }
    }
}