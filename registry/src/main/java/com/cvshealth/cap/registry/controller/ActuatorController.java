// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import javax.net.ssl.SSLSession;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("${service.context-path}/")
public class ActuatorController {

	private static final String CIPHER_SUITE_ATTRIBUTE = "jakarta.servlet.request.cipher_suite";
	private static final String SSL_SESSION_ATTRIBUTE = "jakarta.servlet.request.ssl_session";
	private static final String TOMCAT_TLS_VERSION_ATTRIBUTE = "org.apache.tomcat.util.net.secure_protocol_version";

	@Tag(name = "Actuator")
	@Operation(summary = "Fetches TLS details for the current request", description = "Returns negotiated TLS metadata for the current HTTPS request")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Success Response", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))}),
			@ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))})
	})
	@GetMapping(value = "/actuator/tls-info", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> tlsInfo(HttpServletRequest request) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("tlsVersion", resolveTlsVersion(request));
		response.put("cipherSuite", request.getAttribute(CIPHER_SUITE_ATTRIBUTE));
		response.put("timestamp", Instant.now().toString());
		return ResponseEntity.ok(response);
	}

	private String resolveTlsVersion(HttpServletRequest request) {
		Object sslSessionAttribute = request.getAttribute(SSL_SESSION_ATTRIBUTE);
		if (sslSessionAttribute instanceof SSLSession sslSession) {
			return sslSession.getProtocol();
		}
		Object tomcatTlsVersion = request.getAttribute(TOMCAT_TLS_VERSION_ATTRIBUTE);
		return tomcatTlsVersion instanceof String ? (String) tomcatTlsVersion : null;
	}
}