// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.job;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

public class AzureManagerSyncJobTests extends RegistryIntegrationTests {

    @Autowired
    private AzureManagerSyncJob job;

    @Test
    @Sql(scripts = "classpath:composite-data.sql")
    public void testAzureUserSyncJob() {
        job.azureManagerSyncJob();
    }
}
