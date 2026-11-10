// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.metric;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class CountAndDurationMetricModel {

    private final String name;
    private LongHistogram durationHistogram;

    public CountAndDurationMetricModel(String name) {
        this.name = name;
        init();
    }

    private void init() {
        durationHistogram = GlobalOpenTelemetry.meterBuilder(this.name).build()
                .histogramBuilder(String.format("%s.duration", name))
                .setDescription(String.format("Duration of %s requests", name))
                .ofLongs()
                .setUnit("ms")
                .build();
    }

    public void recordDuration(long ms, Attributes attributes) {
        durationHistogram.record(ms, attributes);
    }

    public static Attributes toAttributes(MetricAttributePair[] attributePairs, boolean success) {
        AttributesBuilder builder = Attributes.builder();
        Arrays.stream(attributePairs).forEach(ap -> builder.put(ap.key(), ap.value()));
        builder.put("status", success ? "success" : "error");
        return builder.build();
    }
}
