// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.AuthProviderCheckClient;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties("app.job.auth-provider-client-check")
@Getter
@Setter
@ToString
public class AuthProviderClientCheckJobConfig {
    private boolean enabled;
    private List<AuthProviderCheckClient> clients;
}
