// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.service.RegistryService;
import com.cvshealth.cap.registry.service.ApplicationService;
import com.cvshealth.cap.registry.service.ExternalApplicationService;
import com.cvshealth.cap.registry.service.notification.NotificationService;
import io.leangen.graphql.execution.ResolutionEnvironment;
import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static com.cvshealth.cap.registry.config.MetricRegistryConfig.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExternalIdSyncJobTests {

    @InjectMocks
    private ExternalIdSyncJob externalIdSyncJob;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private RegistryService registryService;

    @Mock
    private ExternalApplicationService externalApplicationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void testExternalIdSyncJob() throws NotificationServiceException {
        // given
        LockAssert.TestHelper.makeAllAssertsPass(true);
        CountAndDurationMetricModel metricModel = mock(CountAndDurationMetricModel.class);
        when(countAndDurationMetrics.get(METRIC_REGISTRY_JOB_STATS)).thenReturn(metricModel);

        PaginatedApplicationResponse applicationResponse = mock(PaginatedApplicationResponse.class);
        when(applicationService.getApplications(any(PaginationInput.class), any(ApplicationSearchFilter.class),
                nullable(ResolutionEnvironment.class))).thenReturn(applicationResponse);

        ApplicationViewTeam team = ApplicationViewTeam.builder().id(10L).name("team1").build();
        List<ApplicationView> applications = List.of(
                ApplicationView.builder().id(1L).name("app1").externalId("externalId1").team(team).build(),
                ApplicationView.builder().id(1L).name("app2").externalId("externalId2").team(team).build()
        );
        when(applicationResponse.getApplications()).thenReturn(applications);
        when(applicationResponse.getTotalPageCount()).thenReturn(1);

        ExternalApplication externalApplication = mock(ExternalApplication.class);
        when(externalApplicationService.getExternalApplication("externalId1")).thenReturn(null);
        when(externalApplicationService.getExternalApplication("externalId2")).thenReturn(externalApplication);

        // when
        externalIdSyncJob.init();
        externalIdSyncJob.fetchBatchSize = 10;
        externalIdSyncJob.systemUpdateMessageTemplate = "template";
        externalIdSyncJob.updateExternalIdDocsUrl = "docs";
        externalIdSyncJob.externalIdSyncJob();

        // then
        verify(registryService, times(1)).updateApplicationExternalId(any(ApplicationView.class));
        verify(notificationService, times(1)).sendTeamNotification(any(NotificationRequest.class));
    }
}