// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.GenericContainer;

public class RegistryMailContainer extends GenericContainer<RegistryMailContainer> {
    private static final String IMAGE_VERSION = "axllent/mailpit:v1.33.0";
    private static RegistryMailContainer container;

    private RegistryMailContainer() {
        super(IMAGE_VERSION);
    }

    public static RegistryMailContainer getInstance() {
        if (container == null) {
            container = new RegistryMailContainer();
        }
        return container;
    }

    @Override
    public void start() {
        int dockerContainerPort = 1025;
        int dockerLocalPort = 1028;
        super.withReuse(true);
        super.withExposedPorts(dockerContainerPort);
        super.withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(
                new HostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(dockerLocalPort),
                                new ExposedPort(dockerContainerPort)))
        ));
        super.start();
        System.setProperty("spring.mail.port", container.getMappedPort(dockerContainerPort).toString());
    }

    @Override
    public void stop() {
        //do nothing, JVM handles shut down
    }
}