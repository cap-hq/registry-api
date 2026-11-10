// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.cvshealth.cap.registry.model.notification.NotificationRequest;
import com.cvshealth.cap.registry.model.notification.NotificationType;
import com.cvshealth.cap.registry.service.DateFormatService;
import com.cvshealth.cap.registry.service.TeamService;
import com.slack.api.model.Conversation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTests {
    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private EmailService emailService;
    @Mock
    private SlackService slackService;
    @Mock
    private TeamService teamService;
    @Mock
    private DateFormatService dateFormatService;
    @Mock
    private NotificationMessageTemplateProcessor messageTemplateProcessor;

    private static final String SUBJECT = "Subject";
    private static final String MESSAGE = "Message";

    @Test
    public void sendTeamNotification() throws SlackServiceException {
        // given
        String teamName = "Team-1";
        String slackChannelId = "slackChannelId";
        String slackChannelName = "slackChannelName";
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .teamName(teamName)
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();
        TeamViewMember teamOwner = TeamViewMember.builder().role(TeamMemberRole.OWNER).email("owner@company.com").build();
        TeamViewMember teamMember = TeamViewMember.builder().role(TeamMemberRole.MEMBER).email("member@company.com").build();
        TeamView teamView = TeamView.builder()
                .name(teamName)
                .slackChannel(slackChannelId)
                .members(List.of(teamOwner, teamMember))
                .build();
        when(teamService.getTeamView(TeamSearchFilter.builder().name(teamName).build(), null)).thenReturn(teamView);
        when(emailService.sendMessage(SUBJECT, MESSAGE, List.of(teamOwner.getEmail()))).thenReturn(CompletableFuture.completedFuture(null));
        when(slackService.getChannelInfo(slackChannelId)).thenReturn(Conversation.builder().id(slackChannelId).name(slackChannelName).build());
        when(slackService.sendAsyncMessageToChannel(slackChannelId, slackChannelName, SUBJECT + System.lineSeparator() + System.lineSeparator() + MESSAGE)).thenReturn(CompletableFuture.completedFuture(null));

        // when
        assertDoesNotThrow(() -> notificationService.sendTeamNotification(notificationRequest));

        // then
        verifyNoMoreInteractions(teamService);
        verifyNoMoreInteractions(emailService);
        verifyNoMoreInteractions(slackService);
    }

    @Test
    public void sendTeamNotification_onlyEmail() {
        // given
        String teamName = "Team-1";
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .notificationTypes(List.of(NotificationType.EMAIL))
                .teamName(teamName)
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();
        TeamViewMember teamOwner = TeamViewMember.builder().role(TeamMemberRole.OWNER).email("owner@company.com").build();
        TeamViewMember teamMember = TeamViewMember.builder().role(TeamMemberRole.MEMBER).email("member@company.com").build();
        TeamView teamView = TeamView.builder()
                .name(teamName)
                .slackChannel("slackChannelId")
                .emailList("group@company.com")
                .members(List.of(teamOwner, teamMember))
                .build();
        when(teamService.getTeamView(TeamSearchFilter.builder().name(teamName).build(), null)).thenReturn(teamView);
        when(emailService.sendMessage(SUBJECT, MESSAGE, List.of("group@company.com"))).thenReturn(CompletableFuture.completedFuture(null));

        // when
        assertDoesNotThrow(() -> notificationService.sendTeamNotification(notificationRequest));

        // then
        verifyNoMoreInteractions(teamService);
        verifyNoMoreInteractions(emailService);
        verifyNoInteractions(slackService);
    }

    @Test
    public void sendTeamNotification_onlySlack() throws SlackServiceException {
        // given
        String teamName = "Team-1";
        String slackChannelId = "slackChannelId";
        String slackChannelName = "slackChannelName";
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .notificationTypes(List.of(NotificationType.SLACK))
                .teamName(teamName)
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();
        TeamViewMember teamOwner = TeamViewMember.builder().role(TeamMemberRole.OWNER).email("owner@company.com").build();
        TeamViewMember teamMember = TeamViewMember.builder().role(TeamMemberRole.MEMBER).email("member@company.com").build();
        TeamView teamView = TeamView.builder()
                .name(teamName)
                .slackChannel(slackChannelId)
                .members(List.of(teamOwner, teamMember))
                .build();
        when(teamService.getTeamView(TeamSearchFilter.builder().name(teamName).build(), null)).thenReturn(teamView);
        when(slackService.getChannelInfo(slackChannelId)).thenReturn(Conversation.builder().id(slackChannelId).name(slackChannelName).build());
        when(slackService.sendAsyncMessageToChannel(slackChannelId, slackChannelName, SUBJECT + System.lineSeparator() + System.lineSeparator() + MESSAGE)).thenReturn(CompletableFuture.completedFuture(null));

        // when
        assertDoesNotThrow(() -> notificationService.sendTeamNotification(notificationRequest));

        // then
        verifyNoMoreInteractions(teamService);
        verifyNoInteractions(emailService);
        verifyNoMoreInteractions(slackService);
    }

    @Test
    public void sendTeamNotification_badRequest_when_teamDoesNotExist() throws SlackServiceException {
        // given
        String teamName = "Team-1";
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .teamName(teamName)
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();
        when(teamService.getTeamView(TeamSearchFilter.builder().name(teamName).build(), null)).thenReturn(null);

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> notificationService.sendTeamNotification(notificationRequest));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_NOT_FOUND);

        // then
        verifyNoMoreInteractions(teamService);
        verifyNoInteractions(emailService);
        verifyNoInteractions(slackService);
    }

    @Test
    public void sendAsyncSlackNotification_badRequest_when_channelNotFound() throws SlackServiceException {
        // given
        String slackChannelId = "slackChannelId";
        when(slackService.getChannelInfo(slackChannelId)).thenReturn(null);

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> notificationService.sendAsyncSlackNotification(slackChannelId, SUBJECT, MESSAGE));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_FOUND);
    }

    @Test
    public void sendAsyncSlackNotification_noError_when_slackServiceThrowsException() throws SlackServiceException {
        // given
        String slackChannelId = "slackChannelId";
        when(slackService.getChannelInfo(slackChannelId)).thenThrow(SlackServiceException.class);

        // when
        notificationService.sendAsyncSlackNotification(slackChannelId, SUBJECT, MESSAGE);
    }

    @Test
    public void sendSlackNotification() throws SlackServiceException {
        // given
        String slackChannelId = "slackChannelId";
        String slackChannelName = "slackChannelName";
        when(slackService.getChannelInfo(slackChannelId)).thenReturn(Conversation.builder().id(slackChannelId).name(slackChannelName).build());

        // when
        notificationService.sendSlackNotification(slackChannelId, SUBJECT, MESSAGE);

        // verify
        verify(slackService).sendMessageToChannel(slackChannelId, slackChannelName, SUBJECT + System.lineSeparator() + System.lineSeparator() + MESSAGE);
        verifyNoMoreInteractions(slackService);
    }

    @Test
    public void sendSlackNotification_badRequest_when_channelNotFound() throws SlackServiceException {
        // given
        String slackChannelId = "slackChannelId";
        when(slackService.getChannelInfo(slackChannelId)).thenReturn(null);

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> notificationService.sendSlackNotification(slackChannelId, SUBJECT, MESSAGE));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_FOUND);
    }

    @Test
    public void sendSlackNotification_throwsError_when_slackServiceThrowsException() throws SlackServiceException {
        // given
        String slackChannelId = "slackChannelId";
        when(slackService.getChannelInfo(slackChannelId)).thenThrow(SlackServiceException.class);

        // when
        SlackServiceException ex = assertThrows(SlackServiceException.class, () -> notificationService.sendSlackNotification(slackChannelId, SUBJECT, MESSAGE));
        assertThat(ex).isNotNull();
    }

    @Test
    public void sendEmailNotification() {
        // given
        List<String> emailAddresses = List.of("owner@company.com");
        when(emailService.sendMessage(SUBJECT, MESSAGE, emailAddresses)).thenReturn(CompletableFuture.completedFuture(null));

        // when
        notificationService.sendEmailNotification(emailAddresses, SUBJECT, MESSAGE);

        // verify
        verifyNoMoreInteractions(emailService);
    }

    @Test
    public void sendEmailNotification_nothingSent_whenEmptyEmailList() {
        // given
        List<String> emailAddresses = Collections.emptyList();

        // when
        notificationService.sendEmailNotification(emailAddresses, SUBJECT, MESSAGE);

        // verify
        verifyNoInteractions(emailService);
    }

    @Test
    public void dateAtTimeFormat() {
        // given
        String dateTimeString = "2025-03-17 at 5:15:30 AM EST";
        Instant dateTime = Instant.parse("2025-03-17T10:15:30.00Z");
        when(dateFormatService.dateAtTimeFormat(dateTime)).thenReturn(dateTimeString);

        // when
        String result = notificationService.dateAtTimeFormat(dateTime);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(dateTimeString);
    }

    @Test
    public void registryOrCapUI() {
        // given
        notificationService.capUiUrl = "https://capui.example.com";
        notificationService.registryUiUrl = "https://registry.example.com";
        notificationService.registryUiName = "Registry UI";

        // when
        String result = notificationService.registryOrCapUI();

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo("in the Registry UI, https://registry.example.com , or in the CAP UI, https://capui.example.com");
    }

    @Test
    public void registryOrCapUI_registryUrlOnly() {
        // given
        notificationService.capUiUrl = "";
        notificationService.registryUiUrl = "https://registry.example.com";
        notificationService.registryUiName = "Registry UI";

        // when
        String result = notificationService.registryOrCapUI();

        // then
        assertThat(result).isEqualTo("in the Registry UI, https://registry.example.com");
    }

    @Test
    public void registryOrCapUI_noUrls() {
        // given
        notificationService.capUiUrl = "";
        notificationService.registryUiUrl = "";
        notificationService.registryUiName = "Registry UI";

        // when
        String result = notificationService.registryOrCapUI();

        // then
        assertThat(result).isEqualTo("in the Registry UI");
    }

    @Test
    public void registryOrCapUI_capUrlOnly() {
        // given
        notificationService.capUiUrl = "https://capui.example.com";
        notificationService.registryUiUrl = "";
        notificationService.registryUiName = "Registry UI";

        // when
        String result = notificationService.registryOrCapUI();

        // then
        assertThat(result).isEqualTo("in the Registry UI , or in the CAP UI, https://capui.example.com");
    }

    @Test
    public void registryOrCapUI_configuredUiName() {
        // given - dev/prod override registry-ui-name
        notificationService.capUiUrl = "";
        notificationService.registryUiUrl = "https://registry.example.com";
        notificationService.registryUiName = "App Registry UI";

        // when
        String result = notificationService.registryOrCapUI();

        // then
        assertThat(result).isEqualTo("in the App Registry UI, https://registry.example.com");
    }

    @Test
    public void sendMemberNotification() throws NotificationServiceException {
        // given
        NotificationRequest request = NotificationRequest.builder()
                .notificationTypes(Collections.singletonList(NotificationType.EMAIL))
                .recipients(List.of(
                        Member.builder().email("first.last@example.com").build()
                ))
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();
        when(emailService.sendMessage(anyString(), anyString(), any(List.class))).thenReturn(CompletableFuture.completedFuture(true));

        // when
        notificationService.sendMemberNotification(request);

        // then
        verifyNoInteractions(messageTemplateProcessor);
    }

    @Test
    public void sendMemberNotification_noRecipients() {
        // given
        NotificationRequest request = NotificationRequest.builder()
                .notificationTypes(Collections.singletonList(NotificationType.EMAIL))
                .build();

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> notificationService.sendMemberNotification(request));

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.MEMBER_NOTIFICATION_RECIPIENTS_REQUIRED);
        verifyNoInteractions(messageTemplateProcessor);
        verifyNoInteractions(emailService);
    }

    @Test
    public void sendMemberNotification_slackMessage() {
        // given
        NotificationRequest request = NotificationRequest.builder()
                .notificationTypes(Collections.singletonList(NotificationType.SLACK))
                .recipients(List.of(
                        Member.builder().email("first.last@example.com").build()
                ))
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> notificationService.sendMemberNotification(request));

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.MEMBER_SLACK_NOTIFICATION_NOT_SUPPORTED);
        verifyNoInteractions(messageTemplateProcessor);
    }

    @Test
    public void buildNotificationMessage() throws NotificationServiceException {
        // given
        NotificationRequest request = NotificationRequest.builder()
                .recipients(List.of(
                        Member.builder().email("first.last@example.com").build()
                ))
                .subject(SUBJECT)
                .message(MESSAGE)
                .build();

        // when
        String result = notificationService.buildNotificationMessage(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(MESSAGE);
    }

    @Test
    public void buildNotificationMessage_withTemplate() throws NotificationServiceException {
        // givenß
        NotificationRequest request = NotificationRequest.builder()
                .recipients(List.of(
                        Member.builder().email("first.last@example.com").build()
                ))
                .subject(SUBJECT)
                .template("some-template")
                .model(Map.of("key-1", "value-1"))
                .build();
        String templateProcessorResult = "result";
        when(messageTemplateProcessor.process(anyString(), any(Map.class))).thenReturn(templateProcessorResult);

        // when
        String result = notificationService.buildNotificationMessage(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(templateProcessorResult);
    }

    @Test
    public void buildNotificationMessage_missingTemplate() {
        // given
        NotificationRequest request = NotificationRequest.builder()
                .recipients(List.of(
                        Member.builder().email("first.last@example.com").build()
                ))
                .subject(SUBJECT)
                .template("")
                .build();

        // when
        BadRequestException ex = assertThrows(BadRequestException.class, () -> notificationService.buildNotificationMessage(request));

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.NOTIFICATION_MESSAGE_TEMPLATE_NOT_FOUND);
    }
}
