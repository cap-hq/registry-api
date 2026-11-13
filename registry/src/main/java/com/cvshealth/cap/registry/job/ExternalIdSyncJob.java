// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.ApplicationSearchFilter;
import com.cvshealth.cap.registry.model.ApplicationView;
import com.cvshealth.cap.registry.model.PaginatedApplicationResponse;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;
import com.cvshealth.cap.registry.model.job.JobRecordProcessingStats;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.ApplicationService;
import com.cvshealth.cap.registry.service.ExternalApplicationService;

import com.cvshealth.cap.registry.service.notification.NotificationService;
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
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.*;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty("app.job.external-id-sync.enabled")
public class ExternalIdSyncJob {

    private final ApplicationService applicationService;
    private final RegistryService registryService;
    @Nullable
    private final ExternalApplicationService externalApplicationService;
    private final NotificationService notificationService;

    @Value("${app.job.external-id-sync.fetch-batch-size}")
    protected int fetchBatchSize;

    @Value("${app.job.external-id-sync.system-update-message-template}")
    protected String systemUpdateMessageTemplate;

    @Value("${app.job.external-id-sync.docs-url}")
    String updateExternalIdDocsUrl;

    @Value("${app.metrics.prefix}")
    private String metricPrefix;

    private static final String JOB_NAME = "externalIdSyncJob";
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;
    private Map<String, DoubleGauge> jobDetailCountMetrics;

    private final AttributeKey<String> jobNameKey = AttributeKey.stringKey("jobName");
    private final AttributeKey<String> jobStatusKey = AttributeKey.stringKey("status");
    private static final String EXTERNAL_ID_SYSTEM_UPDATE_SUBJECT = "Application %s External ID updated";

    @PostConstruct
    public void init() {
        if (externalApplicationService == null) {
            throw new IllegalStateException("app.job.external-id-sync.enabled requires app.external-application.enabled=true");
        }
        jobDetailCountMetrics = Set.of(METRIC_REGISTRY_JOB_STATS_PROCESSED_COUNT,
                        METRIC_REGISTRY_JOB_STATS_METRIC_UPDATED_COUNT,
                        METRIC_REGISTRY_JOB_STATS_ERROR_COUNT)
                .stream()
                .collect(Collectors.toMap(key -> key, key -> MetricRegistryConfig.buildCountMetric(metricPrefix, key)));
    }

    @Scheduled(cron = "${app.job.external-id-sync.cron-expression}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "${app.job.external-id-sync.job-lock-time}", lockAtLeastFor = "${app.job.external-id-sync.job-lock-time}")
    public void externalIdSyncJob() {
        SecurityHelper.setRegistryUser();

        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS);
        Instant start = Instant.now();
        boolean success = false;
        JobRecordProcessingStats jobStats = new JobRecordProcessingStats();
        try {
            log.info("externalIdSyncJob - started");

            LockAssert.assertLocked();

            Integer totalPages = null;
            PaginationInput paginationInput = new PaginationInput(1, fetchBatchSize);
            ApplicationSearchFilter filter = ApplicationSearchFilter.builder().withExternalIdSet(Boolean.TRUE).build();
            while (totalPages == null || paginationInput.getPageNumber() <= totalPages) {
                PaginatedApplicationResponse response = applicationService.getApplications(paginationInput, filter, null);
                log.info("externalIdSyncJob - batch response[page:{}, totalPages:{}] applications: {}", response.getPageNumber(), response.getTotalPageCount(), response.getApplications().size());

                JobRecordProcessingStats batchJobStats = processBatch(response);
                jobStats.recordBatch(batchJobStats);

                totalPages = response.getTotalPageCount();
                paginationInput.setPageNumber(paginationInput.getPageNumber() + 1);
            }
            success = true;
        } finally {
            Attributes attributes = Attributes.of(
                    jobNameKey, JOB_NAME,
                    jobStatusKey, success ? "success" : "error");

            metricModel.recordDuration(Duration.between(start, Instant.now()).toMillis(), attributes);
            jobDetailCountMetrics.get(METRIC_REGISTRY_JOB_STATS_PROCESSED_COUNT).set(jobStats.getRecordsProcessed(), attributes);
            jobDetailCountMetrics.get(METRIC_REGISTRY_JOB_STATS_METRIC_UPDATED_COUNT).set(jobStats.getRecordsUpdated(), attributes);
            jobDetailCountMetrics.get(METRIC_REGISTRY_JOB_STATS_ERROR_COUNT).set(jobStats.getRecordsErrored(), attributes);
            log.info("externalIdSyncJob - completed: {}", jobStats);
        }
    }

    private JobRecordProcessingStats processBatch(PaginatedApplicationResponse batch) {
        JobRecordProcessingStats batchJobStats = new JobRecordProcessingStats();

        batch.getApplications().forEach(application -> {
            try {
                ExternalApplication externalApplication = externalApplicationService.getExternalApplication(application.getExternalId());
                if (externalApplication == null) {
                    String oldExternalId = application.getExternalId();
                    application.setExternalId(null);    // clear out external ID (creates audit record for Snowball sync)
                    registryService.updateApplicationExternalId(application);
                    batchJobStats.recordUpdated();
                    try {
                        notificationService.sendTeamNotification(buildNotificationRequest(application, oldExternalId));
                    } catch (NotificationServiceException nse) {
                        log.warn(String.format("externalIdSyncJob - unable to send notification message of external ID %s removal to team %s for application %s",
                                oldExternalId, application.getTeam().getName(), application.getName()), nse);
                    }
                }
            } catch (Exception e) {
                log.error(String.format("externalIdSyncJob - unable to sync application %s external ID %s", application.getName(), application.getExternalId()), e);
                batchJobStats.recordErrored();
            }
        });
        batchJobStats.addRecordsProcessed(batch.getApplications().size());
        log.info("externalIdSyncJob - batch processed: {}", batchJobStats);
        return batchJobStats;
    }

    private NotificationRequest buildNotificationRequest(ApplicationView application, String oldExternalId) {
        return NotificationRequest.builder()
                .teamName(application.getTeam().getName())
                .subject(String.format(EXTERNAL_ID_SYSTEM_UPDATE_SUBJECT, application.getName()))
                .template(systemUpdateMessageTemplate)
                .model(Map.of(
                        "teamName", application.getTeam().getName(),
                        "applicationName", application.getName(),
                        "externalId", oldExternalId,
                        "docsUrl", updateExternalIdDocsUrl
                ))
                .build();
    }
}