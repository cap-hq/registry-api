// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.config.SlackClientConfig;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.cvshealth.cap.registry.service.notification.SlackService;
import com.slack.api.model.Conversation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackChannelNameMapper {

    private static final String NOT_FOUND = "CHANNEL NOT FOUND";
    private static final String NOT_AVAILABLE = "CHANNEL LOOKUP NOT AVAILABLE";

    private final SlackService slackService;
    private final SlackClientConfig slackClientConfig;

    public String mapChannelName(String channelId) {
        if (!slackClientConfig.isEnabled()) {
            return NOT_AVAILABLE;
        }
        try {
            Conversation channel = slackService.getChannelInfo(channelId);
            if (channel == null) {
                return NOT_FOUND;
            } else {
                return channel.getName();
            }
        } catch (SlackServiceException e) {
            return NOT_AVAILABLE;
        }
    }
}
