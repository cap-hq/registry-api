// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.config.AuthClientExpirationCheckNightlyJobConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.job.AuthClientExpirationCheckJobStats;
import com.cvshealth.cap.registry.model.job.CheckAuthClientExpirationsRequest;
import com.cvshealth.cap.registry.service.AuthClientExpirationNotificationService;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.METRIC_REGISTRY_JOB_STATS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthClientExpirationCheckNightlyJobTests {

    @Mock
    private AuthClientExpirationNotificationService notificationService;
    @Mock
    private Map<String, CountAndDurationMetricModel> countAndDurationMetrics;
    @Mock
    private AuthClientExpirationCheckNightlyJobConfig jobConfig;

    @InjectMocks
    private AuthClientExpirationCheckNightlyJob job;

    @Test
    public void testAuthClientExpirationCheckNightlyJob_buildsRequestWithLowerBound() {
        // given
        LockAssert.TestHelper.makeAllAssertsPass(true);
        CheckAuthClientExpirationsRequest jobRequest = mock(CheckAuthClientExpirationsRequest.class);
        AuthClientExpirationCheckJobStats jobStats = new AuthClientExpirationCheckJobStats(jobRequest);

        when(jobConfig.getExpirationDaysThreshold()).thenReturn(1);
        when(jobConfig.getExpirationDaysLowerBound()).thenReturn(0);
        when(jobConfig.getAgeDaysThreshold()).thenReturn(9999);
        when(jobConfig.getLegacyClientDeleteAgeDaysThreshold()).thenReturn(9999);
        when(jobConfig.isSendEmailNotifications()).thenReturn(true);
        when(jobConfig.isSendSlackNotifications()).thenReturn(true);
        when(jobConfig.getExpiredClientRetentionDays()).thenReturn(0);
        when(jobConfig.getMessageTemplate()).thenReturn("text/auth-client-expiration-24h.ftl");
        when(jobConfig.getDocsUrl()).thenReturn("https://docs.example.com");
        when(jobConfig.getNotificationSubject()).thenReturn("URGENT: Auth client secret expiring within 24 hours - Action Required");
        when(jobConfig.getLastAccessTokenIssuedWithinHours()).thenReturn(24);
        CountAndDurationMetricModel mockMetricModel = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(mockMetricModel);
        when(notificationService.checkAuthClientsExpirations(any(CheckAuthClientExpirationsRequest.class))).thenReturn(jobStats);

        // when
        job.init();
        job.runScheduledJob();

        // then: request sent to service has the nightly-specific window (lowerBound=0, threshold=1, no deletes)
        ArgumentCaptor<CheckAuthClientExpirationsRequest> requestCaptor = ArgumentCaptor.forClass(CheckAuthClientExpirationsRequest.class);
        verify(notificationService).checkAuthClientsExpirations(requestCaptor.capture());
        CheckAuthClientExpirationsRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.getExpirationDaysThreshold()).isEqualTo(1);
        assertThat(capturedRequest.getMinExpirationDaysThreshold()).isEqualTo(0);
        assertThat(capturedRequest.isDeleteExpiredClients()).isFalse();
        assertThat(capturedRequest.isDeleteExpiredLegacyClients()).isFalse();
        assertThat(capturedRequest.getMessageTemplate()).isEqualTo("text/auth-client-expiration-24h.ftl");
        assertThat(capturedRequest.getNotificationSubject()).isEqualTo("URGENT: Auth client secret expiring within 24 hours - Action Required");
        assertThat(capturedRequest.getLastAccessTokenIssuedWithinHours()).isEqualTo(24);
    }
}
