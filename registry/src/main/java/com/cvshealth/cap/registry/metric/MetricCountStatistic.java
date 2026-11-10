// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.metric;

import com.cvshealth.cap.registry.persistent.CountStatistic;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MetricCountStatistic implements CountStatistic {

    private long count;
    private String type;

    @Override
    public String getType() {
        return type;
    }

    @Override
    public long getCount() {
        return count;
    }
}
