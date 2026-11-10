// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.controller;

import com.cvshealth.cap.registry.auth.model.Client;
import com.cvshealth.cap.registry.auth.model.ClientRequest;
import com.cvshealth.cap.registry.auth.model.ClientSecretExpiration;
import com.cvshealth.cap.registry.auth.model.ClientType;
import com.cvshealth.cap.registry.auth.model.exception.ApiException;
import com.cvshealth.cap.registry.auth.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.auth.service.ClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class ClientController {
    private final ClientService clientService;
    private final static String API_SECRET = "X-Api-Secret";
    @Value("${registry-auth.api-secret}")
    private String apiSecret;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping(value = "/clients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Client>> getRegisteredClients(@RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(clientService.getClients());
    }

    @GetMapping(value = "/client/{clientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Client> getRegisteredClient(@PathVariable String clientId, @RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(clientService.getClient(clientId));
    }

    @GetMapping(value = "/clients/type/{type}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Client>> getClientsByType(@PathVariable ClientType type, @RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(clientService.getClientsByType(type));
    }

    @PostMapping(value = "/clients/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Client>> getClients(@RequestBody List<String> clientIds, @RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(clientService.getClientsByIds(clientIds));
    }

    @PostMapping(value = "/client", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Client> createClient(@RequestBody ClientRequest client, @RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(clientService.createClient(client));
    }

    @PutMapping(value = "/client", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Client> updateClient(@RequestBody ClientRequest client, @RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(clientService.updateClient(client));
    }

    @GetMapping(value = "/client/{clientId}/secret", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Client> resetClientSecret(@PathVariable String clientId, @RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(clientService.resetClientSecret(clientId));
    }

    @DeleteMapping(value = "/client/{clientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteClient(@PathVariable String clientId, @RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        clientService.deleteClient(clientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/clientExpirations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ClientSecretExpiration>> getRegisteredClientExpirations(@RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(clientService.findAllClientSecretExpirations());
    }


    private void validateApiSecret(String secret) {
        if (!this.apiSecret.equals(secret)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "", ExceptionReason.ACCESS_DENIED, "Invalid api secret");
        }
    }

}
