// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import com.cvshealth.cap.registry.model.TeamMemberRole;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.auth-client")
@Getter
public class AuthClientConfig {

    private final TeamMemberRole teamRole;
    private final boolean allowMultipleVersionsPerClient;

    public AuthClientConfig(
            @DefaultValue("MEMBER") TeamMemberRole teamRole,
            @DefaultValue("true") boolean allowMultipleVersionsPerClient
    ) {
        this.teamRole = teamRole;
        this.allowMultipleVersionsPerClient = allowMultipleVersionsPerClient;
    }
}
