// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum CustomScopeType {
    APP_USER("App.User"),
    // "App.AppReg" is the legacy name, accepted until all clients request "App.Registry"
    REGISTRY("App.Registry", "App.AppReg"),
    OPEN_POLICY_AGENT("App.OPA");

    private final String scope;

    // scope plus any legacy names that resolve to this type
    private final Set<String> scopes;

    CustomScopeType(String scope, String... legacyScopes) {
        this.scope = scope;
        this.scopes = Stream.concat(Stream.of(scope), Arrays.stream(legacyScopes))
                .collect(Collectors.toUnmodifiableSet());
    }
}
