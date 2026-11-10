// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

public class RegistryTestContainer {
    @LocalServerPort
    protected Integer serverPort;

    protected static PostgreSQLContainer<?> postgres = RegistryPostgresqlContainer.getInstance();

    public static GenericContainer<?> mailpit = RegistryMailContainer.getInstance();

    static {
        // Start containers in proper order
        postgres.start();
        mailpit.start();
    }
}
