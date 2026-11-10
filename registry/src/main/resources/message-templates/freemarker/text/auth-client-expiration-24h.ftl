<#-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
SPDX-License-Identifier: Apache-2.0 -->You are a member of the Registry team named '${teamName}'. The team owns one or more auth clients with secrets that expire within the next ${expirationHoursThreshold} hours. Auth client secrets must be rotated before the expiration date or they will stop working.

Auth client secrets can be easily rotated directly ${inRegistryOrCapUI} .<#if authClientRotationDocsUrl != "UNDEFINED"> Documentation link, ${authClientRotationDocsUrl} .</#if> Auth client rotation creates a new version for the client ID with a new secret. The previous version will remain active until its expiration date, allowing applications/services to migrate to the new secret without disruption.

The following auth clients have been used to issue access tokens within the last ${lastAccessTokenIssuedWithinHours!expirationHoursThreshold} hours and have secrets that will expire within the next ${expirationHoursThreshold} hours.  If you have recently rotated the secret for any of these auth clients, review the last access token issued date to ensure the old secret is still not being used by any application.

Client ID:
<#list clients as client>  '${client.clientId}' (v${client.clientVersion}) auth client secret expires in ${client.expirationInDays} day(s) on ${client.expiresAt}; secret created on ${client.issuedAt}; last access token was issued on ${client.lastAccessTokenIssuedAt}
</#list>

${footer}