// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomClaim {
    public static final String TYPE_APPLICATION = "application";
    public static final String TYPE_TEAM = "team";
    public static final String TYPE_AUTH_CLIENT = "authClient";
    public static final String TYPE_CUSTOM = "custom";
    public static final String ATTR_CLIENT_PRIVILEGE = "clientPrivilege";
    public static final String SOURCE_REGISTRY = "REGISTRY";
    public static final String SOURCE_AUTH_PARAMETER = "AUTH_PARAMETER";

    public String name;
    @Builder.Default
    public String source = SOURCE_REGISTRY;
    public String type;
    public String attribute;
    public CustomScopeType scopeType;
    public Application application;
    public AuthClient authClient;

    public boolean isMemberClaim() {
        return !TYPE_AUTH_CLIENT.equals(type);
    }

    public boolean isSourceRegistry() {
        return SOURCE_REGISTRY.equals(source);
    }

    public boolean isSourceAuthParameter() {
        return SOURCE_AUTH_PARAMETER.equals(source);
    }

    /** True when this claim picks applications by information classification, which the registry must then be asked for. */
    public boolean filtersOnInformationClassification() {
        return application != null && StringUtils.isNotBlank(application.getInformationClassification());
    }
}
