// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry;

import org.testcontainers.containers.PostgreSQLContainer;

public class RegistryPostgresqlContainer extends PostgreSQLContainer<RegistryPostgresqlContainer> {
    private static final String IMAGE_VERSION = "postgres:15-alpine";
    private static RegistryPostgresqlContainer container;

    private RegistryPostgresqlContainer() {
        super(IMAGE_VERSION);
    }

    public static RegistryPostgresqlContainer getInstance() {
        if (container == null) {
            container = new RegistryPostgresqlContainer();
        }
        return container;
    }

    @Override
    public void start() {
        super.withDatabaseName("app_registry");
        super.withUsername("root");
        super.withPassword("password");
        super.withReuse(true);
        super.start();
        System.setProperty("spring.datasource.url", container.getJdbcUrl());
        System.setProperty("spring.datasource.username", container.getUsername());
        System.setProperty("spring.datasource.password", container.getPassword());
        System.setProperty("spring.flyway.datasource.url", container.getJdbcUrl());
        System.setProperty("spring.flyway.datasource.user", container.getUsername());
        System.setProperty("spring.flyway.datasource.password", container.getPassword());
    }

    @Override
    public void stop() {
        //do nothing, JVM handles shut down
    }
}