// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry;

import com.cvshealth.cap.registry.azure.MockGraphConfiguration;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.security.TestSecurityConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestSecurityConfiguration.class, MockGraphConfiguration.class})
public class RegistryIntegrationTests extends RegistryTestContainer {

    @LocalServerPort
    protected Integer serverPort;

    // Counter for generating unique names to avoid conflicts in parallel test execution
    private static final AtomicInteger uniqueCounter = new AtomicInteger(0);

    /**
     * Generates a unique name by appending a counter to the base name.
     * Use this in tests to avoid duplicate key violations when tests run in parallel.
     */
    protected String uniqueName(String baseName) {
        return baseName + "-" + uniqueCounter.incrementAndGet();
    }

    /**
     * Generates a normalized version of a name (lowercase, alphanumeric only).
     */
    protected String normalizeName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Builds a TeamEntity with a unique name to avoid conflicts in parallel test execution.
     */
    protected TeamEntity buildTeam(String baseName, TeamType type) {
        String name = uniqueName(baseName);
        return TeamEntity.builder()
                .name(name)
                .nameNormalized(normalizeName(name))
                .type(type)
                .build();
    }
}
