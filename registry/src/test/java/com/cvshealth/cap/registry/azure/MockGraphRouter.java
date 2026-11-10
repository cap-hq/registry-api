// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.azure;

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
import java.util.List;
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
    private static final Pattern STARTS_WITH = Pattern.compile("startswith\\(displayName,\\s*'([^']*)'\\)");
    private static final int DEFAULT_TOP = 100;

    /** The {@code $filter} clauses the services send, each joined with {@code and}. */
    private static final Map<String, Predicate<ObjectNode>> FILTER_CLAUSES = Map.of(
            "mail ne null", user -> user.hasNonNull("mail"),
            "displayName ne null", user -> user.hasNonNull("displayName"),
            "userType eq 'Member'", user -> "Member".equals(user.path("userType").asText()),
            "accountEnabled eq true", user -> user.path("accountEnabled").asBoolean());

    private static final Map<String, ObjectNode> USERS_BY_ID = new LinkedHashMap<>();
    private static final Map<String, String> MANAGER_ID_BY_USER_ID = new HashMap<>();
    private static final JsonNode APPLICATIONS = readFixture("/graph/applications.json").get("value");

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
        if ("POST".equals(method) && path.equals("/users/getByIds")) {
            return getByIds(body);
        }
        if ("GET".equals(method) && path.equals("/users")) {
            return searchUsers(query);
        }
        if ("GET".equals(method) && path.equals("/applications")) {
            return applications(query);
        }
        if ("GET".equals(method) && path.startsWith("/users/")) {
            String[] segments = path.substring("/users/".length()).split("/", 2);
            String idOrMail = URLDecoder.decode(segments[0], StandardCharsets.UTF_8);
            String relation = segments.length > 1 ? segments[1] : "";
            return switch (relation) {
                case "" -> user(idOrMail, query);
                case "manager" -> manager(idOrMail);
                case "directReports" -> directReports(idOrMail);
                case "memberOf" -> memberOf(idOrMail);
                default -> notMocked(method, url);
            };
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

    private static MockResponse getByIds(String body) {
        List<String> ids = MAPPER.convertValue(parse(body).path("ids"), MAPPER.getTypeFactory()
                .constructCollectionType(List.class, String.class));
        return ok(collection(USERS_BY_ID.values().stream().filter(user -> ids.contains(user.get("id").asText())).toList()));
    }

    /**
     * Emulates {@code $search="field:value" OR "field:value"} and the {@code $filter} clauses the services send.
     *
     * <p>Search is a case-insensitive "contains"; real Graph tokenizes and prefix-matches, so keep fixture names
     * distinct enough that the difference does not matter. Any filter clause not in {@link #FILTER_CLAUSES} returns
     * 501, so a production filter change cannot be silently ignored by the mock.
     */
    private static MockResponse searchUsers(Map<String, String> query) {
        String search = query.getOrDefault("$search", "");
        String filter = query.getOrDefault("$filter", "");
        int top = Integer.parseInt(query.getOrDefault("$top", String.valueOf(DEFAULT_TOP)));

        Predicate<ObjectNode> matchesFilter = user -> true;
        for (String clause : filter.isBlank() ? new String[0] : filter.split(" and ")) {
            Predicate<ObjectNode> predicate = FILTER_CLAUSES.get(clause.trim());
            if (predicate == null) {
                return notMocked("GET", "/users with $filter clause [" + clause.trim() + "]");
            }
            matchesFilter = matchesFilter.and(predicate);
        }

        return ok(collection(USERS_BY_ID.values().stream()
                .filter(matchesFilter)
                .filter(user -> matchesSearch(user, search))
                .sorted(Comparator.comparing((ObjectNode user) -> user.path("displayName").asText()))
                .limit(top)
                .toList()));
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

    private static MockResponse user(String idOrMail, Map<String, String> query) {
        Optional<ObjectNode> user = findUser(idOrMail);
        if (user.isEmpty()) {
            return notFound(idOrMail);
        }
        ObjectNode result = user.get().deepCopy();
        if (query.getOrDefault("$expand", "").startsWith("manager")) {
            Optional.ofNullable(MANAGER_ID_BY_USER_ID.get(result.get("id").asText()))
                    .map(USERS_BY_ID::get)
                    .ifPresent(manager -> result.set("manager", manager.deepCopy()));
        }
        return ok(result);
    }

    /** Like Graph, a user without a manager has no manager resource (404). */
    private static MockResponse manager(String idOrMail) {
        return findUser(idOrMail)
                .map(user -> MANAGER_ID_BY_USER_ID.get(user.get("id").asText()))
                .map(USERS_BY_ID::get)
                .map(manager -> ok(manager.deepCopy()))
                .orElseGet(() -> notFound(idOrMail));
    }

    private static MockResponse directReports(String idOrMail) {
        return findUser(idOrMail)
                .map(manager -> ok(collection(USERS_BY_ID.values().stream()
                        .filter(user -> manager.get("id").asText().equals(MANAGER_ID_BY_USER_ID.get(user.get("id").asText())))
                        .toList())))
                .orElseGet(() -> notFound(idOrMail));
    }

    /** The fixture users belong to no groups. */
    private static MockResponse memberOf(String idOrMail) {
        return findUser(idOrMail).map(user -> ok(collection(List.of()))).orElseGet(() -> notFound(idOrMail));
    }

    private static MockResponse applications(Map<String, String> query) {
        Matcher startsWith = STARTS_WITH.matcher(query.getOrDefault("$filter", ""));
        String prefix = startsWith.find() ? startsWith.group(1) : "";
        ArrayNode matches = MAPPER.createArrayNode();
        for (JsonNode application : APPLICATIONS) {
            if (application.path("displayName").asText().startsWith(prefix)) {
                matches.add(application.deepCopy());
            }
        }
        ObjectNode result = MAPPER.createObjectNode();
        result.set("value", matches);
        return ok(result);
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

    private static ObjectNode collection(List<ObjectNode> users) {
        ArrayNode value = MAPPER.createArrayNode();
        users.forEach(user -> value.add(user.deepCopy()));
        ObjectNode result = MAPPER.createObjectNode();
        result.set("value", value);
        return result;
    }

    private static MockResponse ok(JsonNode body) {
        return new MockResponse(200, body);
    }

    private static MockResponse notFound(String resource) {
        return error(404, "Request_ResourceNotFound", "Resource '" + resource + "' does not exist in the mock directory");
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
