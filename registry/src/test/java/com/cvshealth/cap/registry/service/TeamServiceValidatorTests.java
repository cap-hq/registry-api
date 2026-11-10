// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.SlackClientConfig;
import com.cvshealth.cap.registry.model.Group;
import com.cvshealth.cap.registry.model.Member;
import com.cvshealth.cap.registry.model.Team;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.ValidationConstraints;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.service.notification.SlackService;
import com.cvshealth.cap.registry.util.ValidationConstraintsFixture;
import com.slack.api.model.Conversation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TeamServiceValidatorTests {
    private static final ValidationConstraints VALIDATION_CONSTRAINTS = ValidationConstraintsFixture.validationConstraints();
    private static final int NAME_MAX_LENGTH = VALIDATION_CONSTRAINTS.getTeam().getName().getMaxLength();
    private static final int NAME_MIN_LENGTH = VALIDATION_CONSTRAINTS.getTeam().getName().getMinLength();

    private TeamServiceValidator validator;
    @Mock
    private SlackService slackService;
    @Mock
    private SlackClientConfig slackClientConfig;
    @Mock
    private AzureAdUserService azureAdUserService;

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        validator = new TeamServiceValidator(VALIDATION_CONSTRAINTS, azureAdUserService, slackService, slackClientConfig);
        validator.init();
    }

    @Test
    public void givenValidObject_whenValidate_thenReturnRequest() throws ApiException {
        // given
        Team request = Team.builder()
                .name("Personal-C11111")
                .type(TeamType.PERSONAL)
                .build();

        // when
        request = validator.validate(request);

        // then
        assertThat(request).isNotNull();
    }

    @Test
    public void givenNull_whenValidate_thenThrowException() {
        // given
        Team request = null;

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).contains(ExceptionReason.REQUEST_IS_EMPTY.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.REQUEST_IS_EMPTY);
    }

    @Test
    public void givenMissingName_whenValidate_thenThrowException() {
        // given
        Team request = Team.builder()
                .name(null)
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAM_NAME_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_NAME_REQUIRED);
    }

    @Test
    public void givenMissingType_whenValidate_thenThrowException() {
        // given
        Team request = Team.builder()
                .name("team1")
                .type(null)
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAM_TYPE_REQUIRED.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_TYPE_REQUIRED);
    }

    @Test
    public void givenUppercaseName_whenValidate_thenException() {
        // given
        Team request = Team.builder()
                .name("-MustStartWithLetter")
                .type(TeamType.SHARED)
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAM_NAME_INVALID_FORMAT.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_NAME_INVALID_FORMAT);
    }

    /**
     * Pins the semantics of the published {@code formatExemptPrefix}: a name carrying the prefix is
     * accepted even though it violates the character regex. Named "reserved" this read as the exact
     * opposite, and a client applying it as a restriction would reject what the server accepts.
     */
    @Test
    public void givenNonPersonalNameWithFormatExemptPrefix_whenValidate_thenAcceptedDespiteInvalidCharacters() {
        // given a name the regex rejects, carrying the exempt prefix
        String exemptPrefix = VALIDATION_CONSTRAINTS.getTeam().getName().getFormatExemptPrefix();
        String name = exemptPrefix + " Space And @";
        assertThat(name.matches(VALIDATION_CONSTRAINTS.getTeam().getName().getRegex()))
                .as("fixture must supply a name the character regex rejects")
                .isFalse();
        Team request = Team.builder()
                .name(name)
                .type(TeamType.SHARED)
                .build();

        // when/then the prefix exempts it from the format rule
        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    public void givenValidObject_whenValidateCreate_thenReturnRequest() throws ApiException {
        // given
        Team request = Team.builder()
                .name("Personal-C11111")
                .type(TeamType.PERSONAL)
                .build();

        // when/then
        assertThat(validator.validateCreate(request)).isNotNull();
    }

    /**
     * The generic checks must run first: validateNew dereferences the name, so on its own it would
     * throw NullPointerException here instead of a 400.
     */
    @Test
    public void givenNull_whenValidateCreate_thenThrowException() {
        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateCreate(null));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.REQUEST_IS_EMPTY);
    }

    /**
     * The exempt prefix is tolerated by validate() for records already in the table, but a new
     * non-personal team may not claim it — in any casing.
     */
    @Test
    public void givenNonPersonalNameWithFormatExemptPrefix_whenValidateCreate_thenThrowException() {
        // given a name validate() accepts
        String exemptPrefix = VALIDATION_CONSTRAINTS.getTeam().getName().getFormatExemptPrefix();
        Team request = Team.builder()
                .name(exemptPrefix.toLowerCase() + "-claimed-by-a-new-team")
                .type(TeamType.SHARED)
                .build();
        assertDoesNotThrow(() -> validator.validate(request));

        // when/then create rejects it
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateCreate(request));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_NAME_INVALID_FORMAT);
    }

    /**
     * The minimum length is a create-time rule enforced directly by validateNew, independent of the
     * character regex (which itself implies a length of at least 2 here) — so it is exercised against
     * validateNew directly rather than through the full validateCreate pipeline.
     */
    @Test
    public void givenTooShortName_whenValidateNew_thenThrowException() {
        // given
        Team request = Team.builder()
                .name("x".repeat(NAME_MIN_LENGTH - 1))
                .type(TeamType.SHARED)
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateNew(request));
        assertThat(ex.getMessage()).contains(String.format(ExceptionReason.TEAM_NAME_INVALID_MIN_LENGTH.label, NAME_MIN_LENGTH));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_NAME_INVALID_MIN_LENGTH);
    }

    @Test
    public void givenNameAtMinLength_whenValidateNew_thenReturnRequest() throws ApiException {
        // given
        Team request = Team.builder()
                .name("x".repeat(NAME_MIN_LENGTH))
                .type(TeamType.SHARED)
                .build();

        // when/then
        assertThat(validator.validateNew(request)).isNotNull();
    }

    @Test
    public void givenInvalidPersonalName_whenValidate_thenException() {
        // given
        Team request = Team.builder()
                .name("Must-Start-With-Personal")
                .type(TeamType.PERSONAL)
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAM_NAME_PERSONAL_INVALID_FORMAT.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_NAME_PERSONAL_INVALID_FORMAT);
    }

    @Test
    public void givenTooLongName_whenValidate_thenException() {
        // given
        Team request = Team.builder()
                .name("x".repeat(NAME_MAX_LENGTH + 1))
                .type(TeamType.SHARED)
                .build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(request));
        assertThat(ex.getMessage()).contains(String.format(ExceptionReason.TEAM_NAME_INVALID_LENGTH.label, NAME_MAX_LENGTH));
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_NAME_INVALID_LENGTH);
    }

    @Test
    public void givenNameAtMaxLength_whenValidate_thenReturnRequest() throws ApiException {
        // given
        Team request = Team.builder()
                .name("x".repeat(NAME_MAX_LENGTH))
                .type(TeamType.SHARED)
                .build();

        // when
        request = validator.validate(request);

        // then
        assertThat(request).isNotNull();
    }

    @Test
    public void givenDiffType_whenUpdate_thenException() {
        // given
        TeamEntity entity = TeamEntity.builder().name("test").type(TeamType.PERSONAL).build();
        Team request = Team.builder().name("test").type(TeamType.SHARED).build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateUpdate(request, entity));
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAM_TYPE_IMMUTABLE.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_TYPE_IMMUTABLE);
    }

    @Test
    public void givenDiffName_whenUpdate_thenException() {
        // given
        TeamEntity entity = TeamEntity.builder().name("test").type(TeamType.PERSONAL).build();
        Team request = Team.builder().name("test-diff").type(TeamType.PERSONAL).build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateUpdate(request, entity));
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAM_NAME_IMMUTABLE.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_NAME_IMMUTABLE);
    }

    @Test
    public void givenNullOrEmptySlackChannel_whenValidateSlackChannel_thenSuccess() {
        // when null
        assertDoesNotThrow(() -> validator.validateSlackChannel(new Team()));
        // when empty
        assertDoesNotThrow(() -> validator.validateSlackChannel(Team.builder().slackChannel("").build()));
    }

    @Test
    public void givenBlankSlackChannel_whenValidateSlackChannel_thenThrowNotImplementedException() {
        // when slack channel is blank
        Team request = Team.builder().slackChannel(" ").build();
        // when
        BadRequestException exception = assertThrows(BadRequestException.class, () -> validator.validateSlackChannel(request));
        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getReason()).isEqualTo(ExceptionReason.TEAM_SLACK_CHANNEL_ID_INVALID);
    }

    @Test
    public void givenSlackNotEnabled_whenValidateSlackChannel_thenThrowNotImplementedException() {
        // when slack integration not enabled
        Team request = Team.builder().slackChannel("C123").build();
        // when
        BadRequestException exception = assertThrows(BadRequestException.class, () -> validator.validateSlackChannel(request));
        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getReason()).isEqualTo(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_IMPLEMENTED);
    }

    @Test
    public void givenSlackChannel_whenValidateSlackChannel_thenThrowValidationException() throws SlackServiceException {
        // given
        String channelId = "C123";
        Team request = Team.builder().slackChannel(channelId).build();
        when(slackClientConfig.isEnabled()).thenReturn(true);
        when(slackService.getChannelInfo(channelId)).thenThrow(SlackServiceException.class);
        // when
        BadRequestException exception = assertThrows(BadRequestException.class, () -> validator.validateSlackChannel(request));
        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getReason()).isEqualTo(ExceptionReason.TEAM_SLACK_CHANNEL_VALIDATION_ERROR);
    }

    @Test
    public void givenPublicSlackChannel_whenValidateSlackChannel_thenThrowException() throws SlackServiceException {
        // given
        String channelId = "C123";
        Team request = Team.builder().slackChannel(channelId).build();
        when(slackClientConfig.isEnabled()).thenReturn(true);
        when(slackService.getChannelInfo(channelId)).thenReturn(Conversation.builder().isPrivate(false).build());
        // when
        BadRequestException exception = assertThrows(BadRequestException.class, () -> validator.validateSlackChannel(request));
        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getReason()).isEqualTo(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_PRIVATE);
    }

    @Test
    public void givenSlackChannelNotFound_whenValidateSlackChannel_thenThrowException() throws SlackServiceException {
        // given
        String channelId = "C123";
        Team request = Team.builder().slackChannel(channelId).build();
        when(slackClientConfig.isEnabled()).thenReturn(true);
        when(slackService.getChannelInfo(channelId)).thenReturn(null);
        // when
        BadRequestException exception = assertThrows(BadRequestException.class, () -> validator.validateSlackChannel(request));
        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getReason()).isEqualTo(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_FOUND);
    }

    @Test
    public void givenDiffExternalName_whenUpdate_thenException() {
        // given
        TeamEntity entity = TeamEntity.builder().name("test").type(TeamType.PERSONAL).externalName("x-test").build();
        Team request = Team.builder().name("test").type(TeamType.PERSONAL).externalName("x-test-diff").build();

        // when/then
        ApiException ex = assertThrows(ApiException.class, () -> validator.validateUpdate(request, entity));
        assertThat(ex.getMessage()).contains(ExceptionReason.TEAM_EXTERNAL_NAME_IMMUTABLE.label);
        assertThat(ex.getReason()).isEqualTo(ExceptionReason.TEAM_EXTERNAL_NAME_IMMUTABLE);
    }

    @Test
    public void givenValidSlackChannel_whenValidateSlackChannel_thenNoException() throws SlackServiceException {
        // given
        String channelId = "C123";
        String channelName = "channel-123";
        Team request = Team.builder().slackChannel(channelId).build();
        when(slackClientConfig.isEnabled()).thenReturn(true);
        when(slackService.getChannelInfo(channelId)).thenReturn(Conversation.builder().id(channelId).name(channelName).isPrivate(true).build());
        // when
        validator.validateSlackChannel(request);
    }

    @Test
    public void validateGroupEmail() {
        // given
        String userEmail = "Test.User@example.com";
        String teamDistroList = "Test1@example.com";
        setMockSecurityContext("external-id", userEmail);
        Team teamRequest = Team.builder().name("Team-1").emailList(teamDistroList).build();
        List<Group> groups = List.of(
                Group.builder().mail(teamDistroList).mailEnabled("true").securityEnabled("false").build()
        );
        when(azureAdUserService.findUserEmailGroups(userEmail)).thenReturn(groups);

        // when
        validator.validateGroupEmail(teamRequest);
    }

    @Test
    public void validateGroupEmail_ignoreWhenNotInRequest() {
        // given
        Team teamRequest = Team.builder().name("Team-1").emailList(null).build();

        // when
        validator.validateGroupEmail(teamRequest);

        verifyNoInteractions(azureAdUserService);
    }

    @Test
    public void validateGroupEmail_notFound_returnsException() {
        // given
        String userEmail = "Test.User@example.com";
        String teamDistroList = "Test1@example.com";
        setMockSecurityContext("external-id", userEmail);
        Team teamRequest = Team.builder().name("Team-1").emailList(teamDistroList).build();
        List<Group> groups = Collections.emptyList();
        when(azureAdUserService.findUserEmailGroups(userEmail)).thenReturn(groups);

        // when
        BadRequestException exception = assertThrows(BadRequestException.class, () -> validator.validateGroupEmail(teamRequest));
        assertThat(exception).isNotNull();
        assertThat(exception.getReason()).isEqualTo(ExceptionReason.TEAM_DISTRIBUTION_LIST_EMAIL_INVALID);
    }

    private void setMockSecurityContext(String externalId, String email) {
        SecurityContextHolder.setContext(securityContext);
        Member member = Member.builder().externalId(externalId).email(email).build();
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getDetails()).thenReturn(member);
    }
}
