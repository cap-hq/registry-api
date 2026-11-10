// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@Slf4j
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
        int dockerContainerPort = 5432;
        super.withDatabaseName("app_registry_auth");
        super.withUsername("root");
        super.withPassword("password");
        super.withReuse(true);
        super.withExposedPorts(dockerContainerPort);
        super.withLogConsumer(new Slf4jLogConsumer(log));
        super.start();
        System.setProperty("spring.datasource.hikari.jdbc-url", container.getJdbcUrl());
        System.setProperty("spring.datasource.hikari.username", container.getUsername());
        System.setProperty("spring.datasource.hikari.password", container.getPassword());
    }

    @Override
    public void stop() {
        //do nothing, JVM handles shut down
    }
}