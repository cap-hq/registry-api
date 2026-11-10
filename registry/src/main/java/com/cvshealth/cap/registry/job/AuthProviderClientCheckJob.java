// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.config.AuthProviderClientCheckJobConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.metric.LongMetricData;
import com.cvshealth.cap.registry.model.CredentialInfo;
import com.cvshealth.cap.registry.service.AzureApplicationService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.*;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.job.auth-provider-client-check.enabled")
public class AuthProviderClientCheckJob {
    private static final String JOB_NAME = "authProviderClientCheckJob";
    private static final String METRIC_AUTH_PROVIDER_CLIENT = METRIC_REGISTRY_JOB_STATS + ".auth-provider-client";
    private static final String METRIC_TOKEN = METRIC_AUTH_PROVIDER_CLIENT + ".token";
    private static final AttributeKey<String> NAME_ATTRIBUTE_KEY = AttributeKey.stringKey("name");
    private static final AttributeKey<String> TYPE_ATTRIBUTE_KEY = AttributeKey.stringKey("type");
    private static final AttributeKey<String> ACTIVE_ATTRIBUTE_KEY = AttributeKey.stringKey("active");

    private final AzureApplicationService azureAdApplicationService;
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;
    private final AuthProviderClientCheckJobConfig authProviderClientCheckConfig;

    private final AttributeKey<String> jobNameKey = AttributeKey.stringKey("jobName");
    private final AttributeKey<String> jobStatusKey = AttributeKey.stringKey("status");

    private final Map<String, LongMetricData[]> unprocessedStatsValues = new ConcurrentHashMap<>();

    @Value("${app.metrics.prefix}")
    private String metricPrefix;

    @PostConstruct
    private void init() {
        Meter meter = GlobalOpenTelemetry.meterBuilder(metricPrefix + "." + METRIC_AUTH_PROVIDER_CLIENT).build();
        meter.gaugeBuilder(metricPrefix + "." + METRIC_TOKEN)
                .setDescription("Registry Auth Provider Client Token")
                .setUnit("number")
                .buildWithCallback(observableMeasurement -> metricCallback(METRIC_TOKEN, observableMeasurement));

        // Log client credential info on startup
        authProviderClientCheckConfig.getClients().forEach(azureAdApplicationService::clientCredentials);
    }

    @Scheduled(cron = "${app.job.auth-provider-client-check.cron-expression}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "${app.job.auth-provider-client-check.job-lock-time}", lockAtLeastFor = "${app.job.auth-provider-client-check.job-lock-time}")
    public void authProviderClientCheckJob() {
        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS);
        Instant start = Instant.now();

        boolean success = false;
        try {
            LockAssert.assertLocked();

            authProviderClientCheckConfig.getClients().forEach(client -> {
                List<CredentialInfo> credentials = azureAdApplicationService.clientCredentials(client);
                if (!CollectionUtils.isEmpty(credentials)) {
                    List<LongMetricData> metricData = new ArrayList<>();
                    credentials.forEach(credentialInfo -> {
                        metricData.add(new LongMetricData(credentialInfo.ageInDays(), Attributes.of(TYPE_ATTRIBUTE_KEY, "ageInDays",
                                NAME_ATTRIBUTE_KEY, credentialInfo.credentialName(), ACTIVE_ATTRIBUTE_KEY, Boolean.toString(credentialInfo.active()))));
                        metricData.add(new LongMetricData(credentialInfo.expirationInDays(), Attributes.of(TYPE_ATTRIBUTE_KEY, "expirationInDays",
                                NAME_ATTRIBUTE_KEY, credentialInfo.credentialName(), ACTIVE_ATTRIBUTE_KEY, Boolean.toString(credentialInfo.active()))));
                    });
                    unprocessedStatsValues.put(METRIC_TOKEN, metricData.toArray(new LongMetricData[0]));
                }
            });
            success = true;
        } finally {
            Attributes attributes = Attributes.of(
                    jobNameKey, JOB_NAME,
                    jobStatusKey, success ? "success" : "error");
            metricModel.recordDuration(Duration.between(start, Instant.now()).toMillis(), attributes);
        }
    }

    /**
     * Metric collector callback that records metric values only when data is present for a given metric.
     * This overrides the default behavior of sending the last recorded metric value, regardless of whether it has been refreshed during the collector periodic interval.
     * The default behavior is not desired for global metrics, where any instance of the service can populate the metric,
     * and an individual instance last recorded metric value may be out of date.
     */
    private void metricCallback(String metricName, ObservableDoubleMeasurement observableMeasurement) {
        LongMetricData[] metricValues = unprocessedStatsValues.remove(metricName);
        if (metricValues != null) {
            for (LongMetricData metricData : metricValues) {
                if (log.isDebugEnabled()) {
                    log.debug(String.format("record metric [%s = %s]", metricName, metricData.toString()));
                }
                observableMeasurement.record(metricData.getValue(), metricData.getAttributes());
            }
        }
    }
}
