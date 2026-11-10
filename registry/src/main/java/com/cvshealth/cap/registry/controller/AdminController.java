// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.controller;

import com.cvshealth.cap.registry.config.AuthClientCleanupJobConfig;
import com.cvshealth.cap.registry.config.AuthClientExpirationCheckJobConfig;
import com.cvshealth.cap.registry.config.AuthClientExpirationCheckNightlyJobConfig;
import com.cvshealth.cap.registry.config.AuthProviderClientCheckJobConfig;
import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.SlackServiceException;
import com.cvshealth.cap.registry.model.externalApplication.ExternalApplication;
import com.cvshealth.cap.registry.model.job.AuthClientCleanupJobResponse;
import com.cvshealth.cap.registry.model.job.AuthClientExpirationCheckJobStats;
import com.cvshealth.cap.registry.model.job.CheckAuthClientExpirationsRequest;
import com.cvshealth.cap.registry.model.job.InformationClassificationBackfillResponse;
import com.cvshealth.cap.registry.service.*;
import com.cvshealth.cap.registry.service.notification.SlackService;
import com.slack.api.model.Conversation;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Slf4j
@RestController
@RequestMapping("${service.context-path}/v1")
public class AdminController {
    private final AuthClientCleanupJobConfig authClientCleanupJobConfig;
    private final AuthClientCleanupService authClientCleanupService;
    private final AzureApplicationService azureApplicationService;
    private final AuthClientExpirationNotificationService authClientExpirationNotificationService;
    private final AuthClientExpirationCheckJobConfig authClientExpirationCheckJobConfig;
    private final AuthClientExpirationCheckNightlyJobConfig authClientExpirationCheckNightlyJobConfig;
    private final SlackService slackService;
    private final ExternalApplicationService externalApplicationService;
    private final MemberSyncService memberSyncService;
    private final MemberOffboardingService memberOffboardingService;
    private final RegistryService registryService;
    private final AuthProviderClientCheckJobConfig authProviderClientCheckConfig;
    private final TeamRollupCacheService teamRollupCacheService;
    private final AzureAdUserService azureAdUserService;
    private final AuthClientService authClientService;
    private final DefaultRedirectUriValidator redirectUriValidator;
    private final InformationClassificationBackfillService informationClassificationBackfillService;

    @Tag(name = "Admin")
    @Operation(summary = "Runs Admin sync Azure users job", description = "sync user info with Azure")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @PostMapping(value = "/admin/job/syncAzureUsers", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<String> syncAzureUsers(@RequestBody(required = false) Map<String, Object> requestBody) throws ApiException {
        Authentication currentUser = SecurityContextHolder.getContext().getAuthentication();
        CompletableFuture.runAsync(() -> {
            // set security context to record user performing action in async job
            SecurityContextHolder.getContext().setAuthentication(currentUser);
            boolean cleanupEnabled = requestBody != null && (Boolean) requestBody.getOrDefault("cleanupEnabled", Boolean.FALSE);
            memberSyncService.syncAzureUsers(cleanupEnabled);
        });
        return ResponseEntity.ok("syncAzureUsers job submitted - see logs");
    }

    @Tag(name = "Admin")
    @Operation(summary = "Returns the direct reports for a manager", description = "Returns the direct reports for manager given the manager's external ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @GetMapping(value = "/admin/managerDirectReports/{managerExternalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<List<User>> managerDirectReports(@PathVariable String managerExternalId) {
        return ResponseEntity.ok(azureAdUserService.findManagerDirectReports(managerExternalId));
    }

    @Tag(name = "Admin")
    @Operation(summary = "Runs Admin sync Azure users manager job", description = "sync user manager info with Azure")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @PostMapping(value = "/admin/job/syncAzureUsersManager", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<String> syncAzureUsersManager(@RequestBody(required = false) Map<String, Object> requestBody) throws ApiException {
        Authentication currentUser = SecurityContextHolder.getContext().getAuthentication();
        CompletableFuture.runAsync(() -> {
            // set security context to record user performing action in async job
            SecurityContextHolder.getContext().setAuthentication(currentUser);
            boolean syncMembersWithExistingManager = requestBody != null && (Boolean) requestBody.getOrDefault("syncMembersWithExistingManager", Boolean.TRUE);
            boolean syncMembersWithNoManager = requestBody != null && (Boolean) requestBody.getOrDefault("syncMembersWithNoManager", Boolean.TRUE);
            if (syncMembersWithExistingManager) {
                memberSyncService.syncUsersManagerByExistingManager();
            }
            if (syncMembersWithNoManager) {
                memberSyncService.syncUsersManager();
            }
        });
        return ResponseEntity.ok("syncAzureUsersManager job submitted - see logs");
    }

    @Tag(name = "Admin")
    @Operation(summary = "Runs Cache Refresh job", description = "refreshes team rollup data cache")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @PostMapping(value = "/admin/job/refreshTeamRollupCache", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<String> refreshTeamRollupCache(@RequestBody(required = false) Map<String, Object> requestBody) throws ApiException {
        Authentication currentUser = SecurityContextHolder.getContext().getAuthentication();
        CompletableFuture.runAsync(() -> {
            // set security context to record user performing action in async job
            SecurityContextHolder.getContext().setAuthentication(currentUser);
            teamRollupCacheService.refreshCache();
        });
        return ResponseEntity.ok("refreshTeamRollupCache job submitted - see logs");
    }

    @Tag(name = "Admin")
    @Operation(summary = "Deletes a member from Registry", description = "sync user info with Azure")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @DeleteMapping(value = "/admin/member/delete", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<String> removeMemberFromRegistry(@RequestBody RemoveMemberRequest request) throws ApiException {
        boolean success = memberOffboardingService.removeMemberFromRegistry(request.getMemberId(), request.getAssigningManagerExternalId());
        return success ? ResponseEntity.noContent().build() : ResponseEntity.internalServerError().body("member could not be removed.  See logs");
    }

    @Tag(name = "Admin")
    @Operation(summary = "Returns the Auth Provider credential information", description = "Returns the Auth Provider credential information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @GetMapping(value = "/admin/authProvider/credentialInfo", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<List<CredentialInfo>> authProviderCredentialInfo() throws ApiException {
        List<CredentialInfo> credentials = new ArrayList<>();
        authProviderClientCheckConfig.getClients().forEach(authProviderClient ->
                credentials.addAll(azureApplicationService.clientCredentials(authProviderClient)));
        return ResponseEntity.ok(credentials);
    }

    @Tag(name = "Admin")
    @Operation(summary = "Returns the Slack Channel information", description = "Returns the Slack Channel information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @GetMapping(value = "/admin/slack/channelInfo/{channelId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<AdminSlackChannelInfo> slackChannelInfo(@PathVariable String channelId) throws SlackServiceException {
        Conversation result = slackService.getChannelInfo(channelId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AdminSlackChannelInfo.builder()
                .id(result.getId()).name(result.getName()).memberCount(result.getNumOfMembers()).isPrivate(result.isPrivate())
                .build());
    }

    @Tag(name = "Admin")
    @Operation(summary = "Runs an Auth Client Expiration Check", description = "Runs an Auth Client expiration check returning expired or soon to be expired auth clients")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @PostMapping(value = "/admin/job/authClientExpirationCheck", produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<String> authClientExpirationCheck(@RequestBody(required = false) Map<String, Object> requestBody) throws ApiException {
        Authentication currentUser = SecurityContextHolder.getContext().getAuthentication();
        // set security context to record user performing action in async job
        SecurityContextHolder.getContext().setAuthentication(currentUser);

        CheckAuthClientExpirationsRequest request = buildCheckAuthClientExpirationsRequest(requestBody);
        if (getValue(requestBody, "runAsync", true)) {
            CompletableFuture.runAsync(() -> authClientExpirationNotificationService.checkAuthClientsExpirations(request));
            return ResponseEntity.ok("authClientExpirationCheck job submitted - see logs");
        } else {
            AuthClientExpirationCheckJobStats jobStats = authClientExpirationNotificationService.checkAuthClientsExpirations(request);
            return ResponseEntity.ok(jobStats.toString());
        }
    }

    @Tag(name = "Admin")
    @Operation(summary = "Returns the external application info", description = "Returns the external application information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @GetMapping(value = "/admin/externalApp/{externalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<ExternalApplication> externalAppInfo(@PathVariable String externalId) throws ApiException {
        ExternalApplication result = externalApplicationService.getExternalApplication(externalId);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    @Tag(name = "Admin")
    @Operation(summary = "Updates the external ID for an application", description = "Updates the external ID for an application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Application.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @PatchMapping(value = "/admin/application/externalId", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<Application> patchApplicationExternalId(@RequestBody ApplicationExternalIdPatchRequest patchRequest) throws ApiException {
        return ResponseEntity.ok(registryService.updateApplicationExternalId(patchRequest));
    }

    @Tag(name = "Admin")
    @Operation(summary = "Check auth client redirect URI compliance",
            description = "Validates USER and USER_PKCE auth client redirect URIs against current validation rules. Returns only non-compliant clients for remediation. Optionally filter to a single clientId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = AuthClientRedirectUriComplianceResult.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @GetMapping(value = "/admin/authClient/redirectUriCompliance", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<List<AuthClientRedirectUriComplianceResult>> authClientRedirectUriCompliance(
            @RequestParam(required = false) String clientId) throws ApiException {
        List<AuthClientRedirectUriComplianceResult> nonCompliant = new ArrayList<>();
        if (clientId != null) {
            authClientService.getAuthClients(clientId).stream()
                    .filter(c -> c.getType() == AuthClientType.USER || c.getType() == AuthClientType.USER_PKCE)
                    .forEach(client -> checkClientCompliance(client, nonCompliant));
        } else {
            authClientService.getAuthClients(AuthClientType.USER).forEach(client -> checkClientCompliance(client, nonCompliant));
            authClientService.getAuthClients(AuthClientType.USER_PKCE).forEach(client -> checkClientCompliance(client, nonCompliant));
        }
        return ResponseEntity.ok(nonCompliant);
    }

    @Tag(name = "Admin")
    @Operation(summary = "Delete an auth client with a non-compliant redirect URI",
            description = "Deletes the specified auth client version after verifying the given team owns it. " +
                    "Intended for remediating non-compliant redirect URIs identified by the redirectUriCompliance endpoint.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Auth client deleted"),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "404", description = "Auth client not found", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @DeleteMapping(value = "/admin/authClient", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<Void> deleteAuthClient(@Valid @RequestBody AdminDeleteAuthClientRequest request) throws ApiException {
        AuthClientView authClient = authClientService.getAuthClient(request.getClientId(), request.getClientVersion(), null);
        List<AuthClientViewTeam> teams = authClient.getTeams();
        if (teams == null || teams.isEmpty() || !teams.get(0).getName().equalsIgnoreCase(request.getTeamName())) {
            throw new BadRequestException(ExceptionReason.AUTH_CLIENT_TEAM_MISMATCH);
        }
        registryService.deleteAuthClient(request.getClientId(), request.getClientVersion());
        return ResponseEntity.noContent().build();
    }

    @Tag(name = "Admin")
    @Operation(summary = "Runs or previews the auth client cleanup job",
            description = "Identifies and deletes stale USER_PKCE auth clients (older than minAgeDays and inactive for inactivityDays). " +
                    "Set dryRun=true to return candidates without deleting. " +
                    "Optionally override minAgeDays and inactivityDays (defaults from AuthClientCleanupJobConfig).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = AuthClientCleanupJobResponse.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @PostMapping(value = "/admin/job/authClientCleanup", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<AuthClientCleanupJobResponse> authClientCleanup(@RequestBody(required = false) Map<String, Object> requestBody) {
        boolean dryRun = getValue(requestBody, "dryRun", false);
        int minAgeDays = getValue(requestBody, "minAgeDays", authClientCleanupJobConfig.getMinAgeDays());
        int inactivityDays = getValue(requestBody, "inactivityDays", authClientCleanupJobConfig.getInactivityDays());

        if (dryRun) {
            List<AuthClientView> candidates = authClientCleanupService.getCandidates(minAgeDays, inactivityDays);
            return ResponseEntity.ok(AuthClientCleanupJobResponse.builder()
                    .dryRun(true).candidates(candidates).deleted(0).failed(0).build());
        }
        return ResponseEntity.ok(authClientCleanupService.runCleanup(minAgeDays, inactivityDays));
    }

    @Tag(name = "Admin")
    @Operation(summary = "Runs the one-time information classification backfill",
            description = "Sets information classification on applications that do not have one yet, from their compliance types. " +
                    "Applications that already have a value are not changed, so it is safe to run again. " +
                    "Applications with an unmapped compliance type are skipped and listed in the response. " +
                    "Requires app.job.information-classification-backfill.enabled=true.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = InformationClassificationBackfillResponse.class))}),
            @ApiResponse(responseCode = "400", description = "Backfill is disabled", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @PostMapping(value = "/admin/job/backfillInformationClassification", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<InformationClassificationBackfillResponse> backfillInformationClassification() {
        return ResponseEntity.ok(informationClassificationBackfillService.backfill());
    }

    private void checkClientCompliance(AuthClientView client, List<AuthClientRedirectUriComplianceResult> nonCompliant) {
        List<AuthClientRedirectUriViolation> violations = new ArrayList<>();
        if (client.getRedirectUrls() != null) {
            client.getRedirectUrls().forEach(uri -> {
                try {
                    redirectUriValidator.validate(uri, client.getType());
                } catch (ApiException e) {
                    violations.add(AuthClientRedirectUriViolation.builder()
                            .redirectUri(uri)
                            .reason(e.getReason() != null ? e.getReason().name() : null)
                            .message(e.getMessage())
                            .build());
                }
            });
        }
        if (!violations.isEmpty()) {
            List<String> teamNames = client.getTeams() == null ? List.of() :
                    client.getTeams().stream().map(AuthClientViewTeam::getName).toList();
            nonCompliant.add(AuthClientRedirectUriComplianceResult.builder()
                    .clientId(client.getClientId())
                    .clientVersion(client.getClientVersion())
                    .type(client.getType())
                    .teamNames(teamNames)
                    .violations(violations)
                    .build());
        }
    }

    CheckAuthClientExpirationsRequest buildCheckAuthClientExpirationsRequest(Map<String, Object> requestBody) {
        return CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(getValue(requestBody, "expirationDaysThreshold", authClientExpirationCheckJobConfig.getExpirationDaysThreshold()))
                .ageDaysThreshold(getValue(requestBody, "ageDaysThreshold", authClientExpirationCheckJobConfig.getAgeDaysThreshold()))
                .sendSlackNotifications(getValue(requestBody, "sendSlackNotifications", authClientExpirationCheckJobConfig.isSendSlackNotifications()))
                .sendEmailNotifications(getValue(requestBody, "sendEmailNotifications", authClientExpirationCheckJobConfig.isSendEmailNotifications()))
                .deleteExpiredClients(getValue(requestBody, "deleteExpiredClients", authClientExpirationCheckJobConfig.isDeleteExpiredClients()))
                .deleteExpiredLegacyClients(getValue(requestBody, "deleteExpiredLegacyClients", authClientExpirationCheckJobConfig.isDeleteExpiredLegacyClients()))
                .expiredClientRetentionDays(getValue(requestBody, "expiredClientRetentionDays", authClientExpirationCheckJobConfig.getExpiredClientRetentionDays()))
                .legacyClientDeleteAgeDaysThreshold(getValue(requestBody, "legacyClientDeleteAgeDaysThreshold", authClientExpirationCheckJobConfig.getLegacyClientDeleteAgeDaysThreshold()))
                .messageTemplate(authClientExpirationCheckJobConfig.getMessageTemplate())
                .docsUrl(authClientExpirationCheckJobConfig.getDocsUrl())
                .build();
    }

    @Tag(name = "Admin")
    @Operation(summary = "Runs the nightly auth client 24-hour expiration check job",
            description = "Notifies teams about auth clients whose secrets expire within the next 24 hours")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success Response"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
    })
    @PostMapping(value = "/admin/job/authClientExpirationCheckNightly", produces = MediaType.TEXT_PLAIN_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'AdminTask', 'run')")
    public ResponseEntity<String> authClientExpirationCheckNightly(@RequestBody(required = false) Map<String, Object> requestBody) throws ApiException {
        Authentication currentUser = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(currentUser);

        CheckAuthClientExpirationsRequest request = buildCheckAuthClientExpirationsNightlyRequest(requestBody);
        if (getValue(requestBody, "runAsync", true)) {
            CompletableFuture.runAsync(() -> authClientExpirationNotificationService.checkAuthClientsExpirations(request));
            return ResponseEntity.ok("authClientExpirationCheckNightly job submitted - see logs");
        } else {
            AuthClientExpirationCheckJobStats jobStats = authClientExpirationNotificationService.checkAuthClientsExpirations(request);
            return ResponseEntity.ok(jobStats.toString());
        }
    }

    CheckAuthClientExpirationsRequest buildCheckAuthClientExpirationsNightlyRequest(Map<String, Object> requestBody) {
        return CheckAuthClientExpirationsRequest.builder()
                .expirationDaysThreshold(getValue(requestBody, "expirationDaysThreshold", authClientExpirationCheckNightlyJobConfig.getExpirationDaysThreshold()))
                .minExpirationDaysThreshold(getValue(requestBody, "minExpirationDaysThreshold", authClientExpirationCheckNightlyJobConfig.getExpirationDaysLowerBound()))
                .ageDaysThreshold(getValue(requestBody, "ageDaysThreshold", authClientExpirationCheckNightlyJobConfig.getAgeDaysThreshold()))
                .sendSlackNotifications(getValue(requestBody, "sendSlackNotifications", authClientExpirationCheckNightlyJobConfig.isSendSlackNotifications()))
                .sendEmailNotifications(getValue(requestBody, "sendEmailNotifications", authClientExpirationCheckNightlyJobConfig.isSendEmailNotifications()))
                .expiredClientRetentionDays(authClientExpirationCheckNightlyJobConfig.getExpiredClientRetentionDays())
                .legacyClientDeleteAgeDaysThreshold(authClientExpirationCheckNightlyJobConfig.getLegacyClientDeleteAgeDaysThreshold())
                .messageTemplate(authClientExpirationCheckNightlyJobConfig.getMessageTemplate())
                .docsUrl(authClientExpirationCheckNightlyJobConfig.getDocsUrl())
                .notificationSubject(authClientExpirationCheckNightlyJobConfig.getNotificationSubject())
                .lastAccessTokenIssuedWithinHours(getValue(requestBody, "lastAccessTokenIssuedWithinHours", authClientExpirationCheckNightlyJobConfig.getLastAccessTokenIssuedWithinHours()))
                .build();
    }

    private <T> T getValue(Map<String, Object> requestBody, String key, T defaultValue) {
        return requestBody != null && requestBody.containsKey(key) ? (T)requestBody.get(key) : defaultValue;
    }
}
