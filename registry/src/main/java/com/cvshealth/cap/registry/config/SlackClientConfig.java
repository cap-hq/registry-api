// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app.slack")
@Getter
@Setter
@ToString
public class SlackClientConfig {
    private boolean enabled;
    private String appName;
    private String token;
    private String methodsEndpointUrlPrefix;
    private int postMessageRetries;
    private int conversationListRetries;
    private int cacheTtlInMinutes;
}
