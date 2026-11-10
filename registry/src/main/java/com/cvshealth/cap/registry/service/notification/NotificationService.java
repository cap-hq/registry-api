// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.config.MetricRegistryConfig;
import com.cvshealth.cap.registry.metric.CountAndDurationMetric;
import com.cvshealth.cap.registry.metric.MetricAttributePair;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.IdentifiableUser;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.service.*;
import com.slack.api.model.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final EmailService emailService;
    private final SlackService slackService;
    private final TeamService teamService;
    private final DateFormatService dateFormatService;
    private final NotificationMessageTemplateProcessor messageTemplateProcessor;

    @Value("${cap-ui-url:}")
    String capUiUrl;

    @Value("${registry-ui-url}")
    String registryUiUrl;

    @Value("${registry-ui-name}")
    String registryUiName;

    @Value("${app.support.contact-info}")
    private String supportContact;

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_NOTIFICATION,
            attributes = {
                    @MetricAttributePair(key = "notificationType", value = "sendTeamNotification")
            })
    public void sendTeamNotification(NotificationRequest notificationRequest) throws NotificationServiceException {
        TeamView teamView = teamService.getTeamView(TeamSearchFilter.builder().name(notificationRequest.getTeamName()).build(), null);
        if (teamView == null) {
            throw new BadRequestException(ExceptionReason.TEAM_NOT_FOUND);
        }
        String message = buildNotificationMessage(notificationRequest);
        if (notificationRequest.sendEmail()) {
            List<String> emailAddresses = new ArrayList<>();
            if (StringUtils.isNotBlank(teamView.getEmailList())) {
                emailAddresses.add(teamView.getEmailList());
            } else {
                emailAddresses.addAll(teamView.getMembers().stream()
                        .filter(member -> member.getRole() == TeamMemberRole.OWNER)
                        .map(TeamViewMember::getEmail).toList());
            }
            if (!CollectionUtils.isEmpty(emailAddresses)) {
                sendEmailNotification(emailAddresses, notificationRequest.getSubject(), message);
            }
        }
        if (StringUtils.isNotBlank(teamView.getSlackChannel()) && notificationRequest.sendSlackMessage()) {
            sendAsyncSlackNotification(teamView.getSlackChannel(), notificationRequest.getSubject(), message);
        }
    }

    @CountAndDurationMetric(value = MetricRegistryConfig.METRIC_REGISTRY_NOTIFICATION,
            attributes = {
                    @MetricAttributePair(key = "notificationType", value = "sendMemberNotification")
            })
    public void sendMemberNotification(NotificationRequest notificationRequest) throws NotificationServiceException {
        if (CollectionUtils.isEmpty(notificationRequest.getRecipients())) {
            throw new BadRequestException(ExceptionReason.MEMBER_NOTIFICATION_RECIPIENTS_REQUIRED);
        }

        if (notificationRequest.sendSlackMessage()) {
            throw new BadRequestException( ExceptionReason.MEMBER_SLACK_NOTIFICATION_NOT_SUPPORTED);
        }

        String message = buildNotificationMessage(notificationRequest);
        if (notificationRequest.sendEmail()) {
            List<String> emailAddresses = new ArrayList<>(notificationRequest.getRecipients().stream()
                    .map(IdentifiableUser::getEmail).toList());
            sendEmailNotification(emailAddresses, notificationRequest.getSubject(), message);
        }

    }

    protected void sendEmailNotification(List<String> emailAddresses, String subject, String message) {
        if (!CollectionUtils.isEmpty(emailAddresses)) {
            log.info("sending email to {}", emailAddresses);
            emailService.sendMessage(subject, message, emailAddresses);
        }
    }

    protected void sendAsyncSlackNotification(String slackChannel, String subject, String message) {
        try {
            log.info("send async slack message to channel {}", slackChannel);
            Conversation channel = getSlackChannel(slackChannel);
            if (channel == null) {
                throw new BadRequestException(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_FOUND);
            }
            slackService.sendAsyncMessageToChannel(channel.getId(), channel.getName(), buildSlackMessage(subject, message));
        } catch (SlackServiceException e) {
            log.error("Failed to send async slack message to channel {}", slackChannel, e);
        }
    }

    protected void sendSlackNotification(String slackChannel, String subject, String message) throws SlackServiceException {
        log.info("send slack message to channel {}", slackChannel);
        Conversation channel = getSlackChannel(slackChannel);
        if (channel == null) {
            throw new BadRequestException(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_FOUND);
        }
        slackService.sendMessageToChannel(channel.getId(), channel.getName(), buildSlackMessage(subject, message));
    }

    /**
     * Returns the Slack conversion info for the given Slack channel
     * @param channelId The Slack channel ID
     * @return An instance of the Slack Conversion
     */
    private Conversation getSlackChannel(String channelId) throws SlackServiceException {
        return slackService.getChannelInfo(channelId);
    }

    private String buildSlackMessage(String subject, String message) {
        return subject + System.lineSeparator() + System.lineSeparator() + message;
    }

    public String dateAtTimeFormat(Instant dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateFormatService.dateAtTimeFormat(dateTime);
    }

    public String registryOrCapUI() {
        String uiReference = StringUtils.isNotBlank(registryUiUrl) ? "in the " + registryUiName + ", " + registryUiUrl : "in the " + registryUiName;
        if (StringUtils.isNotBlank(capUiUrl)) {
            uiReference += " , or in the CAP UI, " + capUiUrl;
        }
        return uiReference;
    }

    String buildNotificationMessage(NotificationRequest notificationRequest) throws NotificationServiceException {
        if (StringUtils.isNotEmpty(notificationRequest.getMessage())) {
            return notificationRequest.getMessage();
        } else if (StringUtils.isEmpty(notificationRequest.getTemplate())) {
            throw new BadRequestException(ExceptionReason.NOTIFICATION_MESSAGE_TEMPLATE_NOT_FOUND);
        }

        Map<String, Object> model = notificationRequest.getModel();
        if (model != null) {
            model = new HashMap<>(notificationRequest.getModel());
            model.put("footer", supportContact);
        }
        return messageTemplateProcessor.process(notificationRequest.getTemplate(), model);
    }
}
