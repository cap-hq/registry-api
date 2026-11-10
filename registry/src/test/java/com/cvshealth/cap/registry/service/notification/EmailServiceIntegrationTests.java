// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

public class EmailServiceIntegrationTests extends RegistryIntegrationTests {

    @Autowired
    private EmailService emailService;

    @Test
    public void testSendEmail() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Boolean> result = emailService.sendMessage("test subject", "some message", List.of("registry-test@example.com"));
        Boolean sent = result.get(10, TimeUnit.SECONDS);
        assertThat(sent).isTrue();
    }
}
