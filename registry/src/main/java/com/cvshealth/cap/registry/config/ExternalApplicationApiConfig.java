// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Wire contract of the external application API: the URI template that looks
 * an application up by its external ID ({@code {id}} is expanded), which
 * response field carries that ID, and which media type the API serves. These
 * vary per provider, so they are set per environment alongside
 * {@code external-application-api.base-uri}.
 */
@Component
@ConfigurationProperties("external-application-api")
@Getter
@Setter
@ToString
public class ExternalApplicationApiConfig {
    private String lookupUri = "/application?id={id}";
    private String idField = "id";
    private String accept = "application/json";
}
