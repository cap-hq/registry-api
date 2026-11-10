// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.azure;

import wiremock.com.fasterxml.jackson.databind.JsonNode;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;
import wiremock.com.fasterxml.jackson.databind.node.ArrayNode;
import wiremock.com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal in-memory stand-in for Microsoft Graph, answering only the endpoints the integration tests use from the
 * synthetic fixtures in {@code src/test/resources/graph}.
 *
 * <p>Anything not covered returns 501 so that a missing fixture fails the test loudly rather than looking like a
 * "user not found" (404), which the services treat as a normal outcome.
 *
 * <p>To cover a new scenario, add a user to {@code graph/users.json} (optionally with a {@code managerId}) or a
 * route below.
 */
final class MockGraphRouter {

    record MockResponse(int status, JsonNode body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SEARCH_CLAUSE = Pattern.compile("\"([^\":]+):([^\"]*)\"");
    private static final Pattern EMPLOYEE_ID_FILTER = Pattern.compile("employeeId eq '([^']*)'");
    private static final int DEFAULT_TOP = 100;

    /** The fixed {@code $filter} clauses the services send, each joined with {@code and}. */
    private static final Map<String, Predicate<ObjectNode>> FILTER_CLAUSES = Map.of(
            "mail ne null", user -> user.hasNonNull("mail"),
            "displayName ne null", user -> user.hasNonNull("displayName"),
            "userType eq 'Member'", user -> "Member".equals(user.path("userType").asText()),
            "accountEnabled eq true", user -> user.path("accountEnabled").asBoolean());

    private static final Map<String, ObjectNode> USERS_BY_ID = new LinkedHashMap<>();
    private static final Map<String, String> MANAGER_ID_BY_USER_ID = new HashMap<>();

    static {
        for (JsonNode entry : readFixture("/graph/users.json")) {
            ObjectNode user = (ObjectNode) entry.get("user");
            String id = user.get("id").asText();
            USERS_BY_ID.put(id, user);
            if (entry.hasNonNull("managerId")) {
                MANAGER_ID_BY_USER_ID.put(id, entry.get("managerId").asText());
            }
        }
    }

    private MockGraphRouter() {
    }

    static MockResponse handle(String method, String rawUrl, String body) {
        String url = rawUrl.replaceFirst("^https?://[^/]+", "");
        if (url.startsWith("/v1.0/")) {
            url = url.substring("/v1.0".length());
        }
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        int queryStart = url.indexOf('?');
        String path = queryStart < 0 ? url : url.substring(0, queryStart);
        Map<String, String> query = parseQuery(queryStart < 0 ? "" : url.substring(queryStart + 1));

        if ("POST".equals(method) && path.equals("/$batch")) {
            return batch(body);
        }
        if ("GET".equals(method) && path.equals("/users")) {
            return searchUsers(query);
        }
        if ("GET".equals(method) && path.startsWith("/users/") && path.indexOf('/', "/users/".length()) < 0) {
            return user(URLDecoder.decode(path.substring("/users/".length()), StandardCharsets.UTF_8), query);
        }
        return notMocked(method, url);
    }

    /**
     * Graph JSON batching: every sub-request is answered by {@link #handle}, echoing the request id the SDK generated.
     */
    private static MockResponse batch(String body) {
        ArrayNode responses = MAPPER.createArrayNode();
        for (JsonNode request : parse(body).path("requests")) {
            JsonNode requestBody = request.get("body");
            MockResponse response = handle(request.path("method").asText(), request.path("url").asText(),
                    requestBody == null || requestBody.isNull() ? null : requestBody.toString());
            ObjectNode item = responses.addObject();
            item.put("id", request.path("id").asText());
            item.put("status", response.status());
            item.putObject("headers").put("Content-Type", "application/json");
            item.set("body", response.body());
        }
        ObjectNode result = MAPPER.createObjectNode();
        result.set("responses", responses);
        return ok(result);
    }

    /**
     * Emulates {@code $search="field:value" OR "field:value"} and the {@code $filter} clauses the services send.
     *
     * <p>Search is a case-insensitive "contains"; real Graph tokenizes and prefix-matches, so keep fixture names
     * distinct enough that the difference does not matter. Any filter clause not in {@link #FILTER_CLAUSES} (or an
     * {@code employeeId eq 'x'} clause) returns 501, so a production filter change cannot be silently ignored.
     */
    private static MockResponse searchUsers(Map<String, String> query) {
        String search = query.getOrDefault("$search", "");
        String filter = query.getOrDefault("$filter", "");
        int top = Integer.parseInt(query.getOrDefault("$top", String.valueOf(DEFAULT_TOP)));

        Predicate<ObjectNode> matchesFilter = user -> true;
        for (String rawClause : filter.isBlank() ? new String[0] : filter.split(" and ")) {
            String clause = rawClause.trim();
            Predicate<ObjectNode> predicate = FILTER_CLAUSES.get(clause);
            Matcher employeeId = EMPLOYEE_ID_FILTER.matcher(clause);
            if (predicate == null && employeeId.matches()) {
                String requiredEmployeeId = employeeId.group(1);
                predicate = user -> requiredEmployeeId.equals(user.path("employeeId").asText(null));
            }
            if (predicate == null) {
                return notMocked("GET", "/users with $filter clause [" + clause + "]");
            }
            matchesFilter = matchesFilter.and(predicate);
        }

        ArrayNode value = MAPPER.createArrayNode();
        USERS_BY_ID.values().stream()
                .filter(matchesFilter)
                .filter(user -> matchesSearch(user, search))
                .sorted(Comparator.comparing((ObjectNode user) -> user.path("displayName").asText()))
                .limit(top)
                .forEach(user -> value.add(user.deepCopy()));
        ObjectNode result = MAPPER.createObjectNode();
        result.set("value", value);
        return ok(result);
    }

    private static boolean matchesSearch(ObjectNode user, String search) {
        Matcher clause = SEARCH_CLAUSE.matcher(search);
        boolean anyClause = false;
        while (clause.find()) {
            anyClause = true;
            String field = clause.group(1);
            String value = clause.group(2).toLowerCase();
            if (user.path(field).asText().toLowerCase().contains(value)) {
                return true;
            }
        }
        return !anyClause;
    }

    /**
     * With {@code $expand=manager($levels=max)} Graph nests the whole management chain, so do the same.
     */
    private static MockResponse user(String idOrMail, Map<String, String> query) {
        Optional<ObjectNode> user = findUser(idOrMail);
        if (user.isEmpty()) {
            return error(404, "Request_ResourceNotFound", "Resource '" + idOrMail + "' does not exist in the mock directory");
        }
        boolean expandManager = query.getOrDefault("$expand", "").startsWith("manager");
        return ok(withManager(user.get(), expandManager));
    }

    private static ObjectNode withManager(ObjectNode user, boolean expandManager) {
        ObjectNode result = user.deepCopy();
        if (expandManager) {
            Optional.ofNullable(MANAGER_ID_BY_USER_ID.get(user.get("id").asText()))
                    .map(USERS_BY_ID::get)
                    .ifPresent(manager -> result.set("manager", withManager(manager, true)));
        }
        return result;
    }

    private static Optional<ObjectNode> findUser(String idOrMail) {
        ObjectNode byId = USERS_BY_ID.get(idOrMail);
        if (byId != null) {
            return Optional.of(byId);
        }
        return USERS_BY_ID.values().stream()
                .filter(user -> idOrMail.equalsIgnoreCase(user.path("mail").asText(null)))
                .findFirst();
    }

    private static MockResponse ok(JsonNode body) {
        return new MockResponse(200, body);
    }

    private static MockResponse notMocked(String method, String url) {
        return error(501, "NotMockedInTests", "No Graph fixture for " + method + " " + url
                + " - add a route to MockGraphRouter or a user to graph/users.json");
    }

    private static MockResponse error(int status, String code, String message) {
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("error").put("code", code).put("message", message);
        return new MockResponse(status, body);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery.isEmpty()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String name = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            query.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return query;
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid JSON in mock Graph request", e);
        }
    }

    private static JsonNode readFixture(String resource) {
        try (InputStream in = MockGraphRouter.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture " + resource);
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read test fixture " + resource, e);
        }
    }
}
