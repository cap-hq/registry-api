// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.config.AuthClientExpirationCheckJobConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.job.AuthClientExpirationCheckJobStats;
import com.cvshealth.cap.registry.model.job.CheckAuthClientExpirationsRequest;
import com.cvshealth.cap.registry.service.AuthClientExpirationNotificationService;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.METRIC_REGISTRY_JOB_STATS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthClientExpirationCheckJobTests {

    @Mock
    private AuthClientExpirationNotificationService notificationService;
    @Mock
    private Map<String, CountAndDurationMetricModel> countAndDurationMetrics;
    @Mock
    private AuthClientExpirationCheckJobConfig jobConfig;

    @InjectMocks
    private AuthClientExpirationCheckJob job;

    @Test
    public void testAuthClientExpirationCheckJob() {
        // given
        LockAssert.TestHelper.makeAllAssertsPass(true);
        CheckAuthClientExpirationsRequest jobRequest = mock(CheckAuthClientExpirationsRequest.class);
        AuthClientExpirationCheckJobStats jobStats = new AuthClientExpirationCheckJobStats(jobRequest);

        when(jobConfig.getExpirationDaysThreshold()).thenReturn(30);
        when(jobConfig.getAgeDaysThreshold()).thenReturn(60);
        when(jobConfig.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(90);
        when(jobConfig.isSendEmailNotifications()).thenReturn(true);
        when(jobConfig.isSendSlackNotifications()).thenReturn(false);
        when(jobConfig.isDeleteExpiredClients()).thenReturn(true);
        when(jobConfig.getExpiredClientRetentionDays()).thenReturn(10);
        when(jobConfig.isDeleteExpiredLegacyClients()).thenReturn(true);
        when(jobConfig.getMessageTemplate()).thenReturn("text/auth-client-expiration.ftl");
        when(jobConfig.getDocsUrl()).thenReturn("https://docs.example.com");
        CountAndDurationMetricModel mockMetricModel = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetricModel);
        when(notificationService.checkAuthClientsExpirations(any(CheckAuthClientExpirationsRequest.class))).thenReturn(jobStats);

        // when
        job.init();
        job.runScheduledJob();

        // then
        verifyNoMoreInteractions(jobConfig);
    }
}
