// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth;

import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

public abstract class RegistryTestContainer {
    @Container
    protected static PostgreSQLContainer<?> postgres = RegistryPostgresqlContainer.getInstance();

    @Container
    protected static DockerComposeContainer<?> oidc = RegistryOidcContainer.getInstance();

    static {
        postgres.start();
        oidc.start();
    }

}
