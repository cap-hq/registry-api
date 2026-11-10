// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.controller;

import com.cvshealth.cap.registry.auth.config.RegisteredClientConfig;
import com.cvshealth.cap.registry.auth.model.Client;
import com.cvshealth.cap.registry.auth.model.ClientRequest;
import com.cvshealth.cap.registry.auth.model.ClientSecretExpiration;
import com.cvshealth.cap.registry.auth.model.ClientType;
import com.cvshealth.cap.registry.auth.model.exception.ExceptionReason;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.testcontainers.shaded.org.hamcrest.MatcherAssert.assertThat;

public class ClientControllerIntegrationTests extends ControllerIntegrationTests {
    @Autowired
    private RegisteredClientConfig registeredClientConfig;

    @BeforeEach
    void setUp() {
        setUpBeforeEachTest();
    }

    @Test
    public void getClients() {
        Client[] clients = given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/clients")
                .then()
                .statusCode(200)
                .extract()
                .as(Client[].class);
        assertThat("Client exists", Arrays.stream(clients).anyMatch(c -> c.getClientId().equals(initialClient.getClientId())));
    }

    @Test
    public void getClientsNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/clients")
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    @Test
    public void getClient() {
        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/client/" + initialClient.getClientId())
                .then()
                .statusCode(200)
                .body("clientId", is(initialClient.getClientId()));
    }

    @Test
    public void getClientNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/client/" + initialClient.getClientId())
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    @Test
    public void createClient() {
        ClientRequest request = ClientRequest.builder()
                .clientId("non-user-client")
                .type(ClientType.NON_USER)
                .accessTokenExpireMinutes(5L)
                .build();

        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/v1/client")
                .then()
                .statusCode(200)
                .body("clientId", is("non-user-client"));
    }

    @Test
    public void createClientNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .body(ClientRequest.builder().build())
                .when()
                .post("/auth/v1/client")
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    @Test
    public void createClientInvalidUrl() {
        ClientRequest request = ClientRequest.builder()
                .clientId("non-user-client")
                .type(ClientType.NON_USER)
                .redirectUrls(List.of("http://localhost:7777/callback"))
                .accessTokenExpireMinutes(5L)
                .build();

        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/v1/client")
                .then()
                .statusCode(400)
                .body("detail", is(ExceptionReason.CLIENT_REDIRECT_NOT_ALLOWED.label))
                .body("reason", is(ExceptionReason.CLIENT_REDIRECT_NOT_ALLOWED.toString()));
    }

    @Test
    public void createClientInvalidRefresh() {
        ClientRequest request = ClientRequest.builder()
                .clientId("non-user-client")
                .type(ClientType.NON_USER)
                .accessTokenExpireMinutes(5L)
                .refreshTokenExpireMinutes(10L)
                .build();

        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/v1/client")
                .then()
                .statusCode(400)
                .body("detail", is(ExceptionReason.CLIENT_REFRESH_NOT_ALLOWED.label))
                .body("reason", is(ExceptionReason.CLIENT_REFRESH_NOT_ALLOWED.toString()));
    }

    @Test
    public void createClientDuplicate() {
        ClientRequest request = ClientRequest.builder()
                .clientId(initialClient.getClientId())
                .type(initialClient.getType())
                .build();

        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/auth/v1/client")
                .then()
                .statusCode(400)
                .body("detail", is(ExceptionReason.CLIENT_DUPLICATE_FOUND.label))
                .body("reason", is(ExceptionReason.CLIENT_DUPLICATE_FOUND.toString()));
    }

    @Test
    public void updateClient() {
        ClientRequest request = ClientRequest.builder()
                .clientId(initialClient.getClientId())
                .type(initialClient.getType())
                .accessTokenExpireMinutes(initialClient.getAccessTokenExpireMinutes() + 100)
                .refreshTokenExpireMinutes(initialClient.getRefreshTokenExpireMinutes() + 100)
                .redirectUrls(initialClient.getRedirectUrls())
                .build();

        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .put("/auth/v1/client")
                .then()
                .statusCode(200)
                .body("accessTokenExpireMinutes", is(initialClient.getAccessTokenExpireMinutes().intValue() + 100))
                .body("refreshTokenExpireMinutes", is(initialClient.getRefreshTokenExpireMinutes().intValue() + 100));
    }

    @Test
    public void updateClientNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .body(ClientRequest.builder().build())
                .when()
                .put("/auth/v1/client")
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    @Test
    public void deleteClient() {
        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .when()
                .delete("/auth/v1/client/" + initialClient.getClientId())
                .then()
                .statusCode(204);
    }

    @Test
    public void deleteClientNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .when()
                .delete("/auth/v1/client/" + initialClient.getClientId())
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    @Test
    public void resetClientSecret() {
        Client client = given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/client/" + initialClient.getClientId() + "/secret")
                .then()
                .statusCode(200)
                .extract()
                .as(Client.class);
        assertThat("Client secret reset", !client.getClientSecret().equals(initialClient.getClientSecret()));
    }

    @Test
    public void resetClientSecretNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/client/" + initialClient.getClientId() + "/secret")
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    @Test
    public void getClientExpirations() {
        ClientSecretExpiration[] clients = given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/clientExpirations")
                .then()
                .statusCode(200)
                .extract()
                .as(ClientSecretExpiration[].class);
        assertThat("Client exists", Arrays.stream(clients).anyMatch(c -> c.getClientId().equals("auth-code-flow")));
    }

    @Test
    public void getClientExpirationsNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/clientExpirations")
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    @ParameterizedTest
    @EnumSource(ClientType.class)
    public void getClientsByType(ClientType type) {
        String clientId = createClientIfMissing("by-type-" + type.name().toLowerCase(), type);
        Client[] clients = given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/clients/type/" + type)
                .then()
                .statusCode(200)
                .extract()
                .as(Client[].class);
        assertThat("Client exists", Arrays.stream(clients).anyMatch(c -> c.getClientId().equals(clientId)));
        assertThat("All clients match type", Arrays.stream(clients).allMatch(c -> c.getType() == type));
    }

    @Test
    public void getClientsByTypeNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .when()
                .get("/auth/v1/clients/type/" + ClientType.USER)
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    @Test
    public void getClientsByIds() {
        String otherClientId = createClientIfMissing("by-ids-client", ClientType.NON_USER);
        Client[] clients = given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .body(List.of(initialClient.getClientId(), otherClientId, "unknown-client"))
                .when()
                .post("/auth/v1/clients/search")
                .then()
                .statusCode(200)
                .extract()
                .as(Client[].class);
        assertThat("Only requested clients returned", Arrays.stream(clients).map(Client::getClientId).sorted().toList()
                .equals(Stream.of(initialClient.getClientId(), otherClientId).sorted().toList()));
    }

    @Test
    public void getClientsByIdsEmpty() {
        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .body(List.of())
                .when()
                .post("/auth/v1/clients/search")
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    @Test
    public void getClientsByIdsOverMax() {
        int maxClientIds = registeredClientConfig.getMaxSearchClientIds();
        List<String> clientIds = IntStream.rangeClosed(0, maxClientIds).mapToObj(i -> "client-" + i).toList();
        given()
                .header("X-Api-Secret", "test")
                .contentType(ContentType.JSON)
                .body(clientIds)
                .when()
                .post("/auth/v1/clients/search")
                .then()
                .statusCode(400)
                .body("detail", is(String.format(ExceptionReason.CLIENT_IDS_OVER_MAX.label, maxClientIds)))
                .body("reason", is(ExceptionReason.CLIENT_IDS_OVER_MAX.toString()));
    }

    @Test
    public void getClientsByIdsNoAuth() {
        given()
                .header("X-Api-Secret", "wrong")
                .contentType(ContentType.JSON)
                .body(List.of(initialClient.getClientId()))
                .when()
                .post("/auth/v1/clients/search")
                .then()
                .statusCode(403)
                .body("detail", is("Invalid api secret"))
                .body("reason", is(ExceptionReason.ACCESS_DENIED.toString()));
    }

    private String createClientIfMissing(String clientId, ClientType type) {
        if (clientService.getClients().stream().noneMatch(c -> c.getClientId().equals(clientId))) {
            clientService.createClient(ClientRequest.builder()
                    .clientId(clientId)
                    .type(type)
                    .accessTokenExpireMinutes(5L)
                    .redirectUrls(type == ClientType.NON_USER ? null : List.of("http://localhost:1234/oauth-callback"))
                    .build());
        }
        return clientId;
    }
}
