// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth.controller;

import com.cvshealth.cap.registry.auth.model.JwkPublicKey;
import com.cvshealth.cap.registry.auth.model.exception.ApiException;
import com.cvshealth.cap.registry.auth.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.auth.oauth.JwksRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1")
public class JwksController {

    private static final String API_SECRET = "X-Api-Secret";

    private final JwksRepository jwksRepository;

    @Value("${registry-auth.api-secret}")
    private String apiSecret;

    public JwksController(JwksRepository jwksRepository) {
        this.jwksRepository = jwksRepository;
    }

    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<JwkPublicKey>> getJwks(@RequestHeader(API_SECRET) String secret) {
        validateApiSecret(secret);
        return ResponseEntity.ok(jwksRepository.findAll());
    }

    private void validateApiSecret(String secret) {
        if (!this.apiSecret.equals(secret)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "", ExceptionReason.ACCESS_DENIED, "Invalid api secret");
        }
    }
}
