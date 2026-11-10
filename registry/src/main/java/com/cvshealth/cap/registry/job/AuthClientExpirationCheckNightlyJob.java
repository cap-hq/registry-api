// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.config.AuthClientExpirationCheckNightlyJobConfig;
import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.job.AuthClientExpirationCheckJobStats;
import com.cvshealth.cap.registry.model.job.CheckAuthClientExpirationsRequest;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import com.cvshealth.cap.registry.service.AuthClientExpirationNotificationService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.*;
import static com.cvshealth.cap.registry.job.AuthClientExpirationCheckJob.*;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.job.auth-client-expiration-check-nightly.enabled")
public class AuthClientExpirationCheckNightlyJob {
    private static final String JOB_NAME = "authClientExpirationCheckNightlyJob";

    private final AuthClientExpirationNotificationService notificationService;
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;
    private final AuthClientExpirationCheckNightlyJobConfig jobConfig;

    private final AttributeKey<String> jobNameKey = AttributeKey.stringKey("jobName");
    private final AttributeKey<String> jobStatusKey = AttributeKey.stringKey("status");
    private Map<String, DoubleGauge> jobDetailMetrics;

    @Value("${app.metrics.prefix}")
    private String metricPrefix;

    @PostConstruct
    void init() {
        jobDetailMetrics = Set.of(EXPIRING_COUNT, EXPIRED_COUNT, DELETED_COUNT, TEAM_NOTIFY_COUNT, SLACK_NOTIFY_COUNT, SLACK_NOTIFY_ERROR_COUNT,
                        EMAIL_NOTIFY_COUNT, LEGACY_EXPIRING_COUNT, LEGACY_EXPIRED_COUNT, LEGACY_DELETED_COUNT)
                .stream()
                .collect(Collectors.toMap(key -> key, key -> MetricRegistryConfig.buildCountMetric(metricPrefix, key)));
    }

    @Scheduled(cron = "${app.job.auth-client-expiration-check-nightly.cron-expression}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "${app.job.auth-client-expiration-check-nightly.job-lock-time}", lockAtLeastFor = "${app.job.auth-client-expiration-check-nightly.job-lock-time}")
    public void runScheduledJob() {
        SecurityHelper.setRegistryUser();

        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS);
        Instant start = Instant.now();

        AuthClientExpirationCheckJobStats jobStats = null;
        boolean success = false;
        try {
            LockAssert.assertLocked();
            jobStats = notificationService.checkAuthClientsExpirations(buildJobRequest());
            success = true;
        } finally {
            Attributes attributes = Attributes.of(
                    jobNameKey, JOB_NAME,
                    jobStatusKey, success ? "success" : "error");
            metricModel.recordDuration(Duration.between(start, Instant.now()).toMillis(), attributes);
            if (jobStats != null) {
                recordJobDetails(jobStats, attributes);
            }
        }
    }

    private CheckAuthClientExpirationsRequest buildJobRequest() {
        return CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(jobConfig.getExpirationDaysThreshold())
                .minExpirationDaysThreshold(jobConfig.getExpirationDaysLowerBound())
                .ageDaysThreshold(jobConfig.getAgeDaysThreshold())
                .sendSlackNotifications(jobConfig.isSendSlackNotifications())
                .sendEmailNotifications(jobConfig.isSendEmailNotifications())
                .deleteExpiredClients(false)
                .deleteExpiredLegacyClients(false)
                .expiredClientRetentionDays(jobConfig.getExpiredClientRetentionDays())
                .legacyClientDeleteAgeDaysThreshold(jobConfig.getLegacyClientDeleteAgeDaysThreshold())
                .messageTemplate(jobConfig.getMessageTemplate())
                .docsUrl(jobConfig.getDocsUrl())
                .notificationSubject(jobConfig.getNotificationSubject())
                .lastAccessTokenIssuedWithinHours(jobConfig.getLastAccessTokenIssuedWithinHours())
                .build();
    }

    private void recordJobDetails(AuthClientExpirationCheckJobStats jobStats, Attributes attributes) {
        jobDetailMetrics.get(EXPIRING_COUNT).set(jobStats.getExpiringAuthClients(), attributes);
        jobDetailMetrics.get(EXPIRED_COUNT).set(jobStats.getExpiredAuthClients(), attributes);
        jobDetailMetrics.get(DELETED_COUNT).set(jobStats.getDeletedAuthClients(), attributes);
        jobDetailMetrics.get(TEAM_NOTIFY_COUNT).set(jobStats.getTeamsNotified(), attributes);
        jobDetailMetrics.get(LEGACY_EXPIRING_COUNT).set(jobStats.getExpiringLegacyAuthClients(), attributes);
        jobDetailMetrics.get(LEGACY_EXPIRED_COUNT).set(jobStats.getExpiredLegacyAuthClients(), attributes);
        jobDetailMetrics.get(LEGACY_DELETED_COUNT).set(jobStats.getDeletedLegacyAuthClients(), attributes);
    }
}
