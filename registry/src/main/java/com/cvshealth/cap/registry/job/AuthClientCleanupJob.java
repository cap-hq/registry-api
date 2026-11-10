// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.config.AuthClientCleanupJobConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.job.AuthClientCleanupJobResponse;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import com.cvshealth.cap.registry.service.AuthClientCleanupService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.METRIC_REGISTRY_JOB_STATS;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.job.auth-client-cleanup.enabled")
public class AuthClientCleanupJob {
    private static final String JOB_NAME = "authClientCleanupJob";

    private final AuthClientCleanupService authClientCleanupService;
    private final AuthClientCleanupJobConfig jobConfig;
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    private final AttributeKey<String> jobNameKey = AttributeKey.stringKey("jobName");
    private final AttributeKey<String> jobStatusKey = AttributeKey.stringKey("status");

    @Scheduled(cron = "${app.job.auth-client-cleanup.cron-expression}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "${app.job.auth-client-cleanup.job-lock-time}", lockAtLeastFor = "${app.job.auth-client-cleanup.job-lock-time}")
    public void runScheduledJob() {
        SecurityHelper.setRegistryUser();

        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS);
        Instant start = Instant.now();

        boolean success = false;
        try {
            LockAssert.assertLocked();
            run();
            success = true;
        } finally {
            Attributes attributes = Attributes.of(jobNameKey, JOB_NAME, jobStatusKey, success ? "success" : "error");
            metricModel.recordDuration(Duration.between(start, Instant.now()).toMillis(), attributes);
        }
    }

    void run() {
        log.info("Running AuthClientCleanupJob minAgeDays={} inactivityDays={}", jobConfig.getMinAgeDays(), jobConfig.getInactivityDays());
        AuthClientCleanupJobResponse result = authClientCleanupService.runCleanup(jobConfig.getMinAgeDays(), jobConfig.getInactivityDays());
        log.info("Completed AuthClientCleanupJob candidates={} deleted={} failed={}",
                result.getCandidates().size(), result.getDeleted(), result.getFailed());
    }
}
