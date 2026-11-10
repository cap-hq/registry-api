-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
-- SPDX-License-Identifier: Apache-2.0

-- Seed the public PKCE client used to obtain a user JWT (no UI is shipped).
-- Values match a USER_PKCE client created through ClientService with default
-- token lifetimes (access 30m, refresh 720m). Settings JSON uses the format
-- written by JdbcRegisteredClientRepository.
INSERT INTO oauth2_registered_client (id, client_id, client_name, client_authentication_methods,
                                      authorization_grant_types, redirect_uris, scopes,
                                      client_settings, token_settings)
VALUES ('b90ca039-a527-4a50-839d-861670611e99',
        'registry-user-pkce',
        'registry-user-pkce',
        'none',
        'refresh_token,authorization_code',
        'http://localhost:3000',
        'openid,profile,email,App.User,App.AppReg,App.OPA',
        '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
        '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration","PT30M"],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration","PT12H"],"settings.token.authorization-code-time-to-live":["java.time.Duration","PT5M"],"settings.token.device-code-time-to-live":["java.time.Duration","PT5M"]}')
ON CONFLICT (client_id) DO NOTHING;
