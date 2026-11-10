// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.config;

import com.cvshealth.cap.registry.rollup.metric.CountAndDurationMetricModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class MetricRegistryConfig {

    public static final String METRIC_REGISTRY_CLIENT_REQUEST = "client.request";

    @Bean
    public Map<String, CountAndDurationMetricModel> countAndDurationMetrics(@Value("${app.metrics.prefix}") String metricPrefix) {
        return Set.of(METRIC_REGISTRY_CLIENT_REQUEST)
                .stream()
                .collect(Collectors.toMap(Function.identity(), name -> new CountAndDurationMetricModel(metricPrefix + "." + name)));
    }
}
