// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.exception;

public enum ExceptionReason {
    // General
    UNEXPECTED_ERROR(null),
    TYPE_MISMATCH("Type mismatch"),
    REQUEST_IS_EMPTY("Request is empty"),
    ACCESS_DENIED("Access denied"),
    NOT_IN_AUTHORIZED_TEAMS_LIST("%s is not in authorized-teams list"),
    NOT_IN_ADMIN_AUTHORIZED_TEAMS_LIST("%s is not in app.admin.authorized-teams list"),

    // Application
    APPLICATION_NOT_FOUND("Application not found"),
    APPLICATION_DUPLICATE_FOUND("Duplicate application found"),
    APPLICATION_ID_REQUIRED("Application Id is required"),
    APPLICATION_NAME_REQUIRED("Name is required"),
    APPLICATION_TEAM_ID_REQUIRED("Team Id is required"),
    APPLICATION_NAME_INVALID_FORMAT("Name must be lower case alphanumeric starting with a letter"),
    APPLICATION_NAME_INVALID_LENGTH("Name max length is %s"),
    APPLICATION_NAME_INVALID_MIN_LENGTH("Name min length is %s"),
    APPLICATION_DESCRIPTION_INVALID_LENGTH("Description max length is %s"),
    APPLICATION_NAME_IMMUTABLE("Name cannot be modified"),
    APPLICATION_TEAM_TRANSFER_REQUIRED("Team ownership can only be changed via application transfer"),
    APPLICATION_RESOURCE_CONSTRAINT("Application cannot be deleted with existing resources: %s"),
    APPLICATION_CLOUD_TENANT_RESOURCE_CONSTRAINT("Application cloud/tenant cannot be changed with existing CAP resources: %s"),
    APPLICATION_DATA_PERIMETER_RESOURCE_CONSTRAINT("Application data perimeter cannot be changed with existing CAP resources: %s"),
    APPLICATION_CLOUD_INVALID("Invalid cloud value: %s"),
    APPLICATION_TENANT_INVALID("Invalid tenant value: %s"),
    APPLICATION_COMPLIANCE_TYPES_INVALID("Compliance Types are invalid.  Valid values include %s"),
    APPLICATION_INFORMATION_CLASSIFICATION_MISMATCH("Information classification does not match the compliance types"),
    INFORMATION_CLASSIFICATION_BACKFILL_DISABLED("Information classification backfill is disabled. Set app.job.information-classification-backfill.enabled=true to run it"),
    APPLICATION_PRIORITY_INVALID("Application priority invalid. Valid values include %s"),
    APPLICATION_EXTERNAL_ID_INVALID("External id is invalid"),
    APPLICATION_EXTERNAL_ID_NOT_FOUND("External id not found"),
    APPLICATION_EXTERNAL_ID_NOT_ALLOWED("External id cannot be set on PERSONAL teams"),
    APPLICATION_EXTERNAL_ID_SERVICE_ERROR("External id owner could not be verified"),
    APPLICATION_EXTERNAL_ID_CANNOT_BE_UNSET("External id cannot be unset"),
    APPLICATION_EXTERNAL_ID_IMMUTABLE("External id cannot be modified"),
    APPLICATION_EXTERNAL_ID_INVALID_FOR_PATCH("External id is invalid for patch update, review request"),
    APPLICATION_EXTERNAL_ID_REQUIRED("External id required"),

    // ApiForge
    APPLICATION_API_FORGE_OWNERSHIP_CONSTRAINT("Application cannot be deleted because it owns APIs in ApiForge: %s"),
    APPLICATION_API_FORGE_CONSUMPTION_CONSTRAINT("Application cannot be deleted because it consumes APIs in ApiForge: %s"),
    APPLICATION_EXTERNAL_ID_API_FORGE_OWNERSHIP_CONSTRAINT("Application external id cannot be changed because it owns APIs in ApiForge: %s"),
    APPLICATION_EXTERNAL_ID_API_FORGE_CONSUMPTION_CONSTRAINT("Application external id cannot be changed because it consumes APIs in ApiForge: %s"),
    // Team
    TEAM_NOT_FOUND("Team not found"),
    TEAM_ID_REQUIRED("Team Id required"),
    TEAM_DUPLICATE_FOUND("Duplicate team found"),
    TEAM_NAME_REQUIRED("Name is required"),
    TEAM_TYPE_REQUIRED("Type is required"),
    TEAM_NAME_INVALID_FORMAT("Name must be alphanumeric, underscore, or dash starting with a letter and cannot start with 'Personal'"),
    TEAM_NAME_PERSONAL_INVALID_FORMAT("Personal team Name must be of format 'Personal-' followed by a single alpha and a series of numbers"),
    TEAM_NAME_INVALID_LENGTH("Name max length is %s"),
    TEAM_NAME_INVALID_MIN_LENGTH("Name min length is %s"),
    TEAM_TYPE_IMMUTABLE("Type cannot be modified"),
    TEAM_NAME_IMMUTABLE("Name cannot be modified"),
    TEAM_EXTERNAL_NAME_IMMUTABLE("External name cannot be modified"),
    TEAM_DELETE_PERSONAL("Personal Team cannot be deleted"),
    TEAM_DELETE_WITH_APPLICATION("Team cannot be deleted until associated applications are transferred"),
    TEAM_DELETE_WITH_MEMBERS("Team cannot be deleted until all other team members are removed"),
    TEAM_DELETE_WITH_DOWNSTREAM_APPLICATION("The team cannot be deleted because it is using the application: %s"),
    TEAM_DELETE_DOWNSTREAM_APPLICATION_UNREACHABLE("The team cannot be deleted because the application %s is unreachable. Try again later."),
    DOWNSTREAM_APPLICATION_CONFIG_TOKEN_MISSING("The configuration for application %s is missing an auth token"),
    TEAMS_LIMIT_EXCEEDED("Number of teams is limited to %s"),
    TEAM_DISTRIBUTION_LIST_EMAIL_INVALID("Owning user entering a Team Distribution List Email must be a member of the provided List"),
    TEAM_SLACK_CHANNEL_ID_INVALID("Team slack channel id is invalid"),
    TEAM_SLACK_CHANNEL_NOT_FOUND("Team slack channel cannot be found or the '%s' application has not been invited to the channel"),
    TEAM_SLACK_CHANNEL_NOT_PRIVATE("Team slack channel must be a private channel"),
    TEAM_SLACK_CHANNEL_VALIDATION_ERROR("Team slack channel could not be validated. Please try again later. (The channel must be a private Slack channel and the app must be invited.)"),
    TEAM_SLACK_CHANNEL_NOT_IMPLEMENTED("Team slack channel could not be validated.  Feature not available at this time"),
    NOTIFICATION_MESSAGE_TEMPLATE_NOT_FOUND("Notification message template is missing or could not be found"),
    NOTIFICATION_MESSAGE_ERROR("Notification message could not be sent"),
    APPLICATION_DELETE_EXTERNAL_RESOURCE_UNREACHABLE("The application cannot be deleted because the external resource %s is unreachable. Try again later."),
    APPLICATION_CLOUD_TENANT_EXTERNAL_RESOURCE_UNREACHABLE("The application cloud/tenant cannot be changed because the external resource %s is unreachable. Try again later."),
    APPLICATION_DATA_PERIMETER_EXTERNAL_RESOURCE_UNREACHABLE("The application data perimeter cannot be changed because the external resource %s is unreachable. Try again later."),

    // Member
    MEMBER_NOT_FOUND("Member not found"),
    MEMBER_DUPLICATE_FOUND("Duplicate member found"),
    MEMBER_EXTERNAL_ID_REQUIRED("External Id is required"),

    // TeamMember
    TEAMMEMBER_TEAM_ID_REQUIRED("Team Id required"),
    TEAMMEMBER_MEMBER_ID_REQUIRED("Member Id required"),
    TEAMMEMBER_ROLE_REQUIRED("Role required"),
    TEAMMEMBER_ONE_OWNER_REQUIRED("Team requires at least one OWNER"),
    TEAMMEMBER_PERSONAL_IMMUTABLE("Cannot modify PERSONAL team membership"),
    TEAMMEMBER_NOT_FOUND("Team member not found"),
    TEAMMEMBER_MAX_MEMBERSHIPS_EXCEEDED("Team member can join up to %s teams only"),

    PARTIAL_NAME_MATCH_NOT_ALLOWED("'partialNameFilter' filter is only allowed with when paired with the 'name' filter"),
    MULTIPLE_NAME_FILTERS_SELECTED("Both 'name' and 'names' filter found.  Only one is allowed.  Use 'name' for a single name, 'names' for multiple"),

    EXTERNAL_USER_NOT_FOUND("External User not found"),
    EXTERNAL_USER_DISABLED("External User Account is disabled"),

    APPLICATION_TRANSFER_NOT_FOUND("Application transfer not found"),
    APPLICATION_TRANSFER_APPLICATION_ID_REQUIRED("Application Id is required"),
    APPLICATION_TRANSFER_SOURCE_TEAM_ID_REQUIRED("Source Team Id is required"),
    APPLICATION_TRANSFER_RECEIVE_TEAM_ID_REQUIRED("Receive Team Id is required"),
    APPLICATION_TRANSFER_SAME_TEAM_IDS("Source Team Id and Receive Team Id cannot be the same"),
    APPLICATION_TRANSFER_STATUS_REQUIRED("Status is required"),
    APPLICATION_TRANSFER_INVALID_RECEIVE_TEAM("Receive team cannot accept application transfer; %s"),
    APPLICATION_TRANSFER_APPLICATION_IMMUTABLE_ON_UPDATE("Application cannot be changed"),
    APPLICATION_TRANSFER_SOURCE_TEAM_IMMUTABLE_ON_UPDATE("Source team cannot be changed"),
    APPLICATION_TRANSFER_RECEIVE_TEAM_IMMUTABLE_ON_UPDATE("Receive team cannot be changed"),
    APPLICATION_TRANSFER_IMMUTABLE_ON_COMPLETED("Application transfer cannot be modified after completion"),
    APPLICATION_TRANSFER_ALREADY_EXISTS("Application transfer already exists"),

    // User
    USER_SEARCH_REQUIRED("User search is required"),
    USER_SEARCH_INVALID_LENGTH("User search min length is %s"),

    // AuthClient
    AUTH_CLIENT_NOT_FOUND("Client record not found"),
    AUTH_CLIENT_DUPLICATE_FOUND("Duplicate client found"),
    AUTH_CLIENT_REDIRECT_NOT_ALLOWED("Redirect urls are not valid for client type"),
    AUTH_CLIENT_REDIRECT_REQUIRED("Redirect url is required for client type"),
    AUTH_CLIENT_REDIRECT_URLS_IMMUTABLE("Redirect urls cannot be modified at this time. Redirect urls can only be modified if no previous version exists."),
    AUTH_CLIENT_CLIENT_ID_REQUIRED("Client Id is required"),
    AUTH_CLIENT_TYPE_REQUIRED("Type is required"),
    AUTH_CLIENT_TYPE_IMMUTABLE("Type cannot be modified"),
    AUTH_CLIENT_CLIENT_ID_INVALID_LENGTH("Client Id max length is %s"),
    AUTH_CLIENT_CLIENT_ID_INVALID_FORMAT("Client Id must be alphanumeric or dash starting with a letter"),
    AUTH_CLIENT_VERSION_REQUIRED("Client version is required"),
    AUTH_CLIENT_TEAM_MISMATCH("Auth client does not belong to the specified team"),
    AUTH_CLIENT_INVALID_REDIRECT_URI("Redirect uri: %s is malformed, or contains a fragment, credentials, or an unusable host"),
    AUTH_CLIENT_REDIRECT_URI_WILDCARD_NOT_ALLOWED("Redirect uri: %s contains a wildcard which is not permitted"),
    AUTH_CLIENT_REDIRECT_URI_DOMAIN_NOT_ALLOWED("Redirect uri: %s does not use an approved domain"),
    AUTH_CLIENT_REDIRECT_URI_SCHEME_NOT_ALLOWED("Redirect uri: %s uses a scheme that is not permitted"),
    AUTH_CLIENT_REDIRECT_URI_CUSTOM_SCHEME_REQUIRES_PKCE("Redirect uri: %s uses a custom URI scheme which requires client type USER_PKCE"),


    AUTH_API_ERROR("Error calling api service"),

    // Team AuthClient
    TEAMAUTHCLIENT_TEAM_ID_REQUIRED("Team Id required"),
    TEAMAUTHCLIENT_AUTHCLIENT_ID_REQUIRED("Member Id required"),

    APPLICATION_PRIORITY_DEFAULT_VALUE_NOT_FOUND("The application priority configuration must specify a default"),

    // Auth Client Expiration Check job
    AUTH_CLIENT_EXPIRATION_THRESHOLD_INVALID("expiration days threshold must be greater than 0"),
    AUTH_CLIENT_AGE_THRESHOLD_INVALID("age days threshold must be greater than 0"),
    AUTH_CLIENT_EXPIRED_RETENTION_DAYS_INVALID("expired client retention days must be greater than or equal to 0"),
    AUTH_CLIENT_LEGACY_DELETE_THRESHOLD_INVALID("legacy client delete age days threshold must be greater than 0"),
    AUTH_CLIENT_EXPIRATION_NOTIFICATION_ERROR("auth client expiration notification could not be sent"),

    MEMBER_NOTIFICATION_RECIPIENTS_REQUIRED("Member notification recipients are required"),
    MEMBER_SLACK_NOTIFICATION_NOT_SUPPORTED("sending slack messages to members is not supported"),

    NOT_AUTHORIZED_TO_UPDATE_SYNC_CHANGE_EVENT("%s is not authorized to update change event sync status for another clientId '%s'"),

    CHANGE_EVENT_CLIENT_ID_NOT_FOUND("Client Id cannot be an empty string"),
    CHANGE_EVENT_NOT_FOUND("Change Event Not Found"),

    // Line of business
    BUSINESS_LINE_NOT_FOUND("Line of business not found"),
    BUSINESS_LINE_NAME_REQUIRED("Name is required"),
    BUSINESS_LINE_NAME_INVALID("Name must be alphanumeric, underscore, dash, comma, or space starting with a letter"),
    BUSINESS_LINE_DESCRIPTION_INVALID("Description must be alphanumeric, underscore, dash, comma, colon, semi-colon, ampersand, parenthesis, dash, period, or space starting with a letter"),
    BUSINESS_LINE_MANAGER_EXTERNAL_ID_REQUIRED("At least one manager externalId is required"),
    BUSINESS_LINE_MANAGER_EXTERNAL_ID_NOT_FOUND("Manager externalId not found"),
    BUSINESS_LINE_MANAGER_EXTERNAL_ID_EXISTS("Line of business '%s' already owned by '%s'"),
    BUSINESS_LINE_MANAGER_DUPLICATE_IN_REQUEST("Manager externalId '%s' appears more than once in the request"),
    BUSINESS_LINE_DUPLICATE_FOUND("Line of business '%s' already exists");


    public final String label;

    ExceptionReason(String label) {
        this.label = label;
    }
}
