// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.metric.LongMetricData;
import com.cvshealth.cap.registry.metric.MetricCountStatistic;
import com.cvshealth.cap.registry.persistent.CountStatistic;
import com.cvshealth.cap.registry.service.ApplicationService;
import com.cvshealth.cap.registry.service.MemberService;
import com.cvshealth.cap.registry.service.TeamMemberService;
import com.cvshealth.cap.registry.service.TeamService;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty("app.job.usage-statistics.enabled")
@RequiredArgsConstructor
@Slf4j
public class UsageStatisticsJob {

    private static final String METRIC_BASE = "stats";
    private static final String METRIC_APPLICATION = METRIC_BASE + ".application";
    private static final String METRIC_TEAM = METRIC_BASE + ".team";
    private static final String METRIC_TEAM_MEMBER = METRIC_BASE + ".team.member";
    private static final String METRIC_MEMBER = METRIC_BASE + ".member";
    private static final AttributeKey<String> TYPE_ATTRIBUTE_KEY = AttributeKey.stringKey("type");

    private final ApplicationService applicationService;
    private final MemberService memberService;
    private final TeamService teamService;
    private final TeamMemberService teamMemberService;

    private final Map<String, LongMetricData[]> unprocessedStatsValues = new ConcurrentHashMap<>();

    @Value("${app.metrics.prefix}")
    private String metricPrefix;

    @PostConstruct
    private void init() {
        Meter meter = GlobalOpenTelemetry.meterBuilder(metricPrefix + "." + METRIC_BASE).build();
        Map<String, String> statisticsMetrics = Map.of(
                METRIC_APPLICATION, "Registry Application Count",
                METRIC_TEAM, "Registry Team Count",
                METRIC_TEAM_MEMBER, "Registry Team Member Count",
                METRIC_MEMBER, "Registry Member Count"
        );
        statisticsMetrics.forEach((metricName, description) -> {
            // use buildWithCallback to control when to send a metric value to collector.
            meter.gaugeBuilder(metricPrefix + "." + metricName)
                    .setDescription(description)
                    .setUnit("number")
                    .buildWithCallback(observableMeasurement -> metricCallback(metricName, observableMeasurement));
        });
    }

    @Scheduled(cron = "${app.job.usage-statistics.cron-expression}")
    @SchedulerLock(name = "applicationStatisticsJob", lockAtMostFor = "${app.job.usage-statistics.job-lock-time}", lockAtLeastFor = "${app.job.usage-statistics.job-lock-time}")
    public void applicationStatisticsJob() {
        LockAssert.assertLocked();
        recordStatistics(METRIC_APPLICATION, applicationService.getApplicationCounts());
    }

    @Scheduled(cron = "${app.job.usage-statistics.cron-expression}")
    @SchedulerLock(name = "teamStatisticsJob", lockAtMostFor = "${app.job.usage-statistics.job-lock-time}", lockAtLeastFor = "${app.job.usage-statistics.job-lock-time}")
    public void teamStatisticsJob() {
        LockAssert.assertLocked();
        recordStatistics(METRIC_TEAM, teamService.getTeamCounts());
    }

    @Scheduled(cron = "${app.job.usage-statistics.cron-expression}")
    @SchedulerLock(name = "teamMemberStatisticsJob", lockAtMostFor = "${app.job.usage-statistics.job-lock-time}", lockAtLeastFor = "${app.job.usage-statistics.job-lock-time}")
    public void teamMemberStatisticsJob() {
        LockAssert.assertLocked();
        recordStatistics(METRIC_TEAM_MEMBER, teamMemberService.getTeamMemberCounts());
    }

    @Scheduled(cron = "${app.job.usage-statistics.cron-expression}")
    @SchedulerLock(name = "memberStatisticsJob", lockAtMostFor = "${app.job.usage-statistics.job-lock-time}", lockAtLeastFor = "${app.job.usage-statistics.job-lock-time}")
    public void memberStatisticsJob() {
        LockAssert.assertLocked();
        recordStatistics(METRIC_MEMBER, List.of(new MetricCountStatistic(memberService.getMemberCount(), null)));
    }

    /**
     * Record metric values by queuing value(s) for next periodic metric collection.
     */
    private void recordStatistics(String metricName, List<CountStatistic> stats) {
        LongMetricData[] data = stats.stream()
                .peek(stat -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Registry Stats [{}={} (type:{})]", metricName, stat.getCount(), stat.getType());
                    }
                })
                .map(stat -> {
                    Attributes attributes = stat.getType() == null ? Attributes.empty() : Attributes.of(TYPE_ATTRIBUTE_KEY, stat.getType());
                    return new LongMetricData(stat.getCount(), attributes);
                })
                .toArray(LongMetricData[]::new);
        unprocessedStatsValues.put(metricName, data);
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
