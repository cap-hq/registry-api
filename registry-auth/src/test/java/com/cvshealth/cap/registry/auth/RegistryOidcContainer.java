// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.File;

@Slf4j
public class RegistryOidcContainer extends DockerComposeContainer<RegistryOidcContainer> {
    private static RegistryOidcContainer container;

    private RegistryOidcContainer(File composeFile) {
        super(composeFile);
    }

    public static RegistryOidcContainer getInstance() {
        if (container == null) {
            container = new RegistryOidcContainer(new File("src/test/resources/docker-compose-oidc-mock.yml"));
        }
        return container;
    }

    @Override
    public void start() {
        super.withExposedService("oidc-server-mock", 80);
        super.withLogConsumer("mock-oidc", new Slf4jLogConsumer(log));
        super.start();
    }
}