// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

public class UsageStatisticsJobTests extends RegistryIntegrationTests {

    @Autowired
    private UsageStatisticsJob job;

    @Test
    public void applicationStatisticsJob() {
        job.applicationStatisticsJob();
    }

    @Test
    public void teamStatisticsJob() {
        job.teamStatisticsJob();
    }

    @Test
    public void teamMemberStatisticsJob() {
        job.teamMemberStatisticsJob();
    }

    @Test
    public void memberStatisticsJob() {
        job.memberStatisticsJob();
    }
}
