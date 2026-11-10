// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.job;

import com.cvshealth.cap.registry.auth.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.cvshealth.cap.registry.auth.config.MetricRegistryConfig.*;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.job.auth-session-cleanup.enabled")
public class AuthorizationSessionCleanupJob {
    private static final String JOB_NAME = "authSessionCleanupJob";

    private final OAuth2AuthorizationService authorizationService;
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    private final AttributeKey<String> jobNameKey = AttributeKey.stringKey("jobName");
    private final AttributeKey<String> jobStatusKey = AttributeKey.stringKey("status");

    @Scheduled(cron = "${app.job.auth-session-cleanup.cron-expression}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "${app.job.auth-session-cleanup.job-lock-time}", lockAtLeastFor = "${app.job.auth-session-cleanup.job-lock-time}")
    public void runCleanup() {
        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS);
        Instant start = Instant.now();

        boolean success = false;
        try {
            LockAssert.assertLocked();
            if (authorizationService instanceof CustomJdbcOauth2AuthorizationService jdbcOauth2AuthorizationService) {
                jdbcOauth2AuthorizationService.cleanUpExpiredAuthorizations();
            }
            success = true;
        } finally {
            Attributes attributes = Attributes.of(
                    jobNameKey, JOB_NAME,
                    jobStatusKey, success ? "success" : "error");
            metricModel.recordDuration(Duration.between(start, Instant.now()).toMillis(), attributes);
        }
    }
}
