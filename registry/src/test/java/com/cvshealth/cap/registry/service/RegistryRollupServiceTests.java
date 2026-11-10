// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service;

import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.TeamType;
import com.cvshealth.cap.registry.model.rollup.LineOfBusinessView;
import com.cvshealth.cap.registry.model.rollup.TeamRollupResult;
import com.cvshealth.cap.registry.model.rollup.TeamRollupView;
import com.cvshealth.cap.registry.model.rollup.UserView;
import com.cvshealth.cap.registry.persistent.base.SecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RegistryRollupServiceTests {

    @Mock
    private Supplier<WebClient> rollupApiWebClient;
    @Mock
    private WebClient webClient;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RegistryRollupService createService(boolean lineOfBusinessEnabled) {
        return new RegistryRollupService(rollupApiWebClient, lineOfBusinessEnabled);
    }

    @SuppressWarnings("unchecked")
    private HttpGraphQlClient setupGraphQlClientMock(TeamRollupResult result) {
        HttpGraphQlClient mockClient = mock(HttpGraphQlClient.class, RETURNS_DEEP_STUBS);
        when(mockClient.document(anyString()).retrieve(anyString()).toEntity(TeamRollupResult.class))
                .thenReturn(Mono.justOrEmpty(result));
        return mockClient;
    }

    // ==================== getTeamRollup(String teamName) ====================

    @Test
    void getTeamRollup_withTeamName_whenTeamFound_returnsFirstTeamView() {
        String teamName = "TestTeam";
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name(teamName)
                .managers(List.of(
                        UserView.builder().displayName("Manager One").build(),
                        UserView.builder().displayName("Manager Two").build()
                ))
                .build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            TeamRollupView result = service.getTeamRollup(teamName);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(teamName);
            assertThat(result.getManagers()).hasSize(2);
        }
    }

    @Test
    void getTeamRollup_withTeamName_whenLobEnabled_includesLobInQuery() {
        String teamName = "CAP";
        LineOfBusinessView lob = LineOfBusinessView.builder().name("LOB1").description("Line 1").build();
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name(teamName)
                .lineOfBusiness(lob).build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(true);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);
            clearInvocations(mockClient);

            TeamRollupView result = service.getTeamRollup(teamName);

            assertThat(result).isNotNull();
            assertThat(result.getLineOfBusiness()).isNotNull();
            assertThat(result.getLineOfBusiness().getName()).isEqualTo("LOB1");
            verify(mockClient).document(argThat(doc -> doc.contains("lineOfBusiness")));
        }
    }

    @Test
    void getTeamRollup_withTeamName_whenLobDisabled_excludesLobFromQuery() {
        String teamName = "TestTeam";
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name(teamName).build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);
            clearInvocations(mockClient);

            service.getTeamRollup(teamName);

            verify(mockClient).document(argThat(doc -> !doc.contains("lineOfBusiness")));
        }
    }

    @Test
    void getTeamRollup_withTeamName_includesTeamNameFilterInQuery() {
        String teamName = "MyTeam";
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name(teamName).build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);
            clearInvocations(mockClient);

            service.getTeamRollup(teamName);

            verify(mockClient).document(argThat(doc -> doc.contains(teamName)));
        }
    }

    @Test
    void getTeamRollup_withTeamName_whenResultIsNull_returnsNull() {
        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(null);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            TeamRollupView result = service.getTeamRollup("NonExistent");

            assertThat(result).isNull();
        }
    }

    @Test
    void getTeamRollup_withTeamName_whenTeamsListEmpty_returnsNull() {
        when(rollupApiWebClient.get()).thenReturn(webClient);
        TeamRollupResult emptyResult = TeamRollupResult.builder().teams(List.of()).build();
        HttpGraphQlClient mockClient = setupGraphQlClientMock(emptyResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            TeamRollupView result = service.getTeamRollup("NonExistent");

            assertThat(result).isNull();
        }
    }

    @Test
    void getTeamRollup_withTeamName_whenNoBearerToken_usesJobsWebClient() {
        // No security context set → SecurityHelper.getBearerToken() returns null → jobs client used
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name("Team1").build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            service.getTeamRollup("Team1");

            staticMock.verify(() -> HttpGraphQlClient.builder(webClient));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
            verify(builderMock).headers(headersCaptor.capture());
            HttpHeaders headers = new HttpHeaders();
            headersCaptor.getValue().accept(headers);
            assertThat(headers.containsHeader("Authorization")).isFalse();
        }
    }

    @Test
    void getTeamRollup_withTeamName_whenBearerTokenPresent_usesRollupWebClientWithAuthHeader() {
        String token = "test-jwt-token";
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name("Team1").build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class);
             MockedStatic<SecurityHelper> securityMock = mockStatic(SecurityHelper.class)) {

            securityMock.when(SecurityHelper::getBearerToken).thenReturn(token);

            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            service.getTeamRollup("Team1");

            staticMock.verify(() -> HttpGraphQlClient.builder(webClient));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
            verify(builderMock).headers(headersCaptor.capture());
            HttpHeaders headers = new HttpHeaders();
            headersCaptor.getValue().accept(headers);
            assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer " + token);
        }
    }

    @Test
    void getTeamRollup_withTeamName_includesPersonalTeamsFilterInQuery() {
        String teamName = "MyTeam";
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name(teamName).build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);
            clearInvocations(mockClient);

            service.getTeamRollup(teamName);

            verify(mockClient).document(argThat(doc -> doc.contains("includePersonalTeams: true")));
        }
    }

    // ==================== getTeamRollup(PaginationInput) ====================

    @Test
    void getTeamRollup_withPaginationInput_returnsTeamRollupResult() {
        String teamName = "CAP";
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name(teamName)
                .managers(List.of(UserView.builder().displayName("Manager One").build()))
                .build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            TeamRollupResult result = service.getTeamRollup(new PaginationInput(1, 100), TeamType.SHARED);

            assertThat(result).isNotNull();
            assertThat(result.getTeams()).hasSize(1);
            assertThat(result.getTeams().get(0).getName()).isEqualTo(teamName);
            assertThat(result.getTeams().get(0).getManagers()).hasSize(1);
        }
    }

    @Test
    void getTeamRollup_withPaginationInput_whenLobEnabled_includesLobInQuery() {
        LineOfBusinessView lob = LineOfBusinessView.builder().name("LOB1").build();
        TeamRollupView teamView = TeamRollupView.builder().id(1L).name("Team1").lineOfBusiness(lob).build();
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of(teamView)).build();

        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(true);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);
            clearInvocations(mockClient);

            service.getTeamRollup(new PaginationInput(1, 100), TeamType.SHARED);

            verify(mockClient).document(argThat(doc -> doc.contains("lineOfBusiness")));
        }
    }

    @Test
    void getTeamRollup_withPaginationInput_whenResultIsNull_returnsNull() {
        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(null);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            TeamRollupResult result = service.getTeamRollup(new PaginationInput(1, 50), TeamType.SHARED);

            assertThat(result).isNull();
        }
    }

    @Test
    void getTeamRollup_withPaginationInput_includesPaginationParamsInQuery() {
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of()).build();
        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);
            clearInvocations(mockClient);

            service.getTeamRollup(new PaginationInput(3, 25), TeamType.SHARED);

            verify(mockClient).document(argThat(doc ->
                    doc.contains("pageSize: 25") && doc.contains("pageNumber: 3")));
        }
    }

    @Test
    void getTeamRollup_withPaginationInput_includesTeamTypeFilterInQuery() {
        TeamRollupResult teamRollupResult = TeamRollupResult.builder().teams(List.of()).build();
        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);
            clearInvocations(mockClient);

            service.getTeamRollup(new PaginationInput(1, 50), TeamType.SHARED);

            verify(mockClient).document(argThat(doc -> doc.contains("teamType: SHARED")));
        }
    }

    @Test
    void getTeamRollup_withPaginationInput_whenNoBearerToken_usesJobsWebClient() {
        TeamRollupResult teamRollupResult = TeamRollupResult.builder()
                .teams(List.of(TeamRollupView.builder().id(1L).name("Team1").build()))
                .build();
        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class)) {
            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            service.getTeamRollup(new PaginationInput(1, 100), TeamType.SHARED);

            staticMock.verify(() -> HttpGraphQlClient.builder(webClient));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
            verify(builderMock).headers(headersCaptor.capture());
            HttpHeaders headers = new HttpHeaders();
            headersCaptor.getValue().accept(headers);
            assertThat(headers.containsHeader("Authorization")).isFalse();
        }
    }

    @Test
    void getTeamRollup_withPaginationInput_whenBearerTokenPresent_usesRollupWebClientWithAuthHeader() {
        String token = "test-jwt-token";
        TeamRollupResult teamRollupResult = TeamRollupResult.builder()
                .teams(List.of(TeamRollupView.builder().id(1L).name("Team1").build()))
                .build();
        when(rollupApiWebClient.get()).thenReturn(webClient);
        HttpGraphQlClient mockClient = setupGraphQlClientMock(teamRollupResult);
        RegistryRollupService service = createService(false);

        try (MockedStatic<HttpGraphQlClient> staticMock = mockStatic(HttpGraphQlClient.class);
             MockedStatic<SecurityHelper> securityMock = mockStatic(SecurityHelper.class)) {

            securityMock.when(SecurityHelper::getBearerToken).thenReturn(token);

            HttpGraphQlClient.Builder<?> builderMock = mock(HttpGraphQlClient.Builder.class);
            staticMock.when(() -> HttpGraphQlClient.builder(any(WebClient.class))).thenReturn(builderMock);
            doReturn(builderMock).when(builderMock).headers(any());
            when(builderMock.build()).thenReturn(mockClient);

            service.getTeamRollup(new PaginationInput(1, 100), TeamType.SHARED);

            staticMock.verify(() -> HttpGraphQlClient.builder(webClient));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<HttpHeaders>> headersCaptor = ArgumentCaptor.forClass(Consumer.class);
            verify(builderMock).headers(headersCaptor.capture());
            HttpHeaders headers = new HttpHeaders();
            headersCaptor.getValue().accept(headers);
            assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer " + token);
        }
    }
}
