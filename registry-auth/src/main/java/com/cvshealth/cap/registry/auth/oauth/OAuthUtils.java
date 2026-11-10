// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.oauth;

public class OAuthUtils {

    private static final String AZURE_CREDENTIAL_ENV_VAR = "SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_SECRET";

    public static String getAuthProviderCredentialPrefix() {
        return System.getenv().containsKey(AZURE_CREDENTIAL_ENV_VAR) ? System.getenv(AZURE_CREDENTIAL_ENV_VAR).substring(0, 3) : null;
    }

}
