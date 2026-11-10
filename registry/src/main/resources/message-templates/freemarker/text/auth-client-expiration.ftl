<#-- SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
SPDX-License-Identifier: Apache-2.0 -->You are a member of the Registry team named '${teamName}'. The team owns one or more auth clients with secrets that have expired or are expiring in the next ${expirationDaysThreshold} days. Auth client secrets must be rotated before the expiration date or they will stop working. Auth clients that were issued without an expiration date more than ${legacyClientDeleteAgeDaysThreshold} days ago are out of compliance, subject to deletion, and should be rotated immediately.

Auth client secrets can be easily rotated directly ${inRegistryOrCapUI} .<#if authClientRotationDocsUrl != "UNDEFINED"> Documentation link, ${authClientRotationDocsUrl} .</#if> Auth client rotation creates a new version for the client ID with a new secret. The previous version will remain active until its expiration date, allowing applications/services to migrate to the new secret without disruption. Auth clients with expired secrets will be retained for a minimum of ${expiredClientRetentionDays} days after expiration, before being scheduled for removal by Registry. Please note that secret expiry leads to that version of the auth client and all its associated attributes being deleted after the retention period has passed.

Client ID:
<#list clients as client>  '${client.clientId}' (v${client.clientVersion}) <#switch client.expirationType>
<#on "expiring">
auth client secret is ${client.ageInDays} days old; secret created on ${client.issuedAt}; secret will expire in ${client.expirationInDays} days on ${client.expiresAt}; last access token was issued on ${client.lastAccessTokenIssuedAt}
<#on "expired">
auth client secret is ${client.ageInDays} days old; secret created on ${client.issuedAt}; secret expired on ${client.expiresAt}; last access token was issued on ${client.lastAccessTokenIssuedAt}
<#on "deleted">
has been auto deleted; secret expired on ${client.expiresAt}
<#on "deleted-no-expiration">
has been auto deleted; secret created on ${client.issuedAt}
<#default>
auth client secret is ${client.ageInDays} days old; secret created on ${client.issuedAt}; last access token was issued on ${client.lastAccessTokenIssuedAt}
</#switch>
</#list>

${footer}