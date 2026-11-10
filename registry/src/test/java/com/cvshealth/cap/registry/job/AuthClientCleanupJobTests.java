// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.config.AuthClientCleanupJobConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.job.AuthClientCleanupJobResponse;
import com.cvshealth.cap.registry.service.AuthClientCleanupService;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.METRIC_REGISTRY_JOB_STATS;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AuthClientCleanupJobTests {

    @Mock
    private AuthClientCleanupService authClientCleanupService;
    @Mock
    private AuthClientCleanupJobConfig jobConfig;
    @Mock
    private Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    @InjectMocks
    private AuthClientCleanupJob job;

    @BeforeEach
    void setUp() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        CountAndDurationMetricModel mockMetricModel = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetricModel);
        when(jobConfig.getMinAgeDays()).thenReturn(30);
        when(jobConfig.getInactivityDays()).thenReturn(30);
        when(authClientCleanupService.runCleanup(anyInt(), anyInt())).thenReturn(
                AuthClientCleanupJobResponse.builder().candidates(List.of()).deleted(0).failed(0).build());
    }

    @Test
    public void whenRun_delegatesToServiceWithConfigValues() {
        job.run();

        verify(authClientCleanupService).runCleanup(30, 30);
    }

    @Test
    public void whenRunScheduledJob_thenRunsWithLockAndMetrics() {
        job.runScheduledJob();

        verify(authClientCleanupService).runCleanup(30, 30);
    }
}
