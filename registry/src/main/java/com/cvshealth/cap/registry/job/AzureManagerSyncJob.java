// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.job.JobRecordProcessingStats;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import com.cvshealth.cap.registry.service.MemberSyncService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
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

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.*;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.job.azure-manager-sync.enabled")
public class AzureManagerSyncJob {
    private static final String JOB_NAME = "azureUserManagerSyncJob";

    private final MemberSyncService azureSyncService;
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;
    private final Map<String, DoubleGauge> jobDetailCountMetrics;

    private final AttributeKey<String> jobNameKey = AttributeKey.stringKey("jobName");
    private final AttributeKey<String> jobStatusKey = AttributeKey.stringKey("status");

    @Scheduled(cron = "${app.job.azure-manager-sync.cron-expression}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "${app.job.azure-manager-sync.job-lock-time}", lockAtLeastFor = "${app.job.azure-manager-sync.job-lock-time}")
    public void azureManagerSyncJob() {
        SecurityHelper.setRegistryUser();

        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS);
        Instant start = Instant.now();

        JobRecordProcessingStats jobStats = null;
        boolean success = false;
        try {
            LockAssert.assertLocked();

            // unassign member's manager if member is no longer a direct report in Active Directory
            azureSyncService.syncUsersManagerByExistingManager();
            // assign members without a manager to their manager in Active Directory
            jobStats = azureSyncService.syncUsersManager();
            success = true;
        } finally {
            Attributes attributes = Attributes.of(
                    jobNameKey, JOB_NAME,
                    jobStatusKey, success ? "success" : "error");

            metricModel.recordDuration(Duration.between(start, Instant.now()).toMillis(), attributes);
            if (jobStats != null) {
                jobDetailCountMetrics.get(METRIC_REGISTRY_JOB_STATS_PROCESSED_COUNT).set(jobStats.getRecordsProcessed(), attributes);
                jobDetailCountMetrics.get(METRIC_REGISTRY_JOB_STATS_METRIC_UPDATED_COUNT).set(jobStats.getRecordsUpdated(), attributes);
                jobDetailCountMetrics.get(METRIC_REGISTRY_JOB_STATS_ERROR_COUNT).set(jobStats.getRecordsErrored(), attributes);
            }
        }
    }
}
