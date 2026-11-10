// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FreemarkerMessageTemplateProcessor implements NotificationMessageTemplateProcessor {

    private final Configuration configuration;

    @Override
    public String process(String messageTemplate, Map<String, Object> model) throws NotificationServiceException {
        try {
            Template template = configuration.getTemplate(messageTemplate);

            StringWriter stringWriter = new StringWriter();
            template.process(model, stringWriter);
            return stringWriter.toString();
        } catch (Exception e) {
            log.error("Could not process Freemarker template: {}", messageTemplate, e);
            throw new NotificationServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "notification",
                    ExceptionReason.NOTIFICATION_MESSAGE_ERROR.label);
        }
    }
}
