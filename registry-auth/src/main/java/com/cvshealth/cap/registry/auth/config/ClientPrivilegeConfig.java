// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.config;

import com.cvshealth.cap.registry.auth.model.ClientPrivilegeType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Component
@Validated
@ConfigurationProperties(prefix = "app.jwt.client-privilege")
@Getter
@Setter
public class ClientPrivilegeConfig {
    private ClientPrivilegeType defaultPrivilege;
    private Map<String, ClientPrivilegeType> authorizedTeams;
    private Map<String, ClientPrivilegeType> authorizedClients;
}
