// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.DoubleGauge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class MetricRegistryConfig {

    // count and duration metrics
    public static final String METRIC_REGISTRY_CLIENT_REQUEST = "client.request";
    public static final String METRIC_REGISTRY_JOB_STATS = "job.stats";
    public static final String METRIC_REGISTRY_NOTIFICATION = "notification";
    public static final String METRIC_REGISTRY_VALIDATION_RESULT = "validation.result";

    // count metrics
    public static final String METRIC_REGISTRY_JOB_STATS_DETAIL = METRIC_REGISTRY_JOB_STATS + ".detail";
    public static final String METRIC_REGISTRY_JOB_STATS_PROCESSED_COUNT = METRIC_REGISTRY_JOB_STATS_DETAIL + ".processed";
    public static final String METRIC_REGISTRY_JOB_STATS_ERROR_COUNT = METRIC_REGISTRY_JOB_STATS_DETAIL + ".errors";
    public static final String METRIC_REGISTRY_JOB_STATS_METRIC_DELETED_COUNT = METRIC_REGISTRY_JOB_STATS_DETAIL + ".deleted";
    public static final String METRIC_REGISTRY_JOB_STATS_METRIC_UPDATED_COUNT = METRIC_REGISTRY_JOB_STATS_DETAIL + ".updated";
    public static final String METRIC_REGISTRY_JOB_STATS_METRIC_DELETED_ERROR_COUNT = METRIC_REGISTRY_JOB_STATS_DETAIL + ".deleteErrors";

    @Bean
    public Map<String, CountAndDurationMetricModel> countAndDurationMetrics(@Value("${app.metrics.prefix}") String metricPrefix) {
        return Set.of(METRIC_REGISTRY_CLIENT_REQUEST, METRIC_REGISTRY_JOB_STATS, METRIC_REGISTRY_NOTIFICATION,
                        METRIC_REGISTRY_VALIDATION_RESULT)
                .stream()
                .collect(Collectors.toMap(Function.identity(), name -> new CountAndDurationMetricModel(metricPrefix + "." + name)));
    }

    @Bean
    public Map<String, DoubleGauge> jobDetailCountMetrics(@Value("${app.metrics.prefix}") String metricPrefix) {
        return Set.of(METRIC_REGISTRY_JOB_STATS_PROCESSED_COUNT,
                        METRIC_REGISTRY_JOB_STATS_METRIC_DELETED_COUNT,
                        METRIC_REGISTRY_JOB_STATS_METRIC_UPDATED_COUNT,
                        METRIC_REGISTRY_JOB_STATS_ERROR_COUNT,
                        METRIC_REGISTRY_JOB_STATS_METRIC_DELETED_ERROR_COUNT)
                .stream()
                .collect(Collectors.toMap(key -> key, key -> buildCountMetric(metricPrefix, key)));
    }

    public static DoubleGauge buildCountMetric(String metricPrefix, String name) {
        String metricName = metricPrefix + "." + name;
        return GlobalOpenTelemetry.meterBuilder(metricPrefix + "." + METRIC_REGISTRY_JOB_STATS)
                .build()
                .gaugeBuilder(metricName)
                .setDescription(String.format("Job Detail counts for %s", metricName))
                .build();
    }
}
