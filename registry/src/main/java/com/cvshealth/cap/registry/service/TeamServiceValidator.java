// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.config.SlackClientConfig;
import com.cvshealth.cap.registry.model.FieldConstraints;
import com.cvshealth.cap.registry.model.Group;
import com.cvshealth.cap.registry.model.Team;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.ValidationConstraints;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import com.cvshealth.cap.registry.service.notification.SlackService;
import com.slack.api.model.Conversation;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TeamServiceValidator {

    private static final Pattern PERSONAL_NAME_PATTERN = Pattern.compile("^Personal-[a-zA-Z0-9-]+$");
    private final ValidationConstraints validationConstraints;
    private final AzureAdUserService azureAdUserService;
    private final SlackService slackService;
    private final SlackClientConfig slackClientConfig;

    /** Compiled once — the configured regex is read on every create and update. */
    private Pattern namePattern;

    @PostConstruct
    void init() {
        namePattern = Pattern.compile(nameConstraints().getRegex());
    }

    private FieldConstraints nameConstraints() {
        return validationConstraints.getTeam().getName();
    }

    public Team validate(Team request) throws ApiException {
        if (null == request) {
            throw new BadRequestException(ExceptionReason.REQUEST_IS_EMPTY);
        }
        if (StringUtils.isBlank(request.getName())) {
            throw new BadRequestException(ExceptionReason.TEAM_NAME_REQUIRED);
        }
        if (request.getType() == null) {
            throw new BadRequestException(ExceptionReason.TEAM_TYPE_REQUIRED);
        }
        String name = request.getName();
        int nameMaxLength = nameConstraints().getMaxLength();
        if (name.length() > nameMaxLength) {
            throw new BadRequestException(ExceptionReason.TEAM_NAME_INVALID_LENGTH,
                    String.format(ExceptionReason.TEAM_NAME_INVALID_LENGTH.label, nameMaxLength));
        }
        if (!TeamType.PERSONAL.equals(request.getType()) && !namePattern.matcher(name).matches()
                && !name.startsWith(nameConstraints().getFormatExemptPrefix())) {
            throw new BadRequestException(ExceptionReason.TEAM_NAME_INVALID_FORMAT);
        }
        if (TeamType.PERSONAL.equals(request.getType()) && !PERSONAL_NAME_PATTERN.matcher(name).matches()) {
            throw new BadRequestException(ExceptionReason.TEAM_NAME_PERSONAL_INVALID_FORMAT);
        }
        return request;
    }

    /**
     * Create-only entry point: the generic checks plus the new team specific ones (min-length, blocks
     * personal prefix). {@link #validateNew} dereferences the name, so it must run after {@link #validate}.
     */
    public Team validateCreate(Team request) throws ApiException {
        validate(request);
        return validateNew(request);
    }

    /**
     * The minimum name length applies to new teams only. Names are immutable once created
     * ({@link ExceptionReason#TEAM_NAME_IMMUTABLE}), so enforcing it on update would only reject
     * existing teams whose name predates the rule.
     */
    public Team validateNew(Team request) throws ApiException {
        int nameMinLength = nameConstraints().getMinLength();
        if (request.getName().length() < nameMinLength) {
            throw new BadRequestException(ExceptionReason.TEAM_NAME_INVALID_MIN_LENGTH,
                    String.format(ExceptionReason.TEAM_NAME_INVALID_MIN_LENGTH.label, nameMinLength));
        }
        if (!TeamType.PERSONAL.equals(request.getType())
                && request.getName().toLowerCase()
                          .startsWith(nameConstraints().getFormatExemptPrefix().toLowerCase())) {
            throw new BadRequestException(ExceptionReason.TEAM_NAME_INVALID_FORMAT);
        }
        return request;
    }

    public Team validateUpdate(Team request, TeamEntity entity) throws ApiException {
        if (!entity.getType().equals(request.getType())) {
            throw new BadRequestException(ExceptionReason.TEAM_TYPE_IMMUTABLE);
        }
        if (!entity.getName().equals(request.getName())) {
            throw new BadRequestException(ExceptionReason.TEAM_NAME_IMMUTABLE);
        }
        if (entity.getExternalName() != null && !entity.getExternalName().equals(request.getExternalName())) {
            throw new BadRequestException(ExceptionReason.TEAM_EXTERNAL_NAME_IMMUTABLE);
        }
        return request;
    }

    @PostAuthorize("returnObject == false OR hasPermission(#request.id, 'Team', 'modify-slack-channel')")
    public boolean validateIfSlackChannelSet(Team request, TeamEntity entity) {
        return (entity==null && !StringUtils.isBlank(request.getSlackChannel())) || (entity!=null && !StringUtils.equalsIgnoreCase(request.getSlackChannel(), entity.getSlackChannel()));
    }

    public void validateSlackChannel(Team request) {
        if (StringUtils.isEmpty(request.getSlackChannel())) {
            return;
        } else if (StringUtils.isBlank(request.getSlackChannel())) {
            throw new BadRequestException(ExceptionReason.TEAM_SLACK_CHANNEL_ID_INVALID);
        } else if (!slackClientConfig.isEnabled()) {
            throw new BadRequestException(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_IMPLEMENTED);
        }

        try {
            Conversation channel = slackService.getChannelInfo(request.getSlackChannel());
            if (channel == null) {
                throw new BadRequestException(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_FOUND,
                        String.format(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_FOUND.label, slackClientConfig.getAppName()));
            } else if (!channel.isPrivate()) {
                throw new BadRequestException(ExceptionReason.TEAM_SLACK_CHANNEL_NOT_PRIVATE);
            }
        } catch (SlackServiceException e) {
            throw new BadRequestException(ExceptionReason.TEAM_SLACK_CHANNEL_VALIDATION_ERROR);
        }
    }

    @PostAuthorize("returnObject == false OR hasPermission(#request.id, 'Team', 'modify_email_list')")
    public boolean validateIfEmailListSet(Team request, TeamEntity entity) {
        return (entity==null && !StringUtils.isBlank(request.getEmailList())) || (entity!=null && !StringUtils.equalsIgnoreCase(request.getEmailList(), entity.getEmailList()));
    }

    public void validateGroupEmail(Team request) throws ApiException {
        //check if request has an email list
        if (StringUtils.isBlank(request.getEmailList())){
            return;
        }
        String userEmail = SecurityHelper.getCurrentUserEmail();
        List<Group> groups = azureAdUserService.findUserEmailGroups(userEmail);

        if (!CollectionUtils.isEmpty(groups)) {
            for (Group group : groups) {
                if (group.getMail()!=null && group.getMail().equalsIgnoreCase(request.getEmailList())) {
                    return;
                }
            }
        }

        throw new BadRequestException(ExceptionReason.TEAM_DISTRIBUTION_LIST_EMAIL_INVALID);
    }

}