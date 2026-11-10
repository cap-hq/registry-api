// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.job;

import com.cvshealth.cap.registry.auth.metric.CountAndDurationMetricModel;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.cvshealth.cap.registry.auth.config.MetricRegistryConfig.METRIC_REGISTRY_JOB_STATS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class AuthEventPartitionManagerTests {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private Map<String, CountAndDurationMetricModel> countAndDurationMetrics;
    @Mock
    private LockingTaskExecutor lockingTaskExecutor;

    @Spy
    @InjectMocks
    private AuthEventPartitionManager job;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(job, "retentionDays", 7);
        ReflectionTestUtils.setField(job, "jobLockTime", Duration.ofMinutes(10));
        ReflectionTestUtils.setField(job, "dailyTimezone", "UTC");
        job.init();
    }

    // --- onApplicationReady ---

    @Test
    void onApplicationReady_delegatesToRunMaintenanceWithLock() {
        doNothing().when(job).runMaintenanceWithLock("startup");

        job.onApplicationReady();

        verify(job).runMaintenanceWithLock("startup");
    }

    // --- runMaintenanceWithLock ---

    @Test
    void runMaintenanceWithLock_executesMaintenanceUnderLock() throws Throwable {
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mock(CountAndDurationMetricModel.class));
        doAnswer(inv -> { ((LockingTaskExecutor.Task) inv.getArgument(0)).call(); return null; })
                .when(lockingTaskExecutor).executeWithLock(any(LockingTaskExecutor.Task.class), any(LockConfiguration.class));
        doReturn(true).when(job).runMaintenance();

        job.runMaintenanceWithLock("startup");

        verify(job).runMaintenance();
    }

    @Test
    void runMaintenanceWithLock_swallowsLockException() throws Throwable {
        CountAndDurationMetricModel mockMetric = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetric);
        doThrow(new RuntimeException("lock failure"))
                .when(lockingTaskExecutor).executeWithLock(any(LockingTaskExecutor.Task.class), any(LockConfiguration.class));

        job.runMaintenanceWithLock("startup"); // should not throw
    }

    @Test
    void runMaintenanceWithLock_recordsSuccessMetric_whenMaintenanceSucceeds() throws Throwable {
        CountAndDurationMetricModel mockMetric = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetric);
        doAnswer(inv -> { ((LockingTaskExecutor.Task) inv.getArgument(0)).call(); return null; })
                .when(lockingTaskExecutor).executeWithLock(any(LockingTaskExecutor.Task.class), any(LockConfiguration.class));
        doReturn(true).when(job).runMaintenance();

        job.runMaintenanceWithLock("startup");

        ArgumentCaptor<Attributes> captor = ArgumentCaptor.forClass(Attributes.class);
        verify(mockMetric).recordDuration(anyLong(), captor.capture());
        assertThat(captor.getValue().get(AttributeKey.stringKey("status"))).isEqualTo("success");
        assertThat(captor.getValue().get(AttributeKey.stringKey("trigger"))).isEqualTo("startup");
    }

    @Test
    void runMaintenanceWithLock_recordsErrorMetric_whenMaintenanceReturnsFalse() throws Throwable {
        CountAndDurationMetricModel mockMetric = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetric);
        doAnswer(inv -> { ((LockingTaskExecutor.Task) inv.getArgument(0)).call(); return null; })
                .when(lockingTaskExecutor).executeWithLock(any(LockingTaskExecutor.Task.class), any(LockConfiguration.class));
        doReturn(false).when(job).runMaintenance();

        job.runMaintenanceWithLock("startup");

        ArgumentCaptor<Attributes> captor = ArgumentCaptor.forClass(Attributes.class);
        verify(mockMetric).recordDuration(anyLong(), captor.capture());
        assertThat(captor.getValue().get(AttributeKey.stringKey("status"))).isEqualTo("error");
        assertThat(captor.getValue().get(AttributeKey.stringKey("trigger"))).isEqualTo("startup");
    }

    @Test
    void runMaintenanceWithLock_recordsErrorMetric_whenLockThrows() throws Throwable {
        CountAndDurationMetricModel mockMetric = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetric);
        doThrow(new RuntimeException("lock failure"))
                .when(lockingTaskExecutor).executeWithLock(any(LockingTaskExecutor.Task.class), any(LockConfiguration.class));

        job.runMaintenanceWithLock("startup");

        ArgumentCaptor<Attributes> captor = ArgumentCaptor.forClass(Attributes.class);
        verify(mockMetric).recordDuration(anyLong(), captor.capture());
        assertThat(captor.getValue().get(AttributeKey.stringKey("status"))).isEqualTo("error");
        assertThat(captor.getValue().get(AttributeKey.stringKey("trigger"))).isEqualTo("startup");
    }

    // --- runMaintenanceJob ---

    @Test
    void runMaintenanceJob_recordsMetric_regardlessOfOutcome() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        CountAndDurationMetricModel mockMetric = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetric);
        doReturn(false).when(job).runMaintenance();

        job.runMaintenanceJob();

        verify(mockMetric).recordDuration(anyLong(), any(Attributes.class));
    }

    // --- runMaintenance: aggregation ---

    @Test
    void runMaintenance_aggregatesDaysMissingFromDailyTable() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        LocalDate dayBefore = today.minusDays(2);

        doReturn(List.of(dayBefore, yesterday)).when(job).getDistinctAuthenticationDays(today);
        doReturn(false).when(job).aggregatedExists(any());
        doNothing().when(job).aggregatePartition(any());
        doNothing().when(job).createPartitionIfAbsent(any());

        boolean result = job.runMaintenance();

        assertThat(result).isTrue();
        verify(job).aggregatePartition(dayBefore);
        verify(job).aggregatePartition(yesterday);
    }

    @Test
    void runMaintenance_skipsAggregation_whenAlreadyAggregated() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);

        doReturn(List.of(yesterday)).when(job).getDistinctAuthenticationDays(today);
        doReturn(true).when(job).aggregatedExists(yesterday);
        doNothing().when(job).createPartitionIfAbsent(any());

        job.runMaintenance();

        verify(job, never()).aggregatePartition(any());
    }

    @Test
    void runMaintenance_skipsAggregation_whenDayIsLocalToday() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        doReturn(List.of(today)).when(job).getDistinctAuthenticationDays(today);
        doNothing().when(job).createPartitionIfAbsent(any());

        job.runMaintenance();

        verify(job, never()).aggregatedExists(any());
        verify(job, never()).aggregatePartition(any());
    }

    @Test
    void runMaintenance_skipsAggregation_whenNoDaysWithData() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        doReturn(List.of()).when(job).getDistinctAuthenticationDays(today);
        doNothing().when(job).createPartitionIfAbsent(any());

        job.runMaintenance();

        verify(job, never()).aggregatedExists(any());
        verify(job, never()).aggregatePartition(any());
    }

    // --- runMaintenance: partition creation ---

    @Test
    void runMaintenance_createsPartitions_forTodayAndTomorrow() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);

        doReturn(List.of()).when(job).getDistinctAuthenticationDays(today);
        doNothing().when(job).createPartitionIfAbsent(any());

        job.runMaintenance();

        verify(job).createPartitionIfAbsent(today);
        verify(job).createPartitionIfAbsent(today.plusDays(1));
    }

    // --- runMaintenance: expiry ---

    @Test
    void runMaintenance_dropsExpiredPartition() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate expired = today.minusDays(9);
        LocalDate recent = today.minusDays(2);

        doReturn(List.of(expired, recent)).when(job).getDistinctAuthenticationDays(today);
        doReturn(true).when(job).aggregatedExists(any());
        doNothing().when(job).createPartitionIfAbsent(any());
        doReturn(List.of(expired)).when(job).getExpiredPartitionDates(any());
        doNothing().when(job).dropPartition(any());

        job.runMaintenance();

        verify(job).dropPartition(expired);
        verify(job, never()).dropPartition(recent);
    }

    @Test
    void runMaintenance_dropsMultipleExpiredPartitions() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate expired1 = today.minusDays(10);
        LocalDate expired2 = today.minusDays(9);
        LocalDate expired3 = today.minusDays(8);
        LocalDate recent = today.minusDays(2);

        doReturn(List.of(expired1, expired2, expired3, recent)).when(job).getDistinctAuthenticationDays(today);
        doReturn(true).when(job).aggregatedExists(any());
        doNothing().when(job).createPartitionIfAbsent(any());
        doReturn(List.of(expired1, expired2, expired3)).when(job).getExpiredPartitionDates(any());
        doNothing().when(job).dropPartition(any());

        job.runMaintenance();

        verify(job).dropPartition(expired1);
        verify(job).dropPartition(expired2);
        verify(job).dropPartition(expired3);
        verify(job, never()).dropPartition(recent);
    }

    @Test
    void runMaintenance_doesNotDropPartitionsWithinRetentionWindow() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate withinRetention = today.minusDays(3);

        doReturn(List.of(withinRetention)).when(job).getDistinctAuthenticationDays(today);
        doReturn(true).when(job).aggregatedExists(withinRetention);
        doNothing().when(job).createPartitionIfAbsent(any());
        doReturn(List.of()).when(job).getExpiredPartitionDates(any());

        job.runMaintenance();

        verify(job, never()).dropPartition(any());
    }

    @Test
    void runMaintenance_skipsDropWhenNoExpiredPartitionsExistInCatalog() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate expired = today.minusDays(9);

        doReturn(List.of(expired)).when(job).getDistinctAuthenticationDays(today);
        doReturn(true).when(job).aggregatedExists(expired);
        doNothing().when(job).createPartitionIfAbsent(any());
        doReturn(List.of()).when(job).getExpiredPartitionDates(any());

        job.runMaintenance();

        verify(job, never()).dropPartition(any());
    }

    @Test
    void runMaintenance_respectsCustomRetentionDays() {
        ReflectionTestUtils.setField(job, "retentionDays", 3);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate expired = today.minusDays(5);
        LocalDate withinRetention = today.minusDays(2);

        doReturn(List.of(expired, withinRetention)).when(job).getDistinctAuthenticationDays(today);
        doReturn(true).when(job).aggregatedExists(any());
        doNothing().when(job).createPartitionIfAbsent(any());
        doReturn(List.of(expired)).when(job).getExpiredPartitionDates(any());
        doNothing().when(job).dropPartition(any());

        job.runMaintenance();

        verify(job).dropPartition(expired);
        verify(job, never()).dropPartition(withinRetention);
    }

    // --- runMaintenance: error handling ---

    @Test
    void runMaintenance_returnsFalse_onException() {
        doThrow(new RuntimeException("DB unavailable")).when(job).getDistinctAuthenticationDays(any());

        boolean result = job.runMaintenance();

        assertThat(result).isFalse();
    }

    // --- dropPartition ---

    @Test
    void dropPartition_aggregatesBeforeDroppingWhenNotAlreadyAggregated() throws Exception {
        LocalDate date = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(9);
        javax.sql.DataSource mockDataSource = mock(javax.sql.DataSource.class);
        java.sql.Connection mockConn = mock(java.sql.Connection.class);
        java.sql.Statement mockStmt = mock(java.sql.Statement.class);
        when(jdbcTemplate.getDataSource()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.createStatement()).thenReturn(mockStmt);

        doReturn(false).when(job).aggregatedExists(date);
        doNothing().when(job).aggregatePartition(date);

        job.dropPartition(date);

        verify(job).aggregatePartition(date);
    }

    @Test
    void dropPartition_skipsAggregationWhenAlreadyAggregated() throws Exception {
        LocalDate date = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(9);
        javax.sql.DataSource mockDataSource = mock(javax.sql.DataSource.class);
        java.sql.Connection mockConn = mock(java.sql.Connection.class);
        java.sql.Statement mockStmt = mock(java.sql.Statement.class);
        when(jdbcTemplate.getDataSource()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.createStatement()).thenReturn(mockStmt);

        doReturn(true).when(job).aggregatedExists(date);

        job.dropPartition(date);

        verify(job, never()).aggregatePartition(any());
    }

    @Test
    void dropPartition_detachesConcurrentlyWhenNotPending() throws Exception {
        LocalDate date = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(9);
        javax.sql.DataSource mockDataSource = mock(javax.sql.DataSource.class);
        java.sql.Connection mockConn = mock(java.sql.Connection.class);
        java.sql.Statement mockStmt = mock(java.sql.Statement.class);
        when(jdbcTemplate.getDataSource()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.createStatement()).thenReturn(mockStmt);

        doReturn(true).when(job).aggregatedExists(date);
        doReturn(false).when(job).isDetachPending(anyString());

        job.dropPartition(date);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockStmt).execute(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("CONCURRENTLY");
        assertThat(sqlCaptor.getValue()).doesNotContain("FINALIZE");
    }

    @Test
    void dropPartition_finalizesDetachWhenDetachPending() throws Exception {
        LocalDate date = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(9);
        javax.sql.DataSource mockDataSource = mock(javax.sql.DataSource.class);
        java.sql.Connection mockConn = mock(java.sql.Connection.class);
        java.sql.Statement mockStmt = mock(java.sql.Statement.class);
        when(jdbcTemplate.getDataSource()).thenReturn(mockDataSource);
        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.createStatement()).thenReturn(mockStmt);

        doReturn(true).when(job).aggregatedExists(date);
        doReturn(true).when(job).isDetachPending(anyString());

        job.dropPartition(date);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockStmt).execute(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("FINALIZE");
        assertThat(sqlCaptor.getValue()).doesNotContain("CONCURRENTLY");
    }

    // --- createPartitionIfAbsent ---

    @Test
    void createPartitionIfAbsent_skipsWhenAlreadyExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any())).thenReturn(true);

        job.createPartitionIfAbsent(LocalDate.now(java.time.ZoneOffset.UTC));

        verify(jdbcTemplate, never()).execute(anyString());
    }

    // --- aggregatePartition ---

    @Test
    void aggregatePartition_utcTimezone_usesMidnightBoundsAndUtcDateCast() {
        LocalDate date = LocalDate.of(2025, 7, 10);

        job.aggregatePartition(date);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> fromCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> toCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo("2025-07-10 00:00:00");
        assertThat(toCaptor.getValue()).isEqualTo("2025-07-11 00:00:00");
        assertThat(sqlCaptor.getValue()).contains("AT TIME ZONE 'UTC'");
    }

    @Test
    void aggregatePartition_nonUtcTimezone_shiftsUtcBoundsAndUsesOffsetDateCast() {
        ReflectionTestUtils.setField(job, "dailyTimezone", "+05:30");
        job.init();
        LocalDate date = LocalDate.of(2025, 7, 10);

        job.aggregatePartition(date);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> fromCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> toCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), fromCaptor.capture(), toCaptor.capture());
        // 2025-07-10 00:00:00 +05:30 = 2025-07-09 18:30:00 UTC
        assertThat(fromCaptor.getValue()).isEqualTo("2025-07-09 18:30:00");
        // 2025-07-11 00:00:00 +05:30 = 2025-07-10 18:30:00 UTC
        assertThat(toCaptor.getValue()).isEqualTo("2025-07-10 18:30:00");
        assertThat(sqlCaptor.getValue()).contains("AT TIME ZONE '+05:30'");
    }
}
