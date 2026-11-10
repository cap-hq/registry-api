// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.job;

import com.cvshealth.cap.registry.auth.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService;
import io.opentelemetry.api.common.Attributes;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.cvshealth.cap.registry.auth.config.MetricRegistryConfig.METRIC_REGISTRY_JOB_STATS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthorizationSessionCleanupJobTests {

    @Mock
    private CustomJdbcOauth2AuthorizationService authorizationService;
    @Mock
    private Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    @InjectMocks
    private AuthorizationSessionCleanupJob job;

    @Test
    public void testAuthorizationSessionCleanupJob() {
        // given
        LockAssert.TestHelper.makeAllAssertsPass(true);

        CountAndDurationMetricModel mockMetricModel = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetricModel);

        // when
        job.runCleanup();

        // then
        verify(mockMetricModel).recordDuration(anyLong(), any(Attributes.class));
        verify(authorizationService).cleanUpExpiredAuthorizations();
    }
}
