// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTests {

    @InjectMocks
    private EmailService emailService;

    @Mock
    private JavaMailSender emailSender;

    @Mock
    private Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    @Mock
    private CountAndDurationMetricModel metricModel;

    @Mock
    private MailException mailException;

    @Test
    public void testSendEmail_sendEnabledFalse() {
        // when
        emailService.enabled = false;
        Boolean sent = sendTestEmail();

        // then
        assertThat(sent).isFalse();
        verifyNoInteractions(emailSender);
        verifyNoInteractions(metricModel);
    }

    @Test
    public void testSendEmail_sendEnabledReturnsSuccess() {
        // given
        emailService.enabled = true;
        when(countAndDurationMetrics.get(anyString())).thenReturn(metricModel);

        // when
        Boolean sent = sendTestEmail();

        // then
        assertThat(sent).isTrue();
        verify(emailSender).send(any(SimpleMailMessage.class));
        verify(metricModel).recordDuration(anyLong(), any());
    }

    @Test
    public void testSendEmail_EnabledReturnsException() {
        // given
        emailService.enabled = true;
        when(countAndDurationMetrics.get(anyString())).thenReturn(metricModel);
        doThrow(mailException).when(emailSender).send(any(SimpleMailMessage.class));

        // when
        Boolean sent = sendTestEmail();

        // then
        assertThat(sent).isFalse();
        verify(emailSender).send(any(SimpleMailMessage.class));
        verify(metricModel).recordDuration(anyLong(), any());
    }

    private Boolean sendTestEmail() {
        try {
            CompletableFuture<Boolean> result = emailService.sendMessage("test subject", "some message", List.of("registry-test@example.com"));
            return result.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            return null;
        }
    }
}
