// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetricModel;
import io.opentelemetry.api.common.Attributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender emailSender;
    private final Map<String, CountAndDurationMetricModel> countAndDurationMetrics;

    @Value("${spring.mail.properties.mail.default.from}")
    private String defaultFrom;

    @Value("${app.environment-name}")
    private String environmentName;

    @Value("${app.support.contact-info}")
    private String supportContact;

    @Value("${spring.mail.properties.mail.enabled:false}")
    protected boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void doSomethingAfterStartup() {
        log.info("Email enabled: " + enabled);
    }

    @Async
    public CompletableFuture<Boolean> sendMessage(String subject, String text, List<String> toEmails) {
        boolean sent = false;
        if (enabled) {
            if (log.isDebugEnabled()) {
                log.debug("Sending email notification :: " + subject + " :: " + text);
            }
            CountAndDurationMetricModel metric = countAndDurationMetrics.get(MetricRegistryConfig.METRIC_REGISTRY_NOTIFICATION);
            long start = System.currentTimeMillis();

            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(defaultFrom);
                message.setTo(toEmails.toArray(new String[0]));
                message.setSubject('[' + environmentName + "] " + subject);
                // ensure all email messages have standard support footer (assumes only sending text messages)
                if (StringUtils.isNotBlank(supportContact) && !text.contains(supportContact)) {
                    text += "\n\n" + supportContact;
                }
                message.setText(text);
                emailSender.send(message);
                sent = true;
            } catch (MailException e) {
                log.error("Failed to send email", e);
            } finally {
                metric.recordDuration(System.currentTimeMillis() - start, Attributes.of(
                        stringKey("notificationType"), "email",
                        stringKey("status"), sent ? "success": "error"
                ));
            }
        }
        return CompletableFuture.completedFuture(sent);
    }
}
