// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.notification;

import com.cvshealth.cap.registry.RegistryIntegrationTests;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.model.exception.NotificationServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FreemarkerMessageTemplateProcessorTests extends RegistryIntegrationTests {

    @Autowired
    private FreemarkerMessageTemplateProcessor messageTemplateProcessor;

    @Test
    public void authClientExpiration_message() throws NotificationServiceException {
        // given
        String template = "text/auth-client-expiration.ftl";
        Map<String, Object> model = Map.of(
            "teamName", "team-1",
            "expirationDaysThreshold", 30,
            "legacyClientDeleteAgeDaysThreshold", 90,
            "inRegistryOrCapUI", "in Registry UI",
            "authClientRotationDocsUrl", "http://localhost:3000",
            "expiredClientRetentionDays", 7,
            "footer", "the-end",
            "clients", List.of(
                    Map.of(
                        "clientId", "expiring-client",
                        "clientVersion", 1,
                        "expirationType", "expiring",
                        "ageInDays", 76,
                        "expirationInDays", 14,
                        "issuedAt", "expiring-issued-at",
                        "expiresAt", "expiring-expires-at",
                        "lastAccessTokenIssuedAt", "expiring-last-token-issued-at"),
                    Map.of(
                        "clientId", "expired-client",
                        "clientVersion", 2,
                        "expirationType", "expired",
                        "ageInDays", 95,
                        "expirationInDays", -5,
                        "issuedAt", "expired-issued-at",
                        "expiresAt", "expired-expires-at",
                        "lastAccessTokenIssuedAt", "expired-last-token-issued-at"),
                    Map.of(
                        "clientId", "deleted-client",
                        "clientVersion", 1,
                        "expirationType", "deleted",
                        "ageInDays", 100,
                        "expirationInDays", -10,
                        "issuedAt", "deleted-issued-at",
                        "expiresAt", "deleted-expires-at"),
                    Map.of(
                        "clientId", "legacy-client",
                        "clientVersion", 1,
                        "expirationType", "no-expiration",
                        "ageInDays", 76,
                        "issuedAt", "legacy-issued-at",
                        "lastAccessTokenIssuedAt", "legacy-last-token-issued-at"))
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).contains("http://localhost:3000");
    }

    @Test
    public void authClientExpiration_message_omitsDocsUrlSentenceWhenUndefined() throws NotificationServiceException {
        // given
        String template = "text/auth-client-expiration.ftl";
        Map<String, Object> model = Map.of(
            "teamName", "team-1",
            "expirationDaysThreshold", 30,
            "legacyClientDeleteAgeDaysThreshold", 90,
            "inRegistryOrCapUI", "in Registry UI",
            "authClientRotationDocsUrl", "UNDEFINED",
            "expiredClientRetentionDays", 7,
            "footer", "the-end",
            "clients", List.of(
                    Map.of(
                        "clientId", "expiring-client",
                        "clientVersion", 1,
                        "expirationType", "expiring",
                        "ageInDays", 76,
                        "expirationInDays", 14,
                        "issuedAt", "expiring-issued-at",
                        "expiresAt", "expiring-expires-at",
                        "lastAccessTokenIssuedAt", "expiring-last-token-issued-at"))
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("UNDEFINED");
        assertThat(result).doesNotContain("Documentation link");
    }

    @Test
    public void authClientExpiration24h_message() throws NotificationServiceException {
        // given
        String template = "text/auth-client-expiration-24h.ftl";
        Map<String, Object> model = Map.of(
            "teamName", "team-1",
            "expirationHoursThreshold", 24,
            "inRegistryOrCapUI", "in Registry UI",
            "authClientRotationDocsUrl", "http://localhost:3000",
            "footer", "the-end",
            "clients", List.of(
                    Map.of(
                        "clientId", "expiring-client",
                        "clientVersion", 1,
                        "expirationInDays", 0,
                        "issuedAt", "expiring-issued-at",
                        "expiresAt", "expiring-expires-at",
                        "lastAccessTokenIssuedAt", "expiring-last-token-issued-at"))
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).contains("http://localhost:3000");
    }

    @Test
    public void authClientExpiration24h_message_omitsDocsUrlSentenceWhenUndefined() throws NotificationServiceException {
        // given
        String template = "text/auth-client-expiration-24h.ftl";
        Map<String, Object> model = Map.of(
            "teamName", "team-1",
            "expirationHoursThreshold", 24,
            "inRegistryOrCapUI", "in Registry UI",
            "authClientRotationDocsUrl", "UNDEFINED",
            "footer", "the-end",
            "clients", List.of(
                    Map.of(
                        "clientId", "expiring-client",
                        "clientVersion", 1,
                        "expirationInDays", 0,
                        "issuedAt", "expiring-issued-at",
                        "expiresAt", "expiring-expires-at",
                        "lastAccessTokenIssuedAt", "expiring-last-token-issued-at"))
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("UNDEFINED");
        assertThat(result).doesNotContain("Documentation link");
    }

    @Test
    public void applicationExternalIdMismatch_message() throws NotificationServiceException {
        // given
        String template = "text/application-external-id-mismatch.ftl";
        Map<String, Object> model = Map.of(
                "teamName", "Team-1",
                "applicationName", "application-1",
                "rollupName", "rollup SVP",
                "externalId", "externalId-1",
                "externalIdRollupName", "externalId SVP",
                "docsUrl", "http://localhost:3000",
                "footer", "footer"
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).contains("http://localhost:3000");
    }

    @Test
    public void applicationExternalIdMismatch_message_omitsDocsUrlSentenceWhenUndefined() throws NotificationServiceException {
        // given
        String template = "text/application-external-id-mismatch.ftl";
        Map<String, Object> model = Map.of(
                "teamName", "Team-1",
                "applicationName", "application-1",
                "rollupName", "rollup SVP",
                "externalId", "externalId-1",
                "externalIdRollupName", "externalId SVP",
                "docsUrl", "UNDEFINED",
                "footer", "footer"
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("UNDEFINED");
        assertThat(result).doesNotContain("can be found at");
    }

    @Test
    public void applicationTransfer_pendingMessage() throws NotificationServiceException {
        // given
        String template = "text/application-transfer.ftl";
        Map<String, Object> model = Map.of(
                "applicationName", "application-1",
                "sourceTeamName", "Team-1",
                "receiveTeamName", "Team-2",
                "transferStatus", "PENDING",
                "transferComments", "",
                "inRegistryOrCapUI", "in Registry UI",
                "footer", "footer"
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).contains("The application transfer can be reviewed/accepted");
    }

    @Test
    public void applicationTransfer_completedMessage() throws NotificationServiceException {
        // given
        String template = "text/application-transfer.ftl";
        Map<String, Object> model = Map.of(
                "applicationName", "application-1",
                "sourceTeamName", "Team-1",
                "receiveTeamName", "Team-2",
                "transferStatus", "COMPLETED",
                "transferComments", "accept",
                "inRegistryOrCapUI", "in Registry UI",
                "footer", "footer"
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).contains("Comments: accept");
        assertThat(result).doesNotContain("The application transfer can be reviewed/accepted");
    }

    @Test
    public void notificationMessageError() {
        // given
        String template = "text/application-transfer.ftl";
        Map<String, Object> model = Collections.emptyMap();

        // when
        NotificationServiceException thrown = assertThrows(NotificationServiceException.class, () -> messageTemplateProcessor.process(template, model));

        // then
        assertThat(thrown).isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(thrown.getMessage()).isEqualTo(ExceptionReason.NOTIFICATION_MESSAGE_ERROR.label);
    }

    @Test
    public void applicationTransferBySystem() throws NotificationServiceException {
        String template = "text/application-transfer-by-system.ftl";
        Map<String, Object> model = Map.of(
                "greeting", "ManagerFirstName",
                "previousOwnerName", "MemberLast, MemberFirst",
                "applicationNames", List.of("application-1", "application-2", "application-3").toString(),
                "footer", "footer"
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void teamTransferBySystem() throws NotificationServiceException {
        String template = "text/team-transfer-by-system.ftl";
        Map<String, Object> model = Map.of(
                "greeting", "ManagerFirstName",
                "previousOwnerName", "MemberLast, MemberFirst",
                "teamName", "Team-1",
                "footer", "footer"
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
    }

    @Test
    public void applicationExternalIdUpdate_message() throws NotificationServiceException {
        // given
        String template = "text/application-external-id-updated.ftl";
        Map<String, Object> model = Map.of(
                "teamName", "Team-1",
                "applicationName", "application-1",
                "externalId", "externalId-1",
                "docsUrl", "http://localhost:3000/somedocsurl",
                "footer", "footer"
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).contains("http://localhost:3000/somedocsurl");
    }

    @Test
    public void applicationExternalIdUpdate_message_omitsDocsUrlSentenceWhenUndefined() throws NotificationServiceException {
        // given
        String template = "text/application-external-id-updated.ftl";
        Map<String, Object> model = Map.of(
                "teamName", "Team-1",
                "applicationName", "application-1",
                "externalId", "externalId-1",
                "docsUrl", "UNDEFINED",
                "footer", "footer"
        );

        // when
        String result = messageTemplateProcessor.process(template, model);

        // then
        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("UNDEFINED");
        assertThat(result).doesNotContain("can be found at");
    }
}