// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.job;

import com.cvshealth.cap.registry.auth.metric.CountAndDurationMetricModel;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static com.cvshealth.cap.registry.auth.config.MetricRegistryConfig.METRIC_REGISTRY_JOB_STATS;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventPartitionManager {

    private static final String JOB_NAME = "authEventPartitionManager";
    private static final DateTimeFormatter PARTITION_FMT = DateTimeFormatter.ofPattern("yyyy_MM_dd");
    private static final DateTimeFormatter DAILY_PARTITION_FMT = DateTimeFormatter.ofPattern("yyyy_MM");
    private static final DateTimeFormatter PG_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PG_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern PARTITION_NAME_PATTERN =
            Pattern.compile("^authentication_events_\\d{4}_\\d{2}_\\d{2}$");

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;
    private final LockingTaskExecutor lockingTaskExecutor;

    @Value("${app.job.auth-event-partition.retention-days:7}")
    private int retentionDays;

    @Value("${app.job.auth-event-partition.job-lock-time}")
    private Duration jobLockTime;

    @Value("${app.authentication.daily-timezone:UTC}")
    private String dailyTimezone;

    private ZoneId dailyZoneId;
    private String pgOffset;

    @PostConstruct
    void init() {
        dailyZoneId = ZoneId.of(dailyTimezone);
        String id = dailyZoneId.getId();
        pgOffset = "Z".equals(id) ? "UTC" : id;
    }

    private final AttributeKey<String> jobNameKey = AttributeKey.stringKey("jobName");
    private final AttributeKey<String> jobStatusKey = AttributeKey.stringKey("status");
    private final AttributeKey<String> triggerKey = AttributeKey.stringKey("trigger");

    @Scheduled(cron = "${app.job.auth-event-partition.cron-expression}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${app.job.auth-event-partition.job-lock-time}",
            lockAtLeastFor = "${app.job.auth-event-partition.job-lock-time}")
    public void runMaintenanceJob() {
        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS);
        Instant start = Instant.now();

        boolean success = false;
        try {
            LockAssert.assertLocked();
            success = runMaintenance();
        } finally {
            Attributes attributes = Attributes.of(
                    jobNameKey, JOB_NAME,
                    jobStatusKey, success ? "success" : "error",
                    triggerKey, "scheduled");
            metricModel.recordDuration(Duration.between(start, Instant.now()).toMillis(), attributes);
        }
    }

    @Async("authEventExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        runMaintenanceWithLock("startup");
    }

    public void runMaintenanceWithLock(String trigger) {
        CountAndDurationMetricModel metricModel = countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS);
        Instant start = Instant.now();
        AtomicBoolean success = new AtomicBoolean(false);
        try {
            lockingTaskExecutor.executeWithLock(
                    (LockingTaskExecutor.Task) () -> success.set(runMaintenance()),
                    new LockConfiguration(Instant.now(), JOB_NAME, jobLockTime, jobLockTime));
        } catch (Throwable e) {
            log.error("runMaintenanceWithLock: Failed to run partition maintenance", e);
        } finally {
            Attributes attributes = Attributes.of(
                    jobNameKey, JOB_NAME,
                    jobStatusKey, success.get() ? "success" : "error",
                    triggerKey, trigger);
            metricModel.recordDuration(Duration.between(start, Instant.now()).toMillis(), attributes);
        }
    }

    public boolean runMaintenance() {
        boolean success = false;
        try {
            log.info("runMaintenance: Starting auth event partition maintenance");
            LocalDate today = LocalDate.now(ZoneOffset.UTC); //  Raw-event database partitions boundaries are UTC-midnight-aligned, so the date driving partition must explicitly be a UTC date.

            // create new daily-summary partitions (monthly)
            YearMonth thisMonth = YearMonth.from(today);
            createDailyPartitionIfAbsent(thisMonth);
            createDailyPartitionIfAbsent(thisMonth.plusMonths(1));

            // aggregate any historical days missing from authentication_events_daily;
            // skip the current local day — its UTC window is not yet complete.
            LocalDate localToday = LocalDate.now(dailyZoneId);
            List<LocalDate> daysWithData = getDistinctAuthenticationDays(today);
            for (LocalDate day : daysWithData) {
                if (day.isBefore(localToday) && !aggregatedExists(day)) {
                    aggregatePartition(day);
                }
            }

            // create new raw-event partitions
            createPartitionIfAbsent(today);
            createPartitionIfAbsent(today.plusDays(1));

            // remove partitions older than the retention period, including empty ones
            LocalDate retentionCutoff = today.minusDays(retentionDays);
            for (LocalDate day : getExpiredPartitionDates(retentionCutoff)) {
                dropPartition(day);
            }
            success = true;
            log.info("runMaintenance: Successfully completed auth event partition maintenance");
        } catch (Exception e) {
            log.error("runMaintenance: Failed to complete auth event partition maintenance", e);
        }
        return success;
    }

    void createPartitionIfAbsent(LocalDate date) {
        String name = partitionName(date);
        if (partitionExists(date)) {
            log.info("runMaintenance: Partition {} already exists, skipping creation", name);
            return;
        }
        String sql = String.format(
                "CREATE TABLE %s PARTITION OF authentication_events FOR VALUES FROM ('%s') TO ('%s')",
                name, date.format(PG_FMT), date.plusDays(1).format(PG_FMT));
        jdbcTemplate.execute(sql);
        log.info("runMaintenance: Created partition {}", name);
    }

    void createDailyPartitionIfAbsent(YearMonth month) {
        String name = dailyPartitionName(month);
        if (dailyPartitionExists(month)) {
            log.info("runMaintenance: Daily partition {} already exists, skipping creation", name);
            return;
        }
        String sql = String.format(
                "CREATE TABLE %s PARTITION OF authentication_events_daily FOR VALUES FROM ('%s') TO ('%s')",
                name, month.atDay(1).format(PG_FMT), month.plusMonths(1).atDay(1).format(PG_FMT));
        jdbcTemplate.execute(sql);
        log.info("runMaintenance: Created daily partition {}", name);
    }

    boolean dailyPartitionExists(YearMonth month) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = current_schema() AND tablename = ?)",
                Boolean.class, dailyPartitionName(month)));
    }

    private String dailyPartitionName(YearMonth month) {
        return "authentication_events_daily_" + month.format(DAILY_PARTITION_FMT);
    }

    void aggregatePartition(LocalDate date) {
        ZonedDateTime from = date.atStartOfDay(dailyZoneId).withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime to = date.plusDays(1).atStartOfDay(dailyZoneId).withZoneSameInstant(ZoneOffset.UTC);
        String fromDate = from.format(PG_DATETIME_FMT);
        String toDate = to.format(PG_DATETIME_FMT);
        String dateCast = "(ae.authenticated_at AT TIME ZONE 'UTC' AT TIME ZONE '" + pgOffset + "')::date";
        String sql = ("""
                INSERT INTO authentication_events_daily
                    (event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, external_alias, success, event_count, first_authentication, last_authentication, event_details)
                SELECT
                    %1$s,
                    ae.client_id,
                    COALESCE(ae.client_name, ''),
                    COALESCE(ae.grant_type, 'UNKNOWN'),
                    COALESCE(ae.principal_name, ''),
                    COALESCE(ae.principal_oid, ''),
                    COALESCE(ae.agent, ''),
                    COALESCE(ae.external_alias, ''),
                    ae.success,
                    COUNT(*),
                    MIN(ae.authenticated_at),
                    MAX(ae.authenticated_at),
                    case when COALESCE(ae.grant_type, 'UNKNOWN') IN ('authorization_code', 'refresh_token') THEN jsonb_build_object(
                        'sid', COUNT(DISTINCT ae.event_attributes ->> 'sid') FILTER (WHERE ae.event_attributes -> 'sid' IS NOT NULL),
                        'App.OPA', COUNT(*) FILTER (WHERE ae.event_attributes -> 'scope' ?? 'App.OPA'),
                        'redirect_uris', to_jsonb(array_agg(DISTINCT ae.event_attributes ->> 'redirect_uri') FILTER (WHERE ae.event_attributes ->> 'redirect_uri' IS NOT NULL))
                    ) ELSE null END
                FROM authentication_events ae
                WHERE ae.authenticated_at >= ?::timestamp
                  AND ae.authenticated_at <  ?::timestamp
                GROUP BY %1$s, ae.client_id, COALESCE(ae.client_name, ''),
                         COALESCE(ae.grant_type, 'UNKNOWN'), COALESCE(ae.principal_name, ''),
                         COALESCE(ae.principal_oid, ''), COALESCE(ae.agent, ''), COALESCE(ae.external_alias, ''), ae.success
                ON CONFLICT (event_date, client_id, client_name, grant_type, principal_name, principal_oid, agent, external_alias, success) DO NOTHING
                """).formatted(dateCast);
        int rows = jdbcTemplate.update(sql, fromDate, toDate);
        log.info("runMaintenance: Aggregated {} group(s) from partition for {}", rows, date);
    }

    List<LocalDate> getDistinctAuthenticationDays(LocalDate before) {
        LocalDate from = before.minusDays(retentionDays + 2);
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT (authenticated_at AT TIME ZONE 'UTC' AT TIME ZONE '" + pgOffset + "')::date " +
                "FROM authentication_events " +
                "WHERE authenticated_at >= ?::timestamp AND authenticated_at < ?::timestamp ORDER BY 1",
                java.sql.Date.class, from.format(PG_FMT), before.format(PG_FMT))
                .stream().map(java.sql.Date::toLocalDate).toList();
    }

    boolean aggregatedExists(LocalDate date) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM authentication_events_daily WHERE event_date = ?)",
                Boolean.class, date));
    }

    List<LocalDate> getExpiredPartitionDates(LocalDate retentionCutoff) {
        return jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() " +
                "AND tablename ~ '^authentication_events_[0-9]{4}_[0-9]{2}_[0-9]{2}$'",
                String.class)
                .stream()
                .map(name -> LocalDate.parse(name.substring("authentication_events_".length()), PARTITION_FMT))
                .filter(date -> date.isBefore(retentionCutoff))
                .toList();
    }

    void dropPartition(LocalDate utcDate) {
        String name = partitionName(utcDate);
        assertValidPartitionName(name);
        // For non-UTC offsets a single UTC partition spans two local dates (one at each day boundary).
        // Ensure both are aggregated before dropping so tail events are not destroyed.
        LocalDate localStart = utcDate.atStartOfDay(ZoneOffset.UTC).withZoneSameInstant(dailyZoneId).toLocalDate();
        LocalDate localEnd = utcDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusNanos(1).withZoneSameInstant(dailyZoneId).toLocalDate();
        if (!aggregatedExists(localStart)) {
            aggregatePartition(localStart);
        }
        if (!localStart.equals(localEnd) && !aggregatedExists(localEnd)) {
            aggregatePartition(localEnd);
        }
        // DETACH PARTITION CONCURRENTLY and FINALIZE cannot run inside a transaction block;
        // use a dedicated autocommit connection for both paths.
        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            if (isDetachPending(name)) {
                // A prior DETACH CONCURRENTLY was interrupted; complete it before dropping.
                log.info("runMaintenance: Partition {} is in detach-pending state, finalizing", name);
                stmt.execute("ALTER TABLE authentication_events DETACH PARTITION " + name + " FINALIZE");
            } else {
                stmt.execute("ALTER TABLE authentication_events DETACH PARTITION " + name + " CONCURRENTLY");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to detach partition " + name, e);
        }
        log.info("runMaintenance: Detached partition {}", name);
        jdbcTemplate.execute("DROP TABLE " + name);
        log.info("runMaintenance: Dropped partition {}", name);
    }

    boolean isDetachPending(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (" +
                "SELECT 1 FROM pg_inherits JOIN pg_class ON inhrelid = pg_class.oid " +
                "WHERE relname = ? AND inhdetachpending = true)",
                Boolean.class, name));
    }

    private void assertValidPartitionName(String name) {
        if (!PARTITION_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid partition name: " + name);
        }
    }

    boolean partitionExists(LocalDate date) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = current_schema() AND tablename = ?)",
                Boolean.class, partitionName(date)));
    }

    private String partitionName(LocalDate date) {
        return "authentication_events_" + date.format(PARTITION_FMT);
    }
}
