// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.auth;

import com.cvshealth.cap.registry.auth.model.TokenResponse;
import com.cvshealth.cap.registry.auth.oauth.CustomJdbcOauth2AuthorizationService;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
public class RegistryAuthorizationServerIntegrationTests extends RegistryIntegrationTests {
	private final static String REDIRECT_URI = "http://localhost:8080/oauth-callback";
	private final static String WILDCARD_REDIRECT_URI = "http://wildcard:8080/oauth-callback";
	private final static String REDIRECT_URI_PREFIX = "/oauth-callback";

	private static final String AUTHORIZATION_CODE_PKCE_REQUEST = UriComponentsBuilder
			.fromPath("/auth/oauth2/authorize")
			.queryParam("response_type", "code")
			.queryParam("client_id", "auth-code-flow-pkce")
			.queryParam("scope", "openid+profile+email+App.User")
			.queryParam("redirect_uri", REDIRECT_URI)
			.queryParam("code_challenge", "--CHALLENGE--")
			.queryParam("code_challenge_method", "S256")
			.toUriString();

	private static final String AUTHORIZATION_CODE_REQUEST = UriComponentsBuilder
			.fromPath("/auth/oauth2/authorize")
			.queryParam("response_type", "code")
			.queryParam("client_id", "auth-code-flow")
			.queryParam("scope", "openid+profile+email+App.User")
			.queryParam("redirect_uri", REDIRECT_URI)
			.queryParam("state", "abc123")
			.toUriString();

	private WebDriver driver;

	private WireMockServer wiremock;

	@Autowired
	private Environment env;

	@Autowired
	@Qualifier("authJdbcTemplate")
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CustomJdbcOauth2AuthorizationService authorizationService;

	@BeforeAll
	protected static void beforeAll() {
		WebDriverManager.chromedriver().setup();
	}

	@BeforeEach
	public void setUp() {
		wiremock = new WireMockServer(options().port(8333));
		wiremock.start();

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		driver = new ChromeDriver(options);
	}

	@AfterEach
	public void tearDown() {
		driver.quit();
		wiremock.stop();
	}

	/**
	 * Authorization Code Flow with PKCE
	 *	1 - Create code verifier: Generate a code_verifier that will be sent to auth server to request tokens.
	 *	2 - Create code challenge: Generate a code_challenge from the code_verifier that will be sent to auth server to request an authorization_code.
	 *	3 - Authorize user: Request the user's authorization (via mock OIDC substitute for Azure) and redirect back to your app with an authorization_code
	 *	4 - Request token: Exchange your authorization_code and code_verifier for token.
	 *	5 - Refresh token: Exchange your refresh_token for a new token.
	 */
	@Test
	public void whenNotLoggedInAndRequestingAuthCodePkceThenRedirectToLoginAndRequestTokenAndRefreshToken() throws IOException, NoSuchAlgorithmException, URISyntaxException {
		// 1 - Create code verifier
		String verifier = getCodeVerifier();

		// 2 - Create code challenge
		String challenge = getCodeChallenge(verifier);

		// 3 - Authorize user
		String host = "http://localhost:" + serverPort;
		driver.get(host + AUTHORIZATION_CODE_PKCE_REQUEST.replace("--CHALLENGE--", challenge));
		assertLoginPage();
		signIn("user1", "pwd");
		assertRedirectPage();

		// 4 - Request token
		String code = getQueryStringParam(driver.getCurrentUrl(), "code");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "authorization_code");
		map.add("code", code);
		map.add("client_id", "auth-code-flow-pkce");
		map.add("redirect_uri", REDIRECT_URI);
		map.add("code_verifier", verifier);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		RestTemplate restTemplate = new RestTemplate();

		// mock web response for app reg API service claims fetch
		registerGraphQLMock();

		ResponseEntity<TokenResponse> response = restTemplate.exchange(host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse1 = response.getBody();

		assertThat(tokenResponse1).isNotNull();
		assertThat(tokenResponse1.getId_token()).isNotNull();
		assertThat(tokenResponse1.getAccess_token()).isNotNull();
		assertThat(tokenResponse1.getToken_type()).isEqualTo("Bearer");
		assertThat(tokenResponse1.getRefresh_token()).isNotNull();
		assertThat(tokenResponse1.getError()).isNull();

		// 5 - Refresh token for public client
		headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		map = new LinkedMultiValueMap<>();
		map.add("grant_type", "refresh_token");
		map.add("client_id", "auth-code-flow-pkce");
		map.add("redirect_uri", REDIRECT_URI);
		map.add("refresh_token", tokenResponse1.getRefresh_token());
		request = new HttpEntity<>(map, headers);

		response = restTemplate.exchange(host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse2 = response.getBody();

		assertThat(tokenResponse2).isNotNull();
		assertThat(tokenResponse1.getId_token()).isNotNull();
		assertThat(tokenResponse2.getAccess_token()).isNotNull();
		assertThat(tokenResponse2.getToken_type()).isEqualTo("Bearer");
		assertThat(tokenResponse2.getRefresh_token()).isNotNull();
		assertThat(tokenResponse2.getError()).isNull();
	}

	/**
	 * Authorization Code Flow
	 *	1 - Authorize user: Request the user's authorization (via mock OIDC substitute for Azure) and redirect back to your app with an authorization_code
	 *	2 - Request token: Exchange your authorization_code for token.
	 *	3 - Refresh token: Exchange your refresh_token for a new token.
	 */
	@Test
	public void whenNotLoggedInAndRequestingAuthCodeThenRedirectToLoginAndRequestTokenAndRefreshToken() throws IOException, URISyntaxException {
		// 1 - Authorize user
		String host = "http://localhost:" + serverPort;
		driver.get(host + AUTHORIZATION_CODE_REQUEST);
		assertLoginPage();
		signIn("user1", "pwd");
		assertRedirectPage();

		// 2 - Request token
		String code = getQueryStringParam(driver.getCurrentUrl(), "code");
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("auth-code-flow", "test2");
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "authorization_code");
		map.add("code", code);
		map.add("redirect_uri", REDIRECT_URI);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		RestTemplate restTemplate = new RestTemplate();

		// mock web response for app reg API service claims fetch
		registerGraphQLMock();

		ResponseEntity<TokenResponse> response = restTemplate.exchange(host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse1 = response.getBody();

		assertThat(tokenResponse1).isNotNull();
		assertThat(tokenResponse1.getId_token()).isNotNull();
		assertThat(tokenResponse1.getAccess_token()).isNotNull();
		assertThat(tokenResponse1.getToken_type()).isEqualTo("Bearer");
		assertThat(tokenResponse1.getRefresh_token()).isNotNull();
		assertThat(tokenResponse1.getError()).isNull();

		// app2 is RESTRICTED and tagged pci, so it is in both claims; "app" has a level this service does not know and is in neither
		wiremock.com.fasterxml.jackson.databind.JsonNode claims = jwtPayload(tokenResponse1.getAccess_token());
		assertThat(claims.get("app_information_classification_restricted").toString()).isEqualTo("[\"app2\"]");
		assertThat(claims.get("app_pci").toString()).isEqualTo("[\"app2\"]");

		// Verify Access token can be used to get userinfo
		RestAssured.baseURI = "http://localhost:" + serverPort;
		given()
				.header("Authorization", "Bearer " + tokenResponse1.getAccess_token())
				.accept(ContentType.JSON)
				.when()
				.get("/auth/userinfo")
				.then()
				.statusCode(200)
				.body("sub", is("User 1"))
				.body("email", is("user.1@example.com"))
				.body("preferred_username", is("user.1@example.com"))
				.body("name", is("Test User"))
				.body("given_name", is("Test"))
				.body("family_name", is("User"));

		// 3 - Refresh token for secured client
		headers = new HttpHeaders();
		headers.setBasicAuth("auth-code-flow", "test2");
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		map = new LinkedMultiValueMap<>();
		map.add("grant_type", "refresh_token");
		map.add("refresh_token", tokenResponse1.getRefresh_token());
		request = new HttpEntity<>(map, headers);

		response = restTemplate.exchange(host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse2 = response.getBody();

		assertThat(tokenResponse2).isNotNull();
		assertThat(tokenResponse1.getId_token()).isNotNull();
		assertThat(tokenResponse2.getAccess_token()).isNotNull();
		assertThat(tokenResponse2.getToken_type()).isEqualTo("Bearer");
		assertThat(tokenResponse2.getRefresh_token()).isNotNull();
		assertThat(tokenResponse2.getError()).isNull();
	}

	@Test
	public void whenNotLoggedInAndRequestingAuthCode_withVersionedAuthClientId_thenRedirectToLoginAndRequestTokenAndRefreshToken() throws IOException, URISyntaxException {
		testAuthCodeFlow_loginAndRequestAuthCode_andRefreshToken("TestUIClient-v2", "test2");
	}

	/**
	 * Authorization Code Flow
	 *	1 - Authorize user: Request the user's authorization (via mock OIDC substitute for Azure) and redirect back to your app with an authorization_code
	 *	2 - Request token: Exchange your authorization_code for token.
	 *	3 - Refresh token: Exchange your refresh_token for a new token.
	 */
	private void testAuthCodeFlow_loginAndRequestAuthCode_andRefreshToken(String clientId, String clientSecret) throws IOException, URISyntaxException {
		// 1 - Authorize user
		String host = "http://localhost:" + serverPort;
		String uri = UriComponentsBuilder
				.fromPath("/auth/oauth2/authorize")
				.queryParam("response_type", "code")
				.queryParam("client_id", clientId)
				.queryParam("scope", "openid+profile+email+App.User")
				.queryParam("redirect_uri", REDIRECT_URI)
				.queryParam("state", "abc123")
				.toUriString();
		driver.get(host + uri);
		assertLoginPage();
		signIn("user1", "pwd");
		assertRedirectPage();

		// 2 - Request token
		String code = getQueryStringParam(driver.getCurrentUrl(), "code");
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(clientId, clientSecret);
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "authorization_code");
		map.add("code", code);
		map.add("redirect_uri", REDIRECT_URI);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		RestTemplate restTemplate = new RestTemplate();

		// mock web response for app reg API service claims fetch
		registerGraphQLMock();

		ResponseEntity<TokenResponse> response = restTemplate.exchange(host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse1 = response.getBody();

		assertThat(tokenResponse1).isNotNull();
		assertThat(tokenResponse1.getId_token()).isNotNull();
		assertThat(tokenResponse1.getAccess_token()).isNotNull();
		assertThat(tokenResponse1.getToken_type()).isEqualTo("Bearer");
		assertThat(tokenResponse1.getRefresh_token()).isNotNull();
		assertThat(tokenResponse1.getError()).isNull();

		// Verify Access token can be used to get userinfo
		RestAssured.baseURI = "http://localhost:" + serverPort;
		given()
				.header("Authorization", "Bearer " + tokenResponse1.getAccess_token())
				.accept(ContentType.JSON)
				.when()
				.get("/auth/userinfo")
				.then()
				.statusCode(200)
				.body("sub", is("User 1"))
				.body("email", is("user.1@example.com"))
				.body("preferred_username", is("user.1@example.com"))
				.body("name", is("Test User"))
				.body("given_name", is("Test"))
				.body("family_name", is("User"));

		// 3 - Refresh token for secured client
		headers = new HttpHeaders();
		headers.setBasicAuth(clientId, clientSecret);
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		map = new LinkedMultiValueMap<>();
		map.add("grant_type", "refresh_token");
		map.add("refresh_token", tokenResponse1.getRefresh_token());
		request = new HttpEntity<>(map, headers);

		response = restTemplate.exchange(host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse2 = response.getBody();

		assertThat(tokenResponse2).isNotNull();
		assertThat(tokenResponse1.getId_token()).isNotNull();
		assertThat(tokenResponse2.getAccess_token()).isNotNull();
		assertThat(tokenResponse2.getToken_type()).isEqualTo("Bearer");
		assertThat(tokenResponse2.getRefresh_token()).isNotNull();
		assertThat(tokenResponse2.getError()).isNull();
	}

	/**
	 * Ordering guard for Finding 5: verifies that SessionExpiryOAuth2RefreshTokenAuthenticationProvider
	 * runs BEFORE Spring's built-in OAuth2RefreshTokenAuthenticationProvider. If the provider ordering
	 * ever changes (e.g. a Spring AS upgrade switches prepend to append), this test will fail with a
	 * 200 instead of 400, catching the regression before it reaches production.
	 */
	@Test
	public void refreshToken_afterSessionExpiry_returnsInvalidGrant() throws IOException, URISyntaxException {
		// 1 - Authorize user via full browser flow
		String host = "http://localhost:" + serverPort;
		driver.get(host + AUTHORIZATION_CODE_REQUEST);
		assertLoginPage();
		signIn("user1", "pwd");
		assertRedirectPage();

		// 2 - Exchange authorization code for tokens
		String code = getQueryStringParam(driver.getCurrentUrl(), "code");
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("auth-code-flow", "test2");
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "authorization_code");
		map.add("code", code);
		map.add("redirect_uri", REDIRECT_URI);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		RestTemplate restTemplate = new RestTemplate();
		registerGraphQLMock();

		ResponseEntity<TokenResponse> response = restTemplate.exchange(
				host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse = response.getBody();
		assertThat(tokenResponse).isNotNull();
		String refreshToken = tokenResponse.getRefresh_token();
		assertThat(refreshToken).isNotNull();

		// 3 - Look up the authorization record and expire its session in the DB
		OAuth2Authorization authorization = authorizationService.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);
		assertThat(authorization).isNotNull();
		jdbcTemplate.update(
				"UPDATE oauth2_authorization SET session_expires_at = ? WHERE id = ?",
				Timestamp.from(Instant.now().minusSeconds(60)),
				authorization.getId());

		// 4 - Attempt to use the refresh token; session expiry must block the grant
		try {
			headers = new HttpHeaders();
			headers.setBasicAuth("auth-code-flow", "test2");
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			map = new LinkedMultiValueMap<>();
			map.add("grant_type", "refresh_token");
			map.add("refresh_token", refreshToken);
			request = new HttpEntity<>(map, headers);
			restTemplate.exchange(host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
			org.junit.jupiter.api.Assertions.fail("Expected invalid_grant but refresh token exchange succeeded");
		} catch (HttpStatusCodeException e) {
			assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(e.getResponseBodyAsString()).contains("invalid_grant");
		} finally {
			authorizationService.remove(authorization);
		}
	}

	/**
	 * A refresh against a session whose authorization row has been deleted (cleanup job, logout)
	 * must be rejected with invalid_grant, not fail with a 500.
	 */
	@Test
	public void refreshToken_afterSessionDeleted_returnsInvalidGrant() throws IOException, URISyntaxException {
		// 1 - Authorize user via full browser flow
		String host = "http://localhost:" + serverPort;
		driver.get(host + AUTHORIZATION_CODE_REQUEST);
		assertLoginPage();
		signIn("user1", "pwd");
		assertRedirectPage();

		// 2 - Exchange authorization code for tokens
		String code = getQueryStringParam(driver.getCurrentUrl(), "code");
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("auth-code-flow", "test2");
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "authorization_code");
		map.add("code", code);
		map.add("redirect_uri", REDIRECT_URI);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		RestTemplate restTemplate = new RestTemplate();
		registerGraphQLMock();

		ResponseEntity<TokenResponse> response = restTemplate.exchange(
				host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse = response.getBody();
		assertThat(tokenResponse).isNotNull();
		String refreshToken = tokenResponse.getRefresh_token();
		assertThat(refreshToken).isNotNull();

		// 3 - Delete the authorization record
		OAuth2Authorization authorization = authorizationService.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);
		assertThat(authorization).isNotNull();
		jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE id = ?", authorization.getId());

		// 4 - Attempt to use the refresh token; the missing session must block the grant
		try {
			headers = new HttpHeaders();
			headers.setBasicAuth("auth-code-flow", "test2");
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			map = new LinkedMultiValueMap<>();
			map.add("grant_type", "refresh_token");
			map.add("refresh_token", refreshToken);
			request = new HttpEntity<>(map, headers);
			restTemplate.exchange(host + "/auth/oauth2/token", org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
			org.junit.jupiter.api.Assertions.fail("Expected invalid_grant but refresh token exchange succeeded");
		} catch (HttpStatusCodeException e) {
			assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(e.getResponseBodyAsString()).contains("invalid_grant");
		}
	}

	private void registerGraphQLMock() throws IOException {
		wiremock.stubFor(post("/api/graphql")
				.withRequestBody(containing("getMemberByExternalId"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withJsonBody(mockClaims())));
	}

	private void registerGraphQLClientMock() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		wiremock.stubFor(post("/api/graphql")
				.withRequestBody(containing("getAuthClient"))
				.willReturn(aResponse()
						.withHeader("Content-Type", "application/json")
						.withJsonBody(mapper.readTree("""
								{ "data": { "getAuthClient": {
									"clientId": "client-credentials-flow",
									"clientVersion": 1,
									"teams": []
								}}}
								"""))));
	}

	/**
	 * Authorization Code Flow with PKCE using wildcard redirect url
	 *	1 - Create code verifier: Generate a code_verifier that will be sent to auth server to request tokens.
	 *	2 - Create code challenge: Generate a code_challenge from the code_verifier that will be sent to auth server to request an authorization_code.
	 *	3 - Authorize user: Request the user's authorization (via mock OIDC substitute for Azure) and redirect back to your app with an authorization_code
	 */
	@Test
	public void whenNotLoggedInAndRequestingAuthCodePkceWildcardRedirectThenSucceed() throws IOException, NoSuchAlgorithmException {
		// 1 - Create code verifier
		String verifier = getCodeVerifier();

		// 2 - Create code challenge
		String challenge = getCodeChallenge(verifier);

		// 3 - Authorize user
		String host = "http://localhost:" + serverPort;
		// verify redirect url is matched against registered client wildcard url
		driver.get(host + AUTHORIZATION_CODE_PKCE_REQUEST.replace(REDIRECT_URI, WILDCARD_REDIRECT_URI).replace("--CHALLENGE--", challenge));
		assertLoginPage();
		signIn("user1", "pwd");
		assertRedirectPage();
		assertThat(driver.getCurrentUrl()).contains(WILDCARD_REDIRECT_URI);
	}

	/**
	 * Client Credentials Flow
	 *  1 - Request token: From the authorized application, request an access token.
	*/
	@Test
	public void whenRequestingClientCredentialsTokenThenSucceed() throws URISyntaxException, IOException {
		// mock web response for app reg API service claims fetch
		registerGraphQLClientMock();

		// 1 - Request tokens
		URI url = new URI("http://localhost:" + serverPort + "/auth/oauth2/token");
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("client-credentials-flow", "test3");
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "client_credentials");
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<TokenResponse> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		TokenResponse tokenResponse = response.getBody();

		assertThat(tokenResponse).isNotNull();
		assertThat(tokenResponse.getId_token()).isNull();
		assertThat(tokenResponse.getAccess_token()).isNotNull();
		assertThat(tokenResponse.getToken_type()).isEqualTo("Bearer");
		assertThat(tokenResponse.getRefresh_token()).isNull();
		assertThat(tokenResponse.getError()).isNull();
	}

	@Test
	public void whenRequestingClientCredentialsTokenWithInvalidClientThenError() throws URISyntaxException {
		URI url = new URI("http://localhost:" + serverPort + "/auth/oauth2/token");

		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth("bad-client", "bad");
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("grant_type", "client_credentials");

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

		RestTemplate restTemplate = new RestTemplate();

		try {
			ResponseEntity<TokenResponse> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, request, TokenResponse.class);
		} catch(HttpStatusCodeException e) {
			ResponseEntity<String> response = ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders())
					.body(e.getResponseBodyAsString());
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		}
	}

	private void assertLoginPage() {
		assertThat(driver.getCurrentUrl().toString()).contains("/Account/Login");

		WebElement usernameInput = driver.findElement(By.cssSelector("input[name=\"Input.Username\"]"));
		WebElement passwordInput = driver.findElement(By.cssSelector("input[name=\"Input.Password\"]"));
		WebElement signInButton = driver.findElement(By.cssSelector("button[name=\"Input.Button\"]"));

		assertThat(usernameInput).isNotNull();
		assertThat(passwordInput).isNotNull();
		assertThat(signInButton.getText()).isEqualTo("Login");
	}

	private void signIn(String username, String password) {
		WebElement usernameInput = driver.findElement(By.cssSelector("input[name=\"Input.Username\"]"));
		WebElement passwordInput = driver.findElement(By.cssSelector("input[name=\"Input.Password\"]"));
		WebElement signInButton = driver.findElement(By.cssSelector("button[name=\"Input.Button\"]"));

		usernameInput.sendKeys(username);
		passwordInput.sendKeys(password);
		signInButton.click();
	}

	private void assertRedirectPage() {
		try {
			Thread.sleep(2000);	// let login do its thing first, before waiting for redirect uri
		} catch (InterruptedException ignored) {}
		Wait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(5));
		wait.until(d -> d.getCurrentUrl()).contains(REDIRECT_URI_PREFIX);
		assertThat(driver.getCurrentUrl()).contains(REDIRECT_URI_PREFIX);
	}

	private String getCodeVerifier() {
		SecureRandom sr = new SecureRandom();
		byte[] code = new byte[32];
		sr.nextBytes(code);
		return Base64.encodeBase64URLSafeString(code);
	}

	private String getCodeChallenge(String verifier) throws UnsupportedEncodingException, NoSuchAlgorithmException {
		byte[] bytes = verifier.getBytes("US-ASCII");
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(bytes, 0, bytes.length);
		byte[] digest = md.digest();
		return Base64.encodeBase64URLSafeString(digest);
	}

	private String mockGraphQLData =
			"""
            { "data": { "getMemberByExternalId": {
                "name": "Test User",
                "firstName": "Test",
                "lastName": "User",
                "externalId": "12345-12345-12345",
                "externalAlias": "12345", 				
                "teams": [
                { 
                    "name": "Personal-12345", 
                    "type": "PERSONAL", 
                    "role": "OWNER", 
                    "applications": [{ 
                        "name": "app",
                        "informationClassification": "A_LEVEL_ADDED_LATER"
                    }]
                },
                { 
                    "name": "Shared-1", 
                    "type": "SHARED", 
                    "role": "MEMBER", 
                    "applications": [{ 
                        "name": "app2",
                        "complianceTypes": ["pci"],
                        "informationClassification": "RESTRICTED"
                    }]
                }]
            }}}
            """;

	private wiremock.com.fasterxml.jackson.databind.JsonNode jwtPayload(String jwt) throws IOException {
		return new ObjectMapper().readTree(Base64.decodeBase64(jwt.split("\\.")[1]));
	}

	private wiremock.com.fasterxml.jackson.databind.JsonNode mockClaims() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readTree(mockGraphQLData);
	}

	private String getQueryStringParam(String url, String name) throws URISyntaxException {
		List<NameValuePair> params = URLEncodedUtils.parse(new URI(url), StandardCharsets.UTF_8);
		return params.stream().filter(p -> p.getName().equalsIgnoreCase((name))).findFirst().get().getValue();
	}

}
