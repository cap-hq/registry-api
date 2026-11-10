// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("external-application-api.auth-provider")
@Getter
@Setter
@ToString(exclude = "clientSecret")
@Slf4j
public class ExternalApplicationClientAuthConfig {
    private String grantType;
    private String scope;
    private String clientId;
    private String clientSecret;
    private int timeoutInSeconds;
    private int refreshBufferInSeconds;

    @PostConstruct
    void validate() {
        if (StringUtils.isBlank(clientId)) log.warn("external-application-api.auth-provider.client-id is not configured");
        if (StringUtils.isBlank(clientSecret)) log.warn("external-application-api.auth-provider.client-secret is not configured");
        if (StringUtils.isBlank(grantType)) log.warn("external-application-api.auth-provider.grant-type is not configured");
        if (StringUtils.isBlank(scope)) log.warn("external-application-api.auth-provider.scope is not configured");
        if (timeoutInSeconds <= 0) log.warn("external-application-api.auth-provider.timeout-in-seconds is not configured or invalid");
    }
}
