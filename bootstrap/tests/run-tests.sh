#!/usr/bin/env bash
# SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
# SPDX-License-Identifier: Apache-2.0
#
# Tests for bootstrap.sh. Pure bash; no test framework required.
#
# Usage: tests/run-tests.sh [path-to-bash]
#   Runs the script under the given bash (default: the bash running this file),
#   so it can be exercised under macOS /bin/bash 3.2 and newer releases.
#
# curl, oauth2c, and uname are replaced by stubs on an isolated PATH; nothing
# contacts a real server and no browser is opened. jq is real (optionally
# wrapped to fake its --version).

set -u

TESTS_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$(cd "$TESTS_DIR/.." && pwd)/bootstrap.sh"
TEST_BASH="${1:-$BASH}"

REAL_JQ="$(command -v jq || true)"
if [ -z "$REAL_JQ" ]; then
    echo "jq is required to run these tests." >&2
    exit 2
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/bootstrap-tests.XXXXXX")"
LISTENER_PID=""
cleanup() {
    if [ -n "$LISTENER_PID" ]; then
        kill "$LISTENER_PID" 2>/dev/null || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

LOGS="$WORK/logs"
SECRET_VALUE="sentinel-SECRET-7f3a9c"
SECRET_FILE="$WORK/api-secret"
printf '%s\n' "$SECRET_VALUE" >"$SECRET_FILE"
chmod 600 "$SECRET_FILE"

PASS=0
FAIL=0
FAILED_NAMES=""

# Sign-in fixtures. The token's signature segment is the leak sentinel; the
# payload carries the operator's externalId that Registry must echo back.
TOKEN_SENTINEL="sentinelTOKEN5e1f7b"
EXTERNAL_ID="EXT-123456"
b64url() { printf '%s' "$1" | base64 | tr -d '\n=' | tr '+/' '-_'; }
JWT_HEADER="$(b64url '{"alg":"RS256","typ":"JWT"}')"
TOKEN_OK="$JWT_HEADER.$(b64url "{\"sub\":\"jdoe\",\"externalId\":\"$EXTERNAL_ID\",\"scope\":\"openid email\"}").$TOKEN_SENTINEL"
TOKEN_NO_EXTERNAL_ID="$JWT_HEADER.$(b64url '{"sub":"svc-client","scope":"openid"}').$TOKEN_SENTINEL"
MEMBER_OK="{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"$EXTERNAL_ID\",\"externalAlias\":\"jdoe\",\"email\":\"jane.doe@example.org\",\"name\":\"Jane Doe\"}}}"

ISSUER="https://auth.example.org/auth"
YES="y"   # answer at the Proceed? [y/N] confirmation
REGISTRY="https://registry.example.org/api/graphql"
DISCOVERY_OK='{"issuer":"https://auth.example.org/auth","authorization_endpoint":"https://auth.example.org/auth/oauth2/authorize","token_endpoint":"https://auth.example.org/auth/oauth2/token","code_challenge_methods_supported":["S256"]}'

# --------------------------------------------------------------------------
# Fixtures
# --------------------------------------------------------------------------

free_port() {
    python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()'
}

# make_bin TOOL... -> creates an isolated bin dir containing only the listed
# tools plus the system utilities the script relies on, and prints its path.
make_bin() {
    local dir tool util
    dir="$(mktemp -d "$WORK/bin.XXXXXX")"
    for util in cat ls tr date mktemp rm; do
        ln -s "$(command -v "$util")" "$dir/$util"
    done
    for tool in "$@"; do
        case "$tool" in
            jq) ln -s "$REAL_JQ" "$dir/jq" ;;
            jqstub) write_jq_stub "$dir/jq"; chmod +x "$dir/jq" ;;
            curl) write_curl_stub "$dir/curl" ;;
            oauth2c) write_oauth2c_stub "$dir/oauth2c" ;;
            uname) printf '#!%s\nprintf "%%s\\n" "${STUB_UNAME:-Darwin}"\n' "$TEST_BASH" >"$dir/uname" ;;
        esac
        chmod +x "$dir/$tool" 2>/dev/null || true
    done
    printf '%s' "$dir"
}

# The oauth2c stub answers "version" like a release build (on stderr, as cobra
# does) with STUB_OAUTH2C_VERSION and exit code STUB_OAUTH2C_EXIT. Any other
# invocation is a sign-in: argv and environment are logged under
# $STUB_LOG_DIR, the authorize URL goes to stderr (as --silent mode does), and
# the outcome follows STUB_OAUTH2C_MODE, replaying what oauth2c 1.21.1 was
# observed to print (ANSI-coloured "  ERROR   <text>" on stderr, exit 1):
#   ok (default)  token JSON on stdout: $STUB_LOG_DIR/token, STUB_OAUTH2C_EXPIRES
#   denied | interrupted | timeout | tokenerr | unreachable
#   malformed     non-JSON stdout, exit 0
#   empty         nothing on stdout, exit 0
#   ctrl          success, but the URL line carries terminal control sequences
write_oauth2c_stub() {
    printf '#!%s\n' "$TEST_BASH" >"$1"
    cat >>"$1" <<'EOF'
if [ "${1:-}" = "version" ]; then
    printf '%s\n' "${STUB_OAUTH2C_VERSION-oauth2c version 1.21.1 (commit abc, built at 2026-01-01)}" >&2
    exit "${STUB_OAUTH2C_EXIT:-0}"
fi
log="${STUB_LOG_DIR:-/tmp}"
printf '%s\n' "$*" >>"$log/oauth2c_argv.log"
export -p >>"$log/oauth2c_env.log"
issuer="${1:-}"
err() { printf '\033[31m  ERROR  \033[0m \033[1m%s\033[0m\n' "$1" >&2; exit 1; }
case "${STUB_OAUTH2C_MODE:-ok}" in
    ctrl) printf '%s/oauth2/authorize?client_id=x\033[31m&state=\a\033]0;owned\007y\r\n' "$issuer" >&2 ;;
    *) printf '%s/oauth2/authorize?response_type=code&client_id=x&code_challenge=y\n' "$issuer" >&2 ;;
esac
case "${STUB_OAUTH2C_MODE:-ok}" in
    denied) err "access_denied" ;;
    interrupted) err "Interrupted" ;;
    timeout) err "timeout" ;;
    tokenerr) err "400: invalid_grant" ;;
    unreachable) err "Get \"$issuer/.well-known/openid-configuration\": dial tcp 127.0.0.1:8200: connect: connection refused" ;;
    malformed) printf 'not json\n'; exit 0 ;;
    empty) exit 0 ;;
esac
token="$(cat "$log/token")"
printf '{"access_token":"%s","refresh_token":"rt-%s","id_token":"%s","expires_in":%s,"scope":"openid email","token_type":"Bearer"}\n' \
    "$token" "$token" "$token" "${STUB_OAUTH2C_EXPIRES:-3600}"
EOF
}

# The jq wrapper fakes `jq --version` (STUB_JQ_VERSION; STUB_JQ_EXIT) and, with
# STUB_JQ_BROKEN=1, answers the @base64d functional probe wrongly. Everything
# else is passed to the real jq.
write_jq_stub() {
    printf '#!%s\nREAL_JQ=%q\n' "$TEST_BASH" "$REAL_JQ" >"$1"
    cat >>"$1" <<'EOF'
if [ "${1:-}" = "--version" ]; then
    printf '%s\n' "${STUB_JQ_VERSION-jq-1.7.1}"
    exit "${STUB_JQ_EXIT:-0}"
fi
if [ "${STUB_JQ_BROKEN:-0}" = "1" ]; then
    case " $* " in *"@base64d"*) echo "y"; exit 0 ;; esac
fi
exec "$REAL_JQ" "$@"
EOF
}

# os_release CONTENT -> writes an os-release fixture and prints its path.
os_release() {
    local file
    file="$(mktemp "$WORK/os-release.XXXXXX")"
    printf '%s\n' "$1" >"$file"
    printf '%s' "$file"
}

# The curl stub logs argv, environment, the -H @file header contents, and the
# request body under $STUB_LOG_DIR, then answers by method and URL:
#   GET  *openid-configuration -> STUB_DISCOVERY_STATUS / STUB_DISCOVERY_BODY
#   GET  */v1/client/<id>      -> STUB_AUTH_GET_STATUS / STUB_AUTH_GET_BODY
#                                 (default: 200 with a compatible seeded
#                                 USER_PKCE client listing $STUB_REG_REDIRECT;
#                                 2nd and later calls: STUB_AUTH_GET2_*, if set)
#   POST */v1/client           -> never expected; answers 500 and is counted
#   POST getMemberByExternalId -> STUB_MEMBER_STATUS / $STUB_LOG_DIR/member
#                                 (Registry member lookup, needs the bearer)
#   POST getTeamByName, createTeam, getAuthClient, createAuthClient
#                              -> stateful fake Registry (see registry_op)
#   anything else              -> STUB_REGISTRY_STATUS / STUB_REGISTRY_BODY
# Auth routes answer 403 ACCESS_DENIED unless the X-Api-Secret header equals
# $STUB_LOG_DIR/expected_secret (kept out of the environment so env leak
# checks stay meaningful). STUB_CURL_FAIL=1 fails every call (transport).
write_curl_stub() {
    printf '#!%s\n' "$TEST_BASH" >"$1"
    cat >>"$1" <<'EOF'
url="" method="GET" headers="" body="" prev=""
for arg in "$@"; do
    case "$prev" in
        --) url="$arg" ;;
        --request) method="$arg" ;;
        --header) case "$arg" in @*) headers="$(cat "${arg#@}")" ;; esac ;;
    esac
    prev="$arg"
done
case " $* " in
    *" @- "*) body="$(cat)" ;;
esac
log="${STUB_LOG_DIR:-/tmp}"
printf '%s\n' "$*" >>"$log/argv.log"
export -p >>"$log/env.log"
printf '%s\n' "$headers" >>"$log/headers.log"
printf '%s %s %s\n' "$method" "$url" "$body" >>"$log/requests.log"
reply() { printf '%s\n%s' "$2" "$1"; exit 0; }
fail() { echo "curl: (28) Operation timed out" >&2; printf '\n000'; exit 28; }
if [ "${STUB_CURL_FAIL:-0}" = "1" ]; then
    echo "curl: (7) Failed to connect" >&2
    printf '\n000'
    exit 7
fi
denied='{"status":403,"detail":"Access denied","reason":"ACCESS_DENIED"}'
expected="$(cat "$log/expected_secret" 2>/dev/null)"
nl=$'\n'
secret_ok() {
    case "$nl$headers$nl" in *"${nl}X-Api-Secret: $expected$nl"*) return 0 ;; esac
    return 1
}

# Stateful fake Registry for the team and association stages, modelled on the
# real resolvers: createTeam makes the caller (externalId from the bearer
# token) OWNER; getAuthClient without a version returns the latest version;
# repeating createAuthClient for the same team creates the next version.
# State lives in $STUB_REG_STATE_DIR (default: the log dir) so a rerun can
# share it, and starts from STUB_REG_TEAM (owner|member|viewer|outsider|
# personal) and STUB_REG_CLIENT (ours|other|v2). A file $STUB_GQL_DIR/<op>.<n>
# scripts the n-th call of an operation: first line "fail" (transport failure,
# nothing changes), "lost" (the change happens, the response is lost) or
# "reply STATUS" (the rest of the file is the body; nothing changes).
st="${STUB_REG_STATE_DIR:-$log}"
st_get() { cat "$st/reg_$1" 2>/dev/null; }
if [ ! -f "$st/reg_init" ]; then
    : >"$st/reg_init"
    tname="${STUB_REG_TEAM_NAME:-test-team}"
    case "${STUB_REG_TEAM-}" in
        owner|member|viewer|outsider|personal)
            printf '%s' "$tname" >"$st/reg_team"
            case "$STUB_REG_TEAM" in
                member) printf MEMBER ;;
                viewer) printf VIEWER ;;
                outsider) printf none ;;
                *) printf OWNER ;;
            esac >"$st/reg_team_role"
            if [ "$STUB_REG_TEAM" = personal ]; then printf PERSONAL; else printf SHARED; fi >"$st/reg_team_type"
            ;;
    esac
    case "${STUB_REG_CLIENT-}" in
        ours|other|v2)
            printf '%s' "${STUB_REG_CLIENT_ID:-bootstrap-test}" >"$st/reg_client"
            if [ "$STUB_REG_CLIENT" = other ]; then printf '%s' "${STUB_REG_OTHER_TEAM:-someone-elses-team}"; else printf '%s' "$tname"; fi >"$st/reg_client_team"
            if [ "$STUB_REG_CLIENT" = v2 ]; then printf 2; else printf 1; fi >"$st/reg_client_version"
            jq -nc --arg r "${STUB_REG_REDIRECT-}" '[$r]' >"$st/reg_client_redirects"
            ;;
    esac
fi
# field NAME -> the string value of NAME: \"VALUE\" in the GraphQL request body
field() {
    local re="$1"': \\"([^\\"]*)\\"'
    [[ $body =~ $re ]] && printf '%s' "${BASH_REMATCH[1]}"
}
operator_id() {
    local tok="${headers#*Authorization: Bearer }"
    tok="${tok%%$nl*}"
    printf '%s' "$tok" | jq -Rr 'split(".")[1] | gsub("-"; "+") | gsub("_"; "/")
        | if length % 4 == 2 then . + "==" elif length % 4 == 3 then . + "=" else . end
        | @base64d | fromjson | .externalId // ""' 2>/dev/null
}
gql_error() {
    printf '{"errors":[{"message":"%s","extensions":{"reason":"%s","status":%s}}],"data":null}' "$1" "$1" "${2:-400}"
}
lc() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }
fake_getTeamByName() {
    if [ ! -f "$st/reg_team" ] || [ "$(lc "$(st_get team)")" != "$(lc "$(field name)")" ]; then gql_error TEAM_NOT_FOUND 404; return; fi
    jq -nc --arg name "$(st_get team)" --arg type "$(st_get team_type)" --arg role "$(st_get team_role)" \
        --arg op "$(operator_id)" --arg cid "$(st_get client)" --arg cteam "$(st_get client_team)" \
        --arg ver "$(st_get client_version)" '
        {data: {getTeamByName: {name: $name, type: $type,
            members: ([{externalId: "EXT-OTHER", role: "OWNER"}]
                + (if $role == "none" then [] else [{externalId: $op, role: $role}] end)),
            authClients: (if $cid != "" and $cteam == $name then [{clientId: $cid, clientVersion: ($ver | tonumber)}] else [] end)}}}'
}
fake_createTeam() {
    if [ -f "$st/reg_team" ]; then gql_error TEAM_DUPLICATE_FOUND; return; fi
    printf '%s' "$(field name)" >"$st/reg_team"
    printf OWNER >"$st/reg_team_role"
    printf SHARED >"$st/reg_team_type"
    jq -nc --arg n "$(st_get team)" '{data: {createTeam: {name: $n}}}'
}
fake_getAuthClient() {
    if [ ! -f "$st/reg_client" ] || [ "$(st_get client)" != "$(field clientId)" ]; then gql_error AUTH_CLIENT_NOT_FOUND 404; return; fi
    jq -nc --arg c "$(st_get client)" --arg t "$(st_get client_team)" --arg v "$(st_get client_version)" \
        '{data: {getAuthClient: {clientId: $c, clientVersion: ($v | tonumber), teams: [{name: $t}]}}}'
}
fake_getAuthClientFull() {
    local redirects
    if [ ! -f "$st/reg_client" ] || [ "$(st_get client)" != "$(field clientId)" ]; then gql_error AUTH_CLIENT_NOT_FOUND 404; return; fi
    redirects="$(st_get client_redirects 2>/dev/null)"; [ -n "$redirects" ] || redirects='[]'
    jq -nc --arg c "$(st_get client)" --arg t "$(st_get client_team)" --argjson r "$redirects" \
        '{data: {getAuthClient: {clientId: $c, clientVersion: 1, type: "USER_PKCE", redirectUrls: $r, teams: [{name: $t}]}}}'
}
fake_createAuthClient() {
    local team redirects re='redirectUrls: (\[[^]]*\])'
    team="$(field teamName)"
    if [ "$(lc "$(st_get team)")" != "$(lc "$team")" ] || [ "$(st_get team_role)" != OWNER ]; then gql_error ACCESS_DENIED 403; return; fi
    if [ -f "$st/reg_client" ]; then
        if [ "$(lc "$(st_get client_team)")" != "$(lc "$team")" ]; then gql_error AUTH_CLIENT_DUPLICATE_FOUND; return; fi
        printf '%s' $(( $(st_get client_version) + 1 )) >"$st/reg_client_version"
    else
        printf '%s' "$(field clientId)" >"$st/reg_client"
        printf '%s' "$team" >"$st/reg_client_team"
        printf 1 >"$st/reg_client_version"
        # The GraphQL query is a JSON string, so the array's quotes are escaped.
        if [[ $body =~ $re ]]; then
            redirects="${BASH_REMATCH[1]}"
            printf '%s' "${redirects//\\\"/\"}" >"$st/reg_client_redirects"
        fi
    fi
    jq -nc --arg c "$(st_get client)" --arg v "$(st_get client_version)" \
        '{data: {createAuthClient: {clientId: $c, clientVersion: ($v | tonumber)}}}'
}
registry_op() {
    local op="$1" n action="real" canned="" out
    n=$(( $(st_get "count_$op" || echo 0) + 1 ))
    printf '%s' "$n" >"$st/reg_count_$op"
    if [ -n "${STUB_GQL_DIR-}" ] && [ -f "$STUB_GQL_DIR/$op.$n" ]; then
        { IFS= read -r action; canned="$(cat)"; } <"$STUB_GQL_DIR/$op.$n"
    fi
    case "$action" in
        fail) fail ;;
        "reply "*) reply "${action#reply }" "$canned" ;;
    esac
    out="$(fake_"$op")"
    if [ "$action" = lost ]; then fail; fi
    reply 200 "$out"
}
case "$method $url" in
    "GET "*openid-configuration)
        reply "${STUB_DISCOVERY_STATUS:-200}" "${STUB_DISCOVERY_BODY-}"
        ;;
    "GET "*/v1/client/*)
        secret_ok || reply 403 "$denied"
        count=$(( $(cat "$log/auth_get_count" 2>/dev/null || echo 0) + 1 ))
        echo "$count" >"$log/auth_get_count"
        [ "${STUB_AUTH_GET_FAIL:-0}" = "1" ] && fail
        if [ "$count" -gt 1 ] && [ -n "${STUB_AUTH_GET2_STATUS-}" ]; then
            reply "$STUB_AUTH_GET2_STATUS" "${STUB_AUTH_GET2_BODY-}"
        fi
        notfound='{"status":404,"detail":"Client record not found","reason":"CLIENT_NOT_FOUND"}'
        if [ "${STUB_AUTH_GET_STATUS-}" = "404" ] && [ -z "${STUB_AUTH_GET_BODY-}" ]; then
            reply 404 "$notfound"
        fi
        seeded="$(jq -nc --arg c "${url##*/}" --arg r "${STUB_REG_REDIRECT-}" \
            '{id: "seed", clientId: $c, type: "USER_PKCE", clientSecret: null, redirectUrls: [$r]}')"
        reply "${STUB_AUTH_GET_STATUS:-200}" "${STUB_AUTH_GET_BODY:-$seeded}"
        ;;
    "POST "*/v1/client)
        secret_ok || reply 403 "$denied"
        reply 500 '{"status":500,"reason":"UNEXPECTED_WRITE"}'
        ;;
    *)
        case "$body" in
            *getMemberByExternalId*)
                reply "${STUB_MEMBER_STATUS:-200}" "$(cat "$log/member" 2>/dev/null)"
                ;;
            *createAuthClient*) registry_op createAuthClient ;;
            *getAuthClient*redirectUrls*) registry_op getAuthClientFull ;;
            *getAuthClient*) registry_op getAuthClient ;;
            *createTeam*) registry_op createTeam ;;
            *getTeamByName*) registry_op getTeamByName ;;
        esac
        reply "${STUB_REGISTRY_STATUS:-401}" "${STUB_REGISTRY_BODY-}"
        ;;
esac
EOF
}

# --------------------------------------------------------------------------
# Runner
# --------------------------------------------------------------------------

OUT=""
CODE=0

# run STDIN BIN_DIR ARGS... -> runs bootstrap.sh; sets OUT (stderr+stdout) and CODE.
run() {
    local input="$1" bin="$2"
    shift 2
    rm -rf "$LOGS"
    mkdir -p "$LOGS"
    printf '%s' "${STUB_EXPECTED_SECRET-$SECRET_VALUE}" >"$LOGS/expected_secret"
    printf '%s' "${STUB_TOKEN-$TOKEN_OK}" >"$LOGS/token"
    printf '%s' "${STUB_MEMBER_BODY-$MEMBER_OK}" >"$LOGS/member"
    OUT="$(printf '%s' "$input" | env -i HOME="$WORK" TMPDIR="$WORK" PATH="$bin" \
        STUB_UNAME="${STUB_UNAME:-Darwin}" \
        STUB_DISCOVERY_STATUS="${STUB_DISCOVERY_STATUS:-200}" \
        STUB_DISCOVERY_BODY="${STUB_DISCOVERY_BODY-$DISCOVERY_OK}" \
        STUB_REGISTRY_STATUS="${STUB_REGISTRY_STATUS:-401}" \
        STUB_REGISTRY_BODY="${STUB_REGISTRY_BODY-}" \
        STUB_CURL_FAIL="${STUB_CURL_FAIL:-0}" \
        STUB_AUTH_GET_STATUS="${STUB_AUTH_GET_STATUS-}" \
        STUB_AUTH_GET_BODY="${STUB_AUTH_GET_BODY-}" \
        STUB_AUTH_GET2_STATUS="${STUB_AUTH_GET2_STATUS-}" \
        STUB_AUTH_GET2_BODY="${STUB_AUTH_GET2_BODY-}" \
        STUB_AUTH_GET_FAIL="${STUB_AUTH_GET_FAIL:-0}" \
        STUB_OAUTH2C_VERSION="${STUB_OAUTH2C_VERSION-oauth2c version 1.21.1 (commit abc, built at 2026-01-01)}" \
        STUB_OAUTH2C_EXIT="${STUB_OAUTH2C_EXIT:-0}" \
        STUB_OAUTH2C_MODE="${STUB_OAUTH2C_MODE:-ok}" \
        STUB_OAUTH2C_EXPIRES="${STUB_OAUTH2C_EXPIRES:-3600}" \
        STUB_MEMBER_STATUS="${STUB_MEMBER_STATUS:-200}" \
        STUB_REG_STATE_DIR="${STUB_REG_STATE_DIR-}" \
        STUB_REG_TEAM="${STUB_REG_TEAM-}" \
        STUB_REG_TEAM_NAME="${STUB_REG_TEAM_NAME-}" \
        STUB_REG_CLIENT="${STUB_REG_CLIENT-}" \
        STUB_REG_CLIENT_ID="${STUB_REG_CLIENT_ID-}" \
        STUB_REG_OTHER_TEAM="${STUB_REG_OTHER_TEAM-}" \
        STUB_REG_REDIRECT="${STUB_REG_REDIRECT-${CALLBACK-}}" \
        STUB_GQL_DIR="${STUB_GQL_DIR-}" \
        STUB_JQ_VERSION="${STUB_JQ_VERSION-jq-1.7.1}" \
        STUB_JQ_EXIT="${STUB_JQ_EXIT:-0}" \
        STUB_JQ_BROKEN="${STUB_JQ_BROKEN:-0}" \
        BOOTSTRAP_OS_RELEASE_FILE="${BOOTSTRAP_OS_RELEASE_FILE:-$WORK/no-os-release}" \
        BOOTSTRAP_PROC_VERSION_FILE="${BOOTSTRAP_PROC_VERSION_FILE:-$WORK/no-proc-version}" \
        BOOTSTRAP_SECRETS_FILE="${BOOTSTRAP_SECRETS_FILE:-$WORK/no-secrets.yml}" \
        ${REGISTRY_API_SECRET+REGISTRY_API_SECRET="$REGISTRY_API_SECRET"} \
        STUB_LOG_DIR="$LOGS" \
        "$TEST_BASH" "$SCRIPT" "$@" 2>&1)"
    CODE=$?
}

record() {
    local name="$1" ok="$2" detail="$3"
    if [ "$ok" = "1" ]; then
        PASS=$((PASS + 1))
        printf 'ok   %s\n' "$name"
    else
        FAIL=$((FAIL + 1))
        FAILED_NAMES="$FAILED_NAMES
  $name"
        printf 'FAIL %s\n     %s\n' "$name" "$detail"
        printf '%s\n' "$OUT" | sed 's/^/     | /'
    fi
}

# expect NAME CODE PATTERN -> asserts last run's exit code and output substring.
expect() {
    local name="$1" want_code="$2" pattern="$3"
    if [ "$CODE" != "$want_code" ]; then
        record "$name" 0 "expected exit $want_code, got $CODE"
    elif [ -n "$pattern" ] && [[ "$OUT" != *"$pattern"* ]]; then
        record "$name" 0 "output missing: $pattern"
    else
        record "$name" 1 ""
    fi
}

expect_absent() {
    local name="$1" pattern="$2"
    if [[ "$OUT" == *"$pattern"* ]]; then
        record "$name" 0 "output unexpectedly contains: $pattern"
    else
        record "$name" 1 ""
    fi
}

# --------------------------------------------------------------------------
# Tests
# --------------------------------------------------------------------------

BIN="$(make_bin curl jq oauth2c uname)"
PORT="$(free_port)"
CALLBACK="http://localhost:$PORT/callback"
URLS=(--auth-issuer "$ISSUER" --registry-url "$REGISTRY" --redirect-url "$CALLBACK")
COMMON=("${URLS[@]}" --api-secret-file "$SECRET_FILE")
IDS=(--client-id bootstrap-test --team-name test-team)
BASE_ARGS=("${COMMON[@]}" "${IDS[@]}")

echo "Running bootstrap.sh tests with $("$TEST_BASH" -c 'echo "bash $BASH_VERSION"')"

# Interface
run "" "$BIN" --help
expect "help exits 0 and documents options" 0 "--api-secret-file PATH"

run "" "$BIN" --bogus
expect "unknown flag rejected" 2 "Unknown argument: --bogus"

run "" "$BIN" --api-secret hunter2
expect "--api-secret rejected" 2 "not accepted as an argument"
expect_absent "--api-secret value not echoed" "hunter2"

run "" "$BIN" --api-secret=hunter2
expect "--api-secret=value rejected" 2 "not accepted as an argument"

run "" "$BIN" --client-id a1 --client-id b2
expect "duplicate flag rejected" 2 "--client-id was given more than once"

run "" "$BIN" --client-id
expect "flag without value rejected" 2 "--client-id requires a value"

run "" "$BIN" --client-id=
expect "empty --flag= rejected" 2 "--client-id requires a value"

# Happy path, flags and defaults
run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "all flags reach confirmation without prompting and exit 0" 0 "Operator verified."
expect_absent "no prompts when all flags given" "Client ID ["

run "

$YES
y
" "$BIN" "${COMMON[@]}"
expect "prompted defaults reach confirmation and exit 0" 0 "Operator verified."
expect "default client id shown" 0 "Client ID:        registry-user-pkce (USER_PKCE)"
expect "default team shown" 0 "Team name:        registry-admin"
expect "scopes fixed" 0 "Scopes:           openid,email"

run "$YES
y
" "$BIN" --auth-issuer="$ISSUER/" --registry-url="$REGISTRY" --redirect-url="$CALLBACK" \
    --client-id=my-client --team-name=Platform_Team --api-secret-file="$SECRET_FILE"
expect "--flag=value form and trailing issuer slash accepted" 0 "Auth issuer:      $ISSUER (discovery verified)"
expect_absent "other: prep table skipped when all flags given" "You will be asked for:"
expect "custom client id used" 0 "Client ID:        my-client"
expect "custom team used" 0 "Team name:        Platform_Team"

# Prompts
run "2
$ISSUER

$REGISTRY


$YES
$SECRET_VALUE
y
" "$BIN" --redirect-url "$CALLBACK"
expect "other: prep table shown before prompts" 0 "Target: other deployment. You will be asked for:"
expect "other: browser endpoint prompt is optional" 0 "Auth browser endpoint [none]:"
expect "prompts accept defaults on blank input" 0 "Client ID [registry-user-pkce]:"
expect "prompted values used" 0 "Registry GraphQL: $REGISTRY"

run "2
$ISSUER
http://localhost:8200/auth
$REGISTRY
prompted-id
prompted-team
$CALLBACK
$YES
$SECRET_VALUE
y
" "$BIN"
expect "prompted overrides used" 0 "Client ID:        prompted-id"
expect "prompted browser endpoint used" 0 "Auth in browser:  http://localhost:8200/auth/oauth2/authorize"
expect "callback prompt shows default" 0 "Callback URL [http://localhost:9876/callback]:"
expect "prompted callback used" 0 "Callback URL:     $CALLBACK"

run "2
" "$BIN"
expect "EOF during prompt cancels" 4 "Input ended before Auth issuer URL"

run "2

" "$BIN"
expect "blank required prompt rejected" 2 "Auth issuer URL is required"

# Cancellation
run "n
" "$BIN" "${BASE_ARGS[@]}"
expect "answering n cancels with no changes" 4 "Cancelled. Nothing was changed."

run "" "$BIN" "${BASE_ARGS[@]}"
expect "EOF at confirmation cancels" 4 "Cancelled. Nothing was changed."

run "
" "$BIN" "${BASE_ARGS[@]}"
expect "blank confirmation defaults to no" 4 "Cancelled"

# Client ID validation
run "" "$BIN" "${COMMON[@]}" --team-name test-team --client-id 1abc
expect "client id must start with a letter" 2 "Client ID must start with a letter"

run "" "$BIN" "${COMMON[@]}" --team-name test-team --client-id has_underscore
expect "client id rejects underscore" 2 "Client ID must start with a letter"

run "" "$BIN" "${COMMON[@]}" --team-name test-team --client-id abcdefghijklmnopqrstuvwxyz12345
expect "client id over 30 chars rejected" 2 "at most 30 characters"

run "$YES
y
" "$BIN" "${COMMON[@]}" --team-name test-team --client-id abcdefghijklmnopqrstuvwxyz1234
expect "client id of exactly 30 chars accepted" 0 "Operator verified."

# Team name validation
run "" "$BIN" "${COMMON[@]}" --client-id bootstrap-test --team-name "bad team"
expect "team name rejects spaces" 2 "Team name must start with a letter"

run "" "$BIN" "${COMMON[@]}" --client-id bootstrap-test --team-name a
expect "team name below min length rejected" 2 "Team name must start with a letter"

run "" "$BIN" "${COMMON[@]}" --client-id bootstrap-test --team-name "a$(printf 'b%.0s' $(seq 1 70))"
expect "team name over 70 chars rejected" 2 "2-70 characters"

run "" "$BIN" "${COMMON[@]}" --client-id bootstrap-test --team-name personalStuff
expect "team name reserved Personal prefix rejected (any case)" 2 "must not start with 'Personal'"

# Service URL validation
run "" "$BIN" --auth-issuer http://auth.example.org/auth --registry-url "$REGISTRY" --redirect-url "$CALLBACK" "${IDS[@]}"
expect "remote http issuer rejected" 2 "must use https for remote hosts"

run "" "$BIN" --auth-issuer "$ISSUER" --registry-url http://registry.example.org/api/graphql --redirect-url "$CALLBACK" "${IDS[@]}"
expect "remote http registry rejected" 2 "must use https for remote hosts"

run "" "$BIN" --auth-issuer "https://user:pw@auth.example.org/auth" --registry-url "$REGISTRY" --redirect-url "$CALLBACK" "${IDS[@]}"
expect "issuer with user info rejected" 2 "without user info, query, or fragment"

run "" "$BIN" --auth-issuer "$ISSUER?x=1" --registry-url "$REGISTRY" --redirect-url "$CALLBACK" "${IDS[@]}"
expect "issuer with query rejected" 2 "without user info, query, or fragment"

run "" "$BIN" --auth-issuer "ftp://auth.example.org" --registry-url "$REGISTRY" --redirect-url "$CALLBACK" "${IDS[@]}"
expect "non-http scheme rejected" 2 "is not a valid http(s) URL"

run "" "$BIN" --auth-issuer "$ISSUER" --registry-url "https://registry.example.org" --redirect-url "$CALLBACK" "${IDS[@]}"
expect "registry url without path rejected" 2 "must include the endpoint path"

STUB_DISCOVERY_BODY='{"issuer":"http://localhost:8200/auth","authorization_endpoint":"a","token_endpoint":"http://localhost:8200/auth/oauth2/token"}' \
    run "y
y
" "$BIN" --auth-issuer http://localhost:8200/auth --registry-url http://127.0.0.1:8100/api/graphql --redirect-url "$CALLBACK" "${IDS[@]}" \
    --api-secret-file "$SECRET_FILE"
expect "loopback http services accepted" 0 "Operator verified."

# Redirect URL validation
run "" "$BIN" --auth-issuer "$ISSUER" --registry-url "$REGISTRY" --redirect-url "http://example.org:9876/callback" "${IDS[@]}"
expect "non-loopback redirect rejected" 2 "Callback URL must be http on localhost"

run "" "$BIN" --auth-issuer "$ISSUER" --registry-url "$REGISTRY" --redirect-url "https://localhost:9876/callback" "${IDS[@]}"
expect "https redirect rejected" 2 "Callback URL must be http on localhost"

run "" "$BIN" --auth-issuer "$ISSUER" --registry-url "$REGISTRY" --redirect-url "http://localhost/callback" "${IDS[@]}"
expect "redirect without port rejected" 2 "explicit port and path"

run "" "$BIN" --auth-issuer "$ISSUER" --registry-url "$REGISTRY" --redirect-url "http://localhost:9876" "${IDS[@]}"
expect "redirect without path rejected" 2 "explicit port and path"

run "" "$BIN" --auth-issuer "$ISSUER" --registry-url "$REGISTRY" --redirect-url "http://localhost:9876/callback?x=1" "${IDS[@]}"
expect "redirect with query rejected" 2 "explicit port and path"

run "" "$BIN" --auth-issuer "$ISSUER" --registry-url "$REGISTRY" --redirect-url "http://localhost:99999/callback" "${IDS[@]}"
expect "redirect with out-of-range port rejected" 2 "explicit port and path"

# Dependencies and platform
run "" "$(make_bin jq oauth2c uname)" "${BASE_ARGS[@]}"
expect "missing curl reported with macOS hint" 3 "curl: brew install curl"

run "" "$(make_bin curl oauth2c uname)" "${BASE_ARGS[@]}"
expect "missing jq reported" 3 "jq: brew install jq"

run "" "$(make_bin curl jq uname)" "${BASE_ARGS[@]}"
expect "missing oauth2c reported with tap hint" 3 "brew install cloudentity/tap/oauth2c"
expect "no-install notice printed" 3 "never installs packages"

DEBIAN_OS="$(os_release 'PRETTY_NAME="Debian GNU/Linux 12 (bookworm)"
ID=debian')"
STUB_UNAME=Linux BOOTSTRAP_OS_RELEASE_FILE="$DEBIAN_OS" run "" "$(make_bin jq uname)" "${BASE_ARGS[@]}"
expect "linux hints for all missing tools" 3 "Missing required tools: curl oauth2c"
expect "debian package hint" 3 "curl: sudo apt-get install curl"
expect "linux oauth2c upstream install hint" 3 "github.com/cloudentity/oauth2c#installation"
expect "linux oauth2c packages hint" 3 "repology.org/project/oauth2c"
expect "linux oauth2c go install hint" 3 "go install github.com/cloudentity/oauth2c@latest"
expect_absent "no brew hint on linux" "brew"

# check_distro_hint NAME OS_RELEASE_CONTENT EXPECTED_HINT
check_distro_hint() {
    STUB_UNAME=Linux BOOTSTRAP_OS_RELEASE_FILE="$(os_release "$2")" \
        run "" "$(make_bin curl oauth2c uname)" "${BASE_ARGS[@]}"
    expect "$1" 3 "jq: $3"
}
check_distro_hint "ubuntu uses apt" 'ID=ubuntu
ID_LIKE=debian' "sudo apt-get install jq"
check_distro_hint "fedora uses dnf" 'ID=fedora' "sudo dnf install jq"
check_distro_hint "rhel derivative uses dnf via ID_LIKE" 'ID="rocky"
ID_LIKE="rhel centos fedora"' "sudo dnf install jq"
check_distro_hint "amazon linux uses dnf" 'ID="amzn"
ID_LIKE="centos rhel fedora"' "sudo dnf install jq"
check_distro_hint "alpine uses apk" 'ID=alpine' "sudo apk add jq"
check_distro_hint "opensuse uses zypper" 'ID="opensuse-leap"
ID_LIKE="suse opensuse"' "sudo zypper install jq"
check_distro_hint "arch uses pacman" 'ID=arch' "sudo pacman -S jq"
check_distro_hint "unknown distro gets generic hint" 'ID=gentoo' "install 'jq' with your distribution's package manager"
check_distro_hint "derivative falls back to ID_LIKE" 'ID=linuxmint
ID_LIKE="ubuntu debian"' "sudo apt-get install jq"
check_distro_hint "os-release is not executed" 'ID=$(touch '"$WORK"'/pwned)debian' "install 'jq' with your distribution's package manager"
if [ -e "$WORK/pwned" ]; then
    record "os-release command substitution not run" 0 "pwned marker exists"
else
    record "os-release command substitution not run" 1 ""
fi

WSL_PROC="$WORK/proc-version-wsl"
printf '%s\n' 'Linux version 5.15.153.1-microsoft-standard-WSL2 (root@1) (gcc) #1 SMP' >"$WSL_PROC"
STUB_UNAME=Linux BOOTSTRAP_PROC_VERSION_FILE="$WSL_PROC" BOOTSTRAP_OS_RELEASE_FILE="$DEBIAN_OS" \
    run "" "$(make_bin curl oauth2c uname)" "${BASE_ARGS[@]}"
expect "wsl uses distro hints" 3 "jq: sudo apt-get install jq"

# oauth2c version probe
STUB_OAUTH2C_VERSION="oauth2c version 1.20.0 (commit abc, built at 2025-01-01)" run "" "$BIN" "${BASE_ARGS[@]}"
expect "old oauth2c rejected" 3 "oauth2c 1.20.0 is older than the minimum supported 1.21.1"
expect "old oauth2c macOS upgrade hint" 3 "brew upgrade cloudentity/tap/oauth2c"

STUB_UNAME=Linux BOOTSTRAP_OS_RELEASE_FILE="$DEBIAN_OS" \
    STUB_OAUTH2C_VERSION="oauth2c version 0.9.12 (commit abc, built at 2023-01-01)" run "" "$BIN" "${BASE_ARGS[@]}"
expect "old oauth2c linux upgrade hint" 3 "github.com/cloudentity/oauth2c#installation"

STUB_OAUTH2C_EXIT=1 run "" "$BIN" "${BASE_ARGS[@]}"
expect "failing oauth2c version rejected" 3 "'oauth2c version' failed"

STUB_OAUTH2C_VERSION="Usage: oauth2c-impostor" run "" "$BIN" "${BASE_ARGS[@]}"
expect "unrecognised oauth2c rejected" 3 "did not identify itself as oauth2c"

STUB_OAUTH2C_VERSION="" run "" "$BIN" "${BASE_ARGS[@]}"
expect "silent oauth2c rejected" 3 "did not identify itself as oauth2c"

for v in "1.21.1" "1.21.10" "1.22.0" "2.0.0" "v1.21.1"; do
    STUB_OAUTH2C_VERSION="oauth2c version $v (commit abc, built at 2026-01-01)" STUB_CURL_FAIL=1 \
        run "" "$BIN" "${BASE_ARGS[@]}"
    expect "oauth2c $v accepted" 1 "Failed to connect"
    expect_absent "oauth2c $v no version complaint" "older than the minimum"
done

STUB_OAUTH2C_VERSION="oauth2c version master (commit none, built at unknown)" STUB_CURL_FAIL=1 \
    run "" "$BIN" "${BASE_ARGS[@]}"
expect "source-built oauth2c warned but allowed" 1 "Warning: cannot confirm the oauth2c version"
expect "source-built oauth2c continues" 1 "Failed to connect"

STUB_UNAME=MINGW64_NT-10.0 run "" "$BIN" "${BASE_ARGS[@]}"
expect "native windows rejected" 3 "Run bootstrap.sh from WSL"

STUB_UNAME=SunOS run "" "$BIN" "${BASE_ARGS[@]}"
expect "unknown platform rejected" 3 "Unsupported platform 'SunOS'"

# Discovery
STUB_DISCOVERY_STATUS=302 run "" "$BIN" "${BASE_ARGS[@]}"
expect "discovery redirect rejected" 1 "redirects are not followed"

STUB_DISCOVERY_STATUS=404 run "" "$BIN" "${BASE_ARGS[@]}"
expect "discovery 404 rejected" 1 "Auth discovery returned HTTP 404"

STUB_DISCOVERY_BODY='<html>nope</html>' run "" "$BIN" "${BASE_ARGS[@]}"
expect "discovery non-json rejected" 1 "not a valid OpenID configuration"

STUB_DISCOVERY_BODY='{"issuer":"https://other.example.org/auth","authorization_endpoint":"a","token_endpoint":"t"}' \
    run "" "$BIN" "${BASE_ARGS[@]}"
expect "issuer mismatch rejected" 1 "Issuer mismatch"

STUB_DISCOVERY_BODY='{"issuer":"https://auth.example.org/auth","token_endpoint":"t"}' \
    run "" "$BIN" "${BASE_ARGS[@]}"
expect "missing authorization_endpoint rejected" 1 "missing authorization_endpoint or token_endpoint"

STUB_DISCOVERY_BODY='{"issuer":"https://auth.example.org/auth","authorization_endpoint":"a","token_endpoint":"https://auth.example.org/token","code_challenge_methods_supported":["S256"]}' \
    run "" "$BIN" "${BASE_ARGS[@]}"
expect "non-standard token_endpoint rejected at preflight" 1 "requires 'https://auth.example.org/auth/oauth2/token'"

STUB_DISCOVERY_BODY='{"issuer":"https://auth.example.org/auth","authorization_endpoint":"a","token_endpoint":"t","code_challenge_methods_supported":["plain"]}' \
    run "" "$BIN" "${BASE_ARGS[@]}"
expect "no S256 rejected" 1 "does not advertise PKCE S256"

STUB_CURL_FAIL=1 run "" "$BIN" "${BASE_ARGS[@]}"
expect "discovery transport failure reported" 1 "Could not reach the Auth discovery endpoint"
expect "transport failure shows curl error" 1 "curl: (7) Failed to connect"
if [[ "$OUT" == *$'\n'000* ]]; then
    record "transport failure omits write-out status" 0 "stray status line in output"
else
    record "transport failure omits write-out status" 1 ""
fi

run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
if grep -q -- '--proto =https' "$LOGS/argv.log" && ! grep -qE -- '(^| )(-L|--location)( |$)' "$LOGS/argv.log" \
    && grep -q -- '--max-time' "$LOGS/argv.log"; then
    record "curl restricted to https, no redirects, with timeout" 1 ""
else
    record "curl restricted to https, no redirects, with timeout" 0 "curl args: $(cat "$LOGS/argv.log")"
fi

# Registry reachability
for status in 200 403; do
    STUB_REGISTRY_STATUS=$status run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
    expect "registry $status treated as reachable" 0 "Operator verified."
done

STUB_REGISTRY_STATUS=404 run "" "$BIN" "${BASE_ARGS[@]}"
expect "registry 404 rejected" 1 "Registry returned HTTP 404"

STUB_REGISTRY_STATUS=301 run "" "$BIN" "${BASE_ARGS[@]}"
expect "registry redirect rejected" 1 "redirects are not followed"

STUB_REGISTRY_STATUS=000 run "" "$BIN" "${BASE_ARGS[@]}"
expect "registry status 000 rejected" 1 "Registry returned HTTP 000"

# Callback port
BUSY_PORT="$(free_port)"
python3 -c "
import socket, time
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(('127.0.0.1', $BUSY_PORT)); s.listen(5); time.sleep(60)
" &
LISTENER_PID=$!
for _ in 1 2 3 4 5 6 7 8 9 10; do
    if (exec 3<>"/dev/tcp/127.0.0.1/$BUSY_PORT") 2>/dev/null; then break; fi
    sleep 0.2
done
run "" "$BIN" --auth-issuer "$ISSUER" --registry-url "$REGISTRY" --redirect-url "http://localhost:$BUSY_PORT/callback" "${IDS[@]}"
expect "occupied callback port rejected" 1 "Callback port $BUSY_PORT on localhost is already in use"
kill "$LISTENER_PID" 2>/dev/null || true
wait "$LISTENER_PID" 2>/dev/null || true
LISTENER_PID=""

# API secret file validation
SECRET="$WORK/secret"
printf 'other-secret' >"$SECRET"
chmod 600 "$SECRET"
STUB_EXPECTED_SECRET=other-secret run "$YES
y
" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$SECRET"
expect "secret file with 600 and no trailing newline accepted" 0 "Operator verified."
expect_absent "secret value never printed" "other-secret"

chmod 640 "$SECRET"
run "" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$SECRET"
expect "group-readable secret file rejected" 2 "chmod 600"

chmod 604 "$SECRET"
run "" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$SECRET"
expect "world-readable secret file rejected" 2 "chmod 600"

: >"$WORK/empty"
chmod 600 "$WORK/empty"
run "" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$WORK/empty"
expect "empty secret file rejected" 2 "API secret file is empty"

run "" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$WORK/missing"
expect "missing secret file rejected" 2 "does not exist or is not a regular file"

run "" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$WORK"
expect "directory as secret file rejected" 2 "does not exist or is not a regular file"

printf '\nsecond-line\n' >"$WORK/blankfirst"
chmod 600 "$WORK/blankfirst"
run "$YES
" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$WORK/blankfirst"
expect "secret file with blank first line rejected" 2 "The API secret is empty"

printf 'crlf-secret\r\n' >"$WORK/crlf"
chmod 600 "$WORK/crlf"
STUB_EXPECTED_SECRET=crlf-secret run "$YES
y
" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$WORK/crlf"
expect "CRLF secret file accepted with CR stripped" 0 "Operator verified."

printf 'bad\tsecret\n' >"$WORK/ctrl"
chmod 600 "$WORK/ctrl"
run "$YES
" "$BIN" "${URLS[@]}" "${IDS[@]}" --api-secret-file "$WORK/ctrl"
expect "secret with control characters rejected" 2 "contains control characters"

# Auth client stage (I3)
post_count() {
    [ -f "$LOGS/requests.log" ] || { echo 0; return; }
    grep -c '^POST [^ ]*/v1/client ' "$LOGS/requests.log" || true
}

expect_posts() {
    local name="$1" want="$2" got
    got="$(post_count)"
    if [ "$got" = "$want" ]; then
        record "$name" 1 ""
    else
        record "$name" 0 "expected $want Auth POST(s), saw $got"
    fi
}

# client_json TYPE SECRET_JSON REDIRECTS_JSON [CLIENT_ID]
client_json() {
    printf '{"id":"a1","clientId":"%s","type":"%s","clientSecret":%s,"redirectUrls":%s}' \
        "${4:-bootstrap-test}" "$1" "$2" "$3"
}
COMPAT="$(client_json USER_PKCE null "[\"http://127.0.0.1:7000/other\",\"$CALLBACK\"]")"

# The client is seeded by registry-auth (db/seed); the script only verifies it.
run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "seeded client found" 0 "Auth client 'bootstrap-test' found (USER_PKCE, no secret; redirects: $CALLBACK)."
expect "confirmation announces seeded client" 0 "1. Look up the seeded Auth client 'bootstrap-test' (sends the Auth API secret to"
expect_posts "no Auth client is ever created" 0
if grep -q "^GET $ISSUER/v1/client/bootstrap-test" "$LOGS/requests.log"; then
    record "lookup uses issuer/v1/client/<id>" 1 ""
else
    record "lookup uses issuer/v1/client/<id>" 0 "$(cat "$LOGS/requests.log")"
fi
if grep -qx "X-Api-Secret: $SECRET_VALUE" "$LOGS/headers.log" && grep -qx 'Content-Type: application/json' "$LOGS/headers.log"; then
    record "secret sent as X-Api-Secret header via -H @file" 1 ""
else
    record "secret sent as X-Api-Secret header via -H @file" 0 "headers: $(cat "$LOGS/headers.log")"
fi
for leak in argv.log env.log; do
    if grep -q "$SECRET_VALUE" "$LOGS/$leak"; then
        record "secret absent from curl $leak" 0 "sentinel found in $leak"
    else
        record "secret absent from curl $leak" 1 ""
    fi
done
expect_absent "secret absent from script output" "$SECRET_VALUE"
if grep -q 'X-Api-Secret' "$LOGS/argv.log"; then
    record "no header literal on argv" 0 "$(grep X-Api-Secret "$LOGS/argv.log")"
else
    record "no header literal on argv" 1 ""
fi

run "$YES
$SECRET_VALUE
y
" "$BIN" "${URLS[@]}" "${IDS[@]}"
expect "hidden prompt secret works" 0 "Auth API secret (input hidden): "
expect "secret prompt discloses destination" 0 "The Auth API secret is sent only to $ISSUER/v1 (discovery verified)."
expect_absent "prompted secret not echoed" "$SECRET_VALUE"

run "$YES
" "$BIN" "${URLS[@]}" "${IDS[@]}"
expect "EOF at secret prompt cancels" 4 "Input ended before the API secret was provided"
expect_posts "EOF at secret prompt writes nothing" 0

run "n
" "$BIN" "${BASE_ARGS[@]}"
expect_posts "cancel at confirmation writes nothing" 0

run "nope
" "$BIN" "${BASE_ARGS[@]}"
expect "non-yes at confirmation cancels" 4 "Cancelled. Nothing was changed."
expect_posts "non-yes at confirmation writes nothing" 0
expect "confirmation prompt shown" 4 "Proceed? [y/N]:"
expect_absent "cancel at confirmation: no sign-in" "Signing in as the operator"

run "
" "$BIN" "${BASE_ARGS[@]}"
expect "blank at confirmation cancels" 4 "Cancelled. Nothing was changed."

# Piped stdin is fine: the confirmation is the only gate (ADR-54/60).
run "y
y
" "$BIN" "${BASE_ARGS[@]}"
expect "piped run proceeds past confirmation" 0 "Operator verified."

STUB_AUTH_GET_STATUS=404 run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "absent client is an error, not created" 1 "Auth has no client 'bootstrap-test'. This script does not create clients"
expect "absent client names the seed" 1 "V2.2026.10.20__seed_registry_user_pkce.sql"
expect "absent client: lookup runs after confirmation" 1 "Proceed? [y/N]"
expect_absent "absent client: stops before sign-in" "Signing in as the operator"
expect_posts "absent client: no write" 0

STUB_AUTH_GET_BODY="$COMPAT" run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "seeded client with extra redirects accepted" 0 "redirects: http://127.0.0.1:7000/other, $CALLBACK"
expect "association mirrors all Auth redirects" 0 "Operator verified."
if grep -qF "redirectUrls: [\\\"http://127.0.0.1:7000/other\\\",\\\"$CALLBACK\\\"]" "$LOGS/requests.log"; then
    record "createAuthClient carries every Auth redirect" 1 ""
else
    record "createAuthClient carries every Auth redirect" 0 "$(grep createAuthClient "$LOGS/requests.log")"
fi
expect_posts "extra redirects: no Auth write" 0

check_incompatible() {
    local name="$1" body="$2" message="$3"
    STUB_AUTH_GET_BODY="$body" run "$YES
" "$BIN" "${BASE_ARGS[@]}"
    expect "$name" 1 "$message"
    expect "$name: guidance" 1 "never modifies a client"
    expect "$name: lookup runs after confirmation" 1 "Proceed? [y/N]"
    expect_absent "$name: stops before sign-in" "Signing in as the operator"
    expect_posts "$name: no write" 0
}
check_incompatible "confidential USER client refused" "$(client_json USER '"******"' "[\"$CALLBACK\"]")" "is type USER, not USER_PKCE"
check_incompatible "NON_USER client refused" "$(client_json NON_USER '"******"' null)" "is type NON_USER, not USER_PKCE"
check_incompatible "PKCE client with secret refused" "$(client_json USER_PKCE '"******"' "[\"$CALLBACK\"]")" "has a client secret"
check_incompatible "client missing callback refused" "$(client_json USER_PKCE null '["http://localhost:1234/cb"]')" "does not list the callback $CALLBACK (registered: http://localhost:1234/cb)"
check_incompatible "client with no redirects refused" "$(client_json USER_PKCE null '[]')" "(registered: none)"
check_incompatible "record for another clientId refused" "$(client_json USER_PKCE null "[\"$CALLBACK\"]" other-id)" "reports clientId other-id"
check_incompatible "malformed 200 record refused" '[1,2]' "malformed client record"

check_lookup_error() {
    local name="$1" message="$2"
    run "$YES
" "$BIN" "${BASE_ARGS[@]}"
    expect "$name" 1 "$message"
    expect_posts "$name: no write" 0
}
STUB_EXPECTED_SECRET=different-secret check_lookup_error "wrong secret (403) explained" "rejected the API secret (HTTP 403 ACCESS_DENIED)"
STUB_AUTH_GET_STATUS=404 STUB_AUTH_GET_BODY='{"status":404}' check_lookup_error "404 without CLIENT_NOT_FOUND is not absence" "404 without reason CLIENT_NOT_FOUND"
STUB_AUTH_GET_STATUS=404 STUB_AUTH_GET_BODY='<html>Not Found</html>' check_lookup_error "HTML 404 is not absence" "404 without reason CLIENT_NOT_FOUND"
STUB_AUTH_GET_STATUS=500 STUB_AUTH_GET_BODY='{"status":500,"reason":"UNEXPECTED_ERROR"}' \
    check_lookup_error "lookup 500 aborts" "Auth returned HTTP 500 (UNEXPECTED_ERROR)"
STUB_AUTH_GET_STATUS=302 check_lookup_error "lookup redirect aborts" "redirects are not followed"
STUB_AUTH_GET_FAIL=1 check_lookup_error "lookup timeout aborts" "could not reach the Auth admin API: curl: (28)"

# jq version probe (I4)
JQBIN="$(make_bin curl jqstub oauth2c uname)"
STUB_JQ_VERSION="jq-1.5" run "" "$JQBIN" "${BASE_ARGS[@]}"
expect "old jq rejected" 3 "jq 1.5 is older than the minimum supported 1.6"
expect "old jq macOS upgrade hint" 3 "brew upgrade jq"

STUB_JQ_VERSION="jq-1.5" STUB_UNAME=Linux BOOTSTRAP_OS_RELEASE_FILE="$DEBIAN_OS" run "" "$JQBIN" "${BASE_ARGS[@]}"
expect "old jq linux upgrade hint" 3 "jq: sudo apt-get install"

STUB_JQ_EXIT=1 run "" "$JQBIN" "${BASE_ARGS[@]}"
expect "failing jq --version rejected" 3 "'jq --version' failed"

STUB_JQ_VERSION="Usage: jq-impostor" run "" "$JQBIN" "${BASE_ARGS[@]}"
expect "unrecognised jq rejected" 3 "did not identify itself as jq"

STUB_JQ_BROKEN=1 run "" "$JQBIN" "${BASE_ARGS[@]}"
expect "jq failing the functional probe rejected" 3 "failed a functional check (JSON with @base64d)"

for v in "jq-1.6" "jq-1.7.1" "jq-1.8.0"; do
    STUB_JQ_VERSION="$v" STUB_CURL_FAIL=1 run "" "$JQBIN" "${BASE_ARGS[@]}"
    expect "$v accepted" 1 "Failed to connect"
done

STUB_JQ_VERSION="jq-master-abc123" STUB_CURL_FAIL=1 run "" "$JQBIN" "${BASE_ARGS[@]}"
expect "source-built jq warned but allowed" 1 "Warning: cannot confirm the jq version"

run "" "$JQBIN" --help
expect "help names both tool minimums" 0 "jq 1.6+, oauth2c 1.21.1+"

# Sign-in (I4)
run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "sign-in and verification succeed" 0 "Operator verified."
expect "sign-in announced" 0 "Signing in as the operator (scopes: openid,email)"
expect "authorize URL relayed to the operator" 0 "  $ISSUER/oauth2/authorize?response_type=code"
expect "operator identity shown: name" 0 "Name:        Jane Doe"
expect "operator identity shown: alias" 0 "Alias:       jdoe"
expect "operator identity shown: email" 0 "Email:       jane.doe@example.org"
expect "operator identity shown: external id" 0 "External ID: $EXTERNAL_ID"
expect "identity confirmation asked" 0 "Is this you, and should the bootstrap continue as this user? [y/N]"
expect "later stages run to a complete bootstrap" 0 "Resource bootstrap complete."
OAUTH_ARGV="$(cat "$LOGS/oauth2c_argv.log")"
for want in "--silent" "--pkce" "--auth-method none" "--scopes openid,email" "--redirect-url $CALLBACK" "--client-id bootstrap-test" "--grant-type authorization_code"; do
    if [[ "$OAUTH_ARGV" == *"$want"* ]]; then
        record "oauth2c invoked with $want" 1 ""
    else
        record "oauth2c invoked with $want" 0 "argv: $OAUTH_ARGV"
    fi
done
for unwanted in "--authorization-endpoint" "--token-endpoint" "$SECRET_VALUE" "--client-secret"; do
    if [[ "$OAUTH_ARGV" == *"$unwanted"* ]]; then
        record "oauth2c argv without $unwanted by default" 0 "argv: $OAUTH_ARGV"
    else
        record "oauth2c argv without $unwanted by default" 1 ""
    fi
done
if [[ "$OAUTH_ARGV" == "$ISSUER "* ]]; then
    record "oauth2c given the issuer as first argument" 1 ""
else
    record "oauth2c given the issuer as first argument" 0 "argv: $OAUTH_ARGV"
fi
if grep -q "$SECRET_VALUE" "$LOGS/oauth2c_env.log"; then
    record "API secret absent from oauth2c environment" 0 "sentinel in oauth2c env"
else
    record "API secret absent from oauth2c environment" 1 ""
fi
expect_absent "access token absent from script output" "$TOKEN_SENTINEL"
for leak in argv.log env.log oauth2c_argv.log oauth2c_env.log requests.log; do
    if grep -q "$TOKEN_SENTINEL" "$LOGS/$leak"; then
        record "access token absent from $leak" 0 "sentinel found in $leak"
    else
        record "access token absent from $leak" 1 ""
    fi
done
if grep -qx "Authorization: Bearer $TOKEN_OK" "$LOGS/headers.log"; then
    record "bearer token sent to Registry via -H @file" 1 ""
else
    record "bearer token sent to Registry via -H @file" 0 "headers: $(grep -c . "$LOGS/headers.log") lines, no bearer"
fi
if grep -q "refresh_token\|rt-$TOKEN_SENTINEL" "$LOGS/headers.log" "$LOGS/requests.log"; then
    record "refresh token never leaves the script" 0 "refresh token seen in a request"
else
    record "refresh token never leaves the script" 1 ""
fi
MEMBER_REQ="$(grep '^POST [^ ]* .*getMemberByExternalId' "$LOGS/requests.log" | sed 's/^POST [^ ]* //')"
if [ "$(jq -r .query <<<"$MEMBER_REQ" 2>/dev/null)" = "{ getMemberByExternalId(externalId: \"$EXTERNAL_ID\") { externalId externalAlias email name } }" ]; then
    record "member lookup queries Registry-only fields by token externalId" 1 ""
else
    record "member lookup queries Registry-only fields by token externalId" 0 "request: $MEMBER_REQ"
fi
if grep -q 'Authorization' "$LOGS/argv.log"; then
    record "no Authorization literal on curl argv" 0 "$(grep Authorization "$LOGS/argv.log")"
else
    record "no Authorization literal on curl argv" 1 ""
fi
if ls -d "$WORK"/registry-bootstrap.* >/dev/null 2>&1; then
    record "private work dir removed on exit" 0 "$(ls -d "$WORK"/registry-bootstrap.*)"
else
    record "private work dir removed on exit" 1 ""
fi

run "$YES
n
" "$BIN" "${BASE_ARGS[@]}"
expect "declining the identity confirmation cancels" 4 "Cancelled. The Auth client and your Registry member record are kept"

run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "EOF at identity confirmation cancels" 4 "Cancelled. The Auth client and your Registry member record are kept"

run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "operator told to open the printed URL" 0 "Open the URL below in a browser on this machine"
expect_absent "no browser is opened" "Your browser will open"
if grep -q -- '--no-browser' "$LOGS/oauth2c_argv.log"; then
    record "--no-browser always passed to oauth2c" 1 ""
else
    record "--no-browser always passed to oauth2c" 0 "argv: $(cat "$LOGS/oauth2c_argv.log")"
fi

run "" "$BIN" "${BASE_ARGS[@]}" --no-browser
expect "--no-browser option removed" 2 "Unknown argument: --no-browser"

STUB_OAUTH2C_MODE=ctrl run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "sign-in succeeds despite control sequences in oauth2c output" 0 "Operator verified."
if [[ "$OUT" == *$'\033'* ]] || [[ "$OUT" == *$'\a'* ]] || [[ "$OUT" == *$'\r'* ]]; then
    record "terminal control sequences stripped from relayed oauth2c output" 0 "control bytes present"
else
    record "terminal control sequences stripped from relayed oauth2c output" 1 ""
fi
expect_absent "OSC title terminator stripped" $'\007'

# oauth2c failures
check_login_failure() {
    local name="$1" mode="$2" code="$3" message="$4" rerun="${5-yes}"
    STUB_OAUTH2C_MODE="$mode" run "$YES
" "$BIN" "${BASE_ARGS[@]}"
    expect "$name" "$code" "$message"
    if [ -n "$rerun" ]; then expect "$name: rerun hint" "$code" "Rerun bootstrap.sh"; fi
    if grep -q getMemberByExternalId "$LOGS/requests.log"; then
        record "$name: no Registry call" 0 "member lookup attempted"
    else
        record "$name: no Registry call" 1 ""
    fi
}
check_login_failure "browser sign-in declined" denied 4 "Sign-in was declined or cancelled in the browser (access_denied)"
check_login_failure "sign-in interrupted" interrupted 4 "Sign-in was interrupted"
check_login_failure "sign-in timeout" timeout 1 "did not finish before the browser timeout"
check_login_failure "token exchange error explained" tokenerr 1 "Sign-in failed: 400: invalid_grant"
expect "token exchange error: employeeId hint" 1 "your directory account has an employeeId"
expect "token exchange error: shared secret hint" 1 "both use the same X-Api-Secret"
check_login_failure "unreachable issuer during sign-in" unreachable 1 "oauth2c could not reach the Auth server: Get" ""
check_login_failure "malformed token response rejected" malformed 1 "unexpected token response"
check_login_failure "empty token response rejected" empty 1 "unexpected token response"
expect_absent "ERROR colouring stripped from failure output" $'\033['

STUB_TOKEN="$TOKEN_NO_EXTERNAL_ID" run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "token without externalId stops" 1 "access token has no externalId claim"
if grep -q getMemberByExternalId "$LOGS/requests.log"; then
    record "token without externalId: no Registry call" 0 "member lookup attempted"
else
    record "token without externalId: no Registry call" 1 ""
fi

STUB_TOKEN="not.a.jwt.at.all" run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "non-JWT access token rejected" 1 "unexpected token response"

STUB_OAUTH2C_EXPIRES=30 run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "token expiring inside the safety margin stops before Registry" 1 "Your sign-in has expired or is about to"
expect "expiry stop says rerun" 1 "rerun bootstrap.sh to sign in again"

STUB_OAUTH2C_EXPIRES=90 run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "token with 90s left is accepted" 0 "Operator verified."

# Registry member verification
for status in 401 403; do
    STUB_MEMBER_STATUS=$status run "$YES
" "$BIN" "${BASE_ARGS[@]}"
    expect "registry $status on member lookup explained" 1 "Registry rejected your access token (HTTP $status). Registry must trust this Auth issuer (jwt.sources)"
done

STUB_MEMBER_STATUS=500 run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "registry 500 on member lookup reported" 1 "Registry returned HTTP 500 to the member lookup"

STUB_MEMBER_BODY='{"errors":[{"message":"not found","extensions":{"reason":"EXTERNAL_USER_NOT_FOUND"}}],"data":null}' run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "member not found explained" 1 "Registry has no member for your account (EXTERNAL_USER_NOT_FOUND)"
expect "member not found: employeeId hint" 1 "needs an employeeId"

STUB_MEMBER_BODY='{"errors":[{"message":"Forbidden","extensions":{"reason":"ACCESS_DENIED\u001b[31m"}}]}' run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "other GraphQL error reported" 1 "Registry refused the member lookup (ACCESS_DENIED)"

STUB_MEMBER_BODY='{"data":{"getMemberByExternalId":{"externalId":"EXT-OTHER","externalAlias":"x","email":"x@example.org","name":"Someone Else"}}}' run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "member with a different externalId refused" 1 "does not match your token's externalId"
expect_absent "mismatched member never shown as the operator" "Someone Else"

STUB_MEMBER_BODY='{"data":{"getMemberByExternalId":null}}' run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "null member refused" 1 "does not match your token's externalId"

STUB_MEMBER_BODY="{\"data\":{\"getMemberByExternalId\":{\"externalId\":\"$EXTERNAL_ID\",\"externalAlias\":\"ev\u001b]0;il\u0007\",\"email\":null,\"name\":\"Eve\\u001b[31mRed\"}}}" run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect "member fields with control sequences still verify" 0 "Operator verified."
expect "null member field shown as dash" 0 "Email:       -"
if [[ "$OUT" == *$'\033'* ]] || [[ "$OUT" == *$'\a'* ]]; then
    record "control sequences stripped from member fields" 0 "control bytes present"
else
    record "control sequences stripped from member fields" 1 ""
fi

STUB_CURL_FAIL=0 STUB_MEMBER_STATUS=000 run "$YES
" "$BIN" "${BASE_ARGS[@]}"
expect "member lookup status 000 reported" 1 "Registry returned HTTP 000 to the member lookup"

# Browser endpoint split and host.docker.internal (ADR-23)
LOCAL_ISSUER="http://host.docker.internal:8200/auth"
LOCAL_DISCOVERY="{\"issuer\":\"$LOCAL_ISSUER\",\"authorization_endpoint\":\"$LOCAL_ISSUER/oauth2/authorize\",\"token_endpoint\":\"$LOCAL_ISSUER/oauth2/token\",\"code_challenge_methods_supported\":[\"S256\"]}"
STUB_DISCOVERY_BODY="$LOCAL_DISCOVERY" run "y
y
" "$BIN" --auth-issuer "$LOCAL_ISSUER" --registry-url http://localhost:8100/api/graphql --redirect-url "$CALLBACK" "${IDS[@]}" \
    --api-secret-file "$SECRET_FILE" --auth-browser-endpoint http://localhost:8200/auth/
expect "http host.docker.internal issuer accepted" 0 "Operator verified."
expect "browser endpoint shown in targets" 0 "Auth in browser:  http://localhost:8200/auth/oauth2/authorize"
if grep -q -- '--authorization-endpoint http://localhost:8200/auth/oauth2/authorize' "$LOGS/oauth2c_argv.log"; then
    record "browser endpoint passed to oauth2c (trailing slash trimmed)" 1 ""
else
    record "browser endpoint passed to oauth2c (trailing slash trimmed)" 0 "argv: $(cat "$LOGS/oauth2c_argv.log")"
fi
# oauth2c skips discovery once any endpoint is explicit, so the token endpoint must come along.
if grep -q -- "--token-endpoint $LOCAL_ISSUER/oauth2/token" "$LOGS/oauth2c_argv.log"; then
    record "token endpoint passed to oauth2c with browser endpoint" 1 ""
else
    record "token endpoint passed to oauth2c with browser endpoint" 0 "argv: $(cat "$LOGS/oauth2c_argv.log")"
fi
if grep -q "^$LOCAL_ISSUER " "$LOGS/oauth2c_argv.log"; then
    record "issuer unchanged when browser endpoint given" 1 ""
else
    record "issuer unchanged when browser endpoint given" 0 "argv: $(cat "$LOGS/oauth2c_argv.log")"
fi

# Target selection and local presets (ADR-64)
LOCAL_SECRETS="$WORK/secrets.yml"
printf '# comment\nOTHER=1\nREGISTRY_API_SECRET=%s\n' "$SECRET_VALUE" >"$LOCAL_SECRETS"

STUB_DISCOVERY_BODY="$LOCAL_DISCOVERY" BOOTSTRAP_SECRETS_FILE="$LOCAL_SECRETS" run "
y
" "$BIN" --redirect-url "$CALLBACK"
expect "Enter at target prompt picks local" 0 "Target: local docker preview stack"
expect "target prompt shown with default" 0 "Target [1]:"
expect "local presets: issuer" 0 "Auth issuer:      $LOCAL_ISSUER"
expect "local presets: registry" 0 "Registry GraphQL: http://localhost:8100/api/graphql"
expect "local presets: browser endpoint" 0 "Auth in browser:  http://localhost:8200/auth/oauth2/authorize"
expect "local presets: defaults" 0 "Client ID:        registry-user-pkce    Team: registry-admin"
expect "local: secret from secrets.yml" 0 "Auth API secret taken from $LOCAL_SECRETS."
expect "local: no confirmation gate" 0 "Local stack: proceeding without confirmation."
expect_absent "local: Proceed prompt absent" "Proceed? [y/N]"
expect_absent "local: no issuer prompt" "Auth issuer URL:"
expect "local: completes" 0 "Operator verified."
if grep -q -- "--authorization-endpoint http://localhost:8200/auth/oauth2/authorize" "$LOGS/oauth2c_argv.log"; then
    record "local: browser endpoint passed to oauth2c" 1 ""
else
    record "local: browser endpoint passed to oauth2c" 0 "argv: $(cat "$LOGS/oauth2c_argv.log")"
fi

STUB_DISCOVERY_BODY="$LOCAL_DISCOVERY" BOOTSTRAP_SECRETS_FILE="$LOCAL_SECRETS" run "y
" "$BIN" --target local --redirect-url "$CALLBACK"
expect "--target local skips the prompt" 0 "Target: local docker preview stack"
expect_absent "--target local: no target prompt" "Target [1]:"

STUB_DISCOVERY_BODY="$LOCAL_DISCOVERY" REGISTRY_API_SECRET="$SECRET_VALUE" run "y
" "$BIN" --target local --redirect-url "$CALLBACK"
expect "local: secret from environment" 0 "Auth API secret taken from environment variable REGISTRY_API_SECRET."
expect "local: environment secret works" 0 "Operator verified."

STUB_DISCOVERY_BODY="$LOCAL_DISCOVERY" STUB_EXPECTED_SECRET=from-env REGISTRY_API_SECRET=from-env BOOTSTRAP_SECRETS_FILE="$LOCAL_SECRETS" run "y
" "$BIN" --target local --redirect-url "$CALLBACK"
expect "local: environment wins over secrets.yml" 0 "taken from environment variable"

STUB_DISCOVERY_BODY="$LOCAL_DISCOVERY" run "$SECRET_VALUE
y
" "$BIN" --target local --redirect-url "$CALLBACK"
expect "local: missing secret explained" 0 "The Auth X-Api-Secret for the local stack was not found."
expect "local: recommends secrets.yml line" 0 "add the line REGISTRY_API_SECRET=<shared-secret> to"
expect "local: recommends export" 0 "export REGISTRY_API_SECRET=<shared-secret>"
expect "local: hidden prompt fallback works" 0 "Operator verified."
expect_absent "local: prompted secret not echoed" "$SECRET_VALUE"

STUB_DISCOVERY_BODY="$LOCAL_DISCOVERY" run "y
" "$BIN" --target local --redirect-url "$CALLBACK" --api-secret-file "$SECRET_FILE"
expect "local: --api-secret-file wins" 0 "Operator verified."
expect_absent "local: file given, no lookup message" "Auth API secret taken from"

STUB_DISCOVERY_BODY="$LOCAL_DISCOVERY" BOOTSTRAP_SECRETS_FILE="$LOCAL_SECRETS" run "y
" "$BIN" --target local --redirect-url "$CALLBACK" --team-name my-team --client-id my-client
expect "local: flags override presets" 0 "Client ID:        my-client    Team: my-team"

run "2
$ISSUER

$REGISTRY


$YES
$SECRET_VALUE
y
" "$BIN" --redirect-url "$CALLBACK"
expect "2 at target prompt picks other and prompts" 0 "Auth issuer URL:"
expect "other: confirmation gate kept" 0 "Proceed? [y/N]"
expect "other: completes" 0 "Operator verified."

run "3
" "$BIN"
expect "bad target answer rejected" 2 "Target must be 1 (local) or 2 (other), not '3'"

run "" "$BIN"
expect "EOF at target prompt cancels" 4 "Input ended before the target was chosen"

run "" "$BIN" --target cloud
expect "bad --target rejected" 2 "--target must be local or other, not 'cloud'"

run "$YES
y
" "$BIN" "${BASE_ARGS[@]}"
expect_absent "explicit URLs imply other: no target prompt" "Target [1]:"
expect "explicit URLs imply other: gate kept" 0 "Proceed? [y/N]"

run "" "$BIN" "${BASE_ARGS[@]}" --auth-browser-endpoint http://auth.example.org/auth
expect "remote http browser endpoint rejected" 2 "Auth browser endpoint URL must use https for remote hosts"

run "" "$BIN" "${BASE_ARGS[@]}" --auth-browser-endpoint "not a url"
expect "invalid browser endpoint rejected" 2 "Auth browser endpoint URL is not a valid http(s) URL"

run "" "$BIN" "${BASE_ARGS[@]}" --auth-browser-endpoint https://a.example.org --auth-browser-endpoint https://b.example.org
expect "duplicate browser endpoint rejected" 2 "--auth-browser-endpoint was given more than once"

run "" "$BIN" --auth-issuer http://host.docker.internal.evil.example.org/auth --registry-url "$REGISTRY" --redirect-url "$CALLBACK" "${IDS[@]}"
expect "http host.docker.internal lookalike rejected" 2 "must use https for remote hosts"

run "" "$BIN" "${BASE_ARGS[@]}" --help
expect "help documents the browser endpoint" 0 "--auth-browser-endpoint URL"
expect "help lists host.docker.internal" 0 "localhost, 127.0.0.1, [::1], or host.docker.internal"

# Team, client association, and report (I5)
YES2="$YES
y
"
GQL=""
# gql_reset -> starts a fresh set of scripted Registry answers (STUB_GQL_DIR).
gql_reset() { GQL="$(mktemp -d "$WORK/gql.XXXXXX")"; }
# gql_case OP.N ACTION [BODY] -> scripts the n-th call of a Registry operation.
gql_case() { printf '%s\n%s' "$2" "${3-}" >"$GQL/$1"; }
# calls PATTERN -> number of logged Registry requests whose body matches PATTERN.
calls() { grep -c -- "$1" "$LOGS/requests.log" 2>/dev/null || true; }
# expect_calls NAME PATTERN COUNT
expect_calls() {
    local got
    got="$(calls "$2")"
    if [ "$got" = "$3" ]; then record "$1" 1 ""; else record "$1" 0 "expected $3 '$2' calls, got $got"; fi
}
state_dir() { mktemp -d "$WORK/reg.XXXXXX"; }

# Greenfield: team and association created once, then verified.
run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "greenfield bootstrap completes" 0 "Resource bootstrap complete."
expect "greenfield: team created" 0 "Team 'test-team':  created; you are OWNER"
expect "greenfield: association created" 0 "Association:  created; version 1 owned by 'test-team'"
expect "greenfield: auth client reported" 0 "Auth client 'bootstrap-test':  seeded (USER_PKCE; redirects: $CALLBACK)"
expect "report: admin rights stay operator-managed" 0 "owning 'test-team' grants none by itself"
expect "report: setup access reminder" 0 "restore your normal access restrictions"
expect_absent "report never claims full configuration" "fully configured"
expect_calls "greenfield: one createTeam" 'createTeam(' 1
expect_calls "greenfield: one createAuthClient" 'createAuthClient(' 1
TEAM_REQ="$(grep 'createTeam(' "$LOGS/requests.log" | sed 's/^POST [^ ]* //' | jq -r .query 2>/dev/null)"
if [ "$TEAM_REQ" = 'mutation { createTeam(team: {name: "test-team", type: SHARED, description: "Created by the Registry guided bootstrap"}) { name } }' ]; then
    record "createTeam request shape" 1 ""
else
    record "createTeam request shape" 0 "got: $TEAM_REQ"
fi
ASSOC_REQ="$(grep 'createAuthClient(' "$LOGS/requests.log" | sed 's/^POST [^ ]* //' | jq -r .query 2>/dev/null)"
if [ "$ASSOC_REQ" = "mutation { createAuthClient(authClient: {teamName: \"test-team\", clientId: \"bootstrap-test\", description: \"Created by the Registry guided bootstrap\", type: USER_PKCE, redirectUrls: [\"$CALLBACK\"]}) { clientId clientVersion } }" ]; then
    record "createAuthClient request shape" 1 ""
else
    record "createAuthClient request shape" 0 "got: $ASSOC_REQ"
fi
LOOKUPS="$(grep 'getAuthClient(' "$LOGS/requests.log" | grep -v 'clientVersion: 1)')"
if [ -n "$LOOKUPS" ] && ! grep -q -E 'redirectUrls|type' <<<"$LOOKUPS"; then
    record "presence lookups select Registry-only fields" 1 ""
else
    record "presence lookups select Registry-only fields" 0 "got: $LOOKUPS"
fi
expect_calls "final verification reads Auth-backed fields" 'clientVersion: 1) { clientId clientVersion type redirectUrls' 1
I5_CALLS="$(grep -c -E 'getTeamByName|createTeam|getAuthClient|createAuthClient' "$LOGS/requests.log")"
BEARERS="$(grep -c 'Authorization: Bearer ' "$LOGS/headers.log")"
if [ "$I5_CALLS" -ge 7 ] && [ "$BEARERS" = "$((I5_CALLS + 1))" ]; then
    record "every registry stage call carries the bearer" 1 ""
else
    record "every registry stage call carries the bearer" 0 "$I5_CALLS stage calls, $BEARERS bearer headers (expected calls + 1 for the member lookup)"
fi
if grep -q "$TOKEN_SENTINEL" "$LOGS/requests.log" "$LOGS/argv.log" "$LOGS/env.log"; then
    record "token stays out of bodies, argv and env in I5 calls" 0 "sentinel leaked"
else
    record "token stays out of bodies, argv and env in I5 calls" 1 ""
fi
expect_absent "token not in output" "$TOKEN_SENTINEL"

# Rerun against the same Registry state: verifies and creates nothing (AC-08).
REG="$(state_dir)"
STUB_REG_STATE_DIR="$REG" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "rerun setup: first run completes" 0 "Resource bootstrap complete."
STUB_REG_STATE_DIR="$REG" STUB_AUTH_GET_STATUS=200 \
    STUB_AUTH_GET_BODY="{\"clientId\":\"bootstrap-test\",\"type\":\"USER_PKCE\",\"redirectUrls\":[\"$CALLBACK\"],\"clientSecret\":null}" \
    run "$YES
y
y
" "$BIN" "${BASE_ARGS[@]}"
expect "rerun completes" 0 "Resource bootstrap complete."
expect "rerun reuses team" 0 "Team 'test-team':  reused; you are OWNER"
expect "rerun skips association" 0 "Association:  already present"
expect_calls "rerun: no createTeam" 'createTeam(' 0
expect_calls "rerun: no createAuthClient" 'createAuthClient(' 0
if [ "$(cat "$REG/reg_client_version")" = 1 ]; then
    record "rerun: client still at version 1" 1 ""
else
    record "rerun: client still at version 1" 0 "version $(cat "$REG/reg_client_version")"
fi

# Existing team
STUB_REG_TEAM=owner run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "owned team reused" 0 "Team 'test-team' exists and you are its OWNER; reusing it."
expect "owned team: association created" 0 "Association:  created"
expect_calls "owned team: no createTeam" 'createTeam(' 0

STUB_REG_TEAM=owner STUB_REG_TEAM_NAME=Test-Team run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "team name matched case-insensitively" 0 "Resource bootstrap complete."

STUB_REG_TEAM=owner STUB_REG_CLIENT=ours run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "existing association verified and skipped" 0 "Client 'bootstrap-test' is already associated with team 'test-team'; skipping."
expect_calls "existing association: no writes" 'mutation' 0

for role in member viewer; do
    upper="$(printf '%s' "$role" | tr '[:lower:]' '[:upper:]')"
    STUB_REG_TEAM=$role run "$YES2" "$BIN" "${BASE_ARGS[@]}"
    expect "team where operator is $role stops" 1 "your role is $upper, not OWNER"
    expect_calls "team where operator is $role: no writes" 'mutation' 0
done

STUB_REG_TEAM=outsider run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "team without the operator stops" 1 "you are not a member. The script never adds you to a team you do not own"
expect_calls "foreign team: no writes" 'mutation' 0
expect "stop lists what is kept" 1 "Kept on the server: your Registry member record. Nothing is rolled back."
expect "stop explains rerun" 1 "Fix the cause and rerun bootstrap.sh"

STUB_REG_TEAM=personal run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "non-SHARED team stops" 1 "is a PERSONAL team, not SHARED"

# Client conflicts are found before anything is written.
STUB_REG_CLIENT=other run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "client owned by another team stops" 1 "already associated with team someone-elses-team, not 'test-team'"
expect_calls "client owned elsewhere: stops before createTeam" 'mutation' 0

STUB_REG_CLIENT=other STUB_REG_OTHER_TEAM=$'ev\033[31mil' run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "other team name shown sanitized" 1 "associated with team evil, not 'test-team'"
expect_absent "other team name: no escape byte" $'\033'

STUB_REG_TEAM=owner STUB_REG_CLIENT=v2 run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "extra client versions stop" 1 "Registry already holds version 2 of client 'bootstrap-test'"
expect_calls "extra versions: no writes" 'mutation' 0

# Lookup failures never mean "absent" (AC-09).
gql_reset; gql_case getTeamByName.1 fail
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "team lookup transport failure stops" 1 "Could not reach Registry for the team lookup"
expect_calls "team lookup failure: no writes" 'mutation' 0

gql_reset; gql_case getTeamByName.1 "reply 500" '{"error":"boom"}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "team lookup HTTP 500 stops" 1 "Registry returned HTTP 500 to the team lookup."

gql_reset; gql_case getTeamByName.1 "reply 200" 'not json'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "malformed team lookup stops" 1 "Registry returned an unexpected response to the team lookup."
expect_calls "malformed team lookup: no writes" 'mutation' 0

gql_reset; gql_case getTeamByName.1 "reply 200" '{"data":{"getTeamByName":null}}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "null team without error is not absence" 1 "unexpected response to the team lookup"

gql_reset; gql_case getTeamByName.1 "reply 200" '{"errors":[{"message":"x","extensions":{"reason":"NOT_IN_AUTHORIZED_TEAMS_LIST"}}],"data":null}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "authorized-teams refusal explained" 1 "Registry refused the team lookup (NOT_IN_AUTHORIZED_TEAMS_LIST)"
expect "authorized-teams hint" 1 "Check the deployment's authorized-teams setting; this script does not change it."

gql_reset; gql_case getTeamByName.1 "reply 401" ''
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "token rejected at team lookup" 1 "Registry rejected your access token during the team lookup (HTTP 401)"

gql_reset; gql_case getAuthClient.1 "reply 200" '{"errors":[{"message":"x","extensions":{"reason":"INTERNAL_ERROR"}}],"data":null}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "client lookup error is not absence" 1 "Registry refused the client lookup (INTERNAL_ERROR)"
expect_calls "client lookup error: no writes" 'mutation' 0

gql_reset; gql_case getAuthClient.1 "reply 200" '{"data":{"getAuthClient":{"clientId":"bootstrap-test","clientVersion":1,"teams":[]}}}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "client without team stops" 1 "without an owning team"

# Team creation outcomes (AC-07, AC-12).
gql_reset; gql_case createTeam.1 lost
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "lost team creation reconciled by lookup" 0 "No response to the team creation"
expect "lost team creation: confirmed and continued" 0 "Team 'test-team':  created (confirmed by lookup)"
expect_calls "lost team creation: not retried" 'createTeam(' 1

gql_reset; gql_case createTeam.1 fail
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "failed team creation stops" 1 "Team 'test-team' was not created."
expect_calls "failed team creation: not retried" 'createTeam(' 1
expect_calls "failed team creation: no association" 'createAuthClient(' 0

gql_reset; gql_case createTeam.1 "reply 500" ''
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "team creation HTTP 500 checked then stops" 1 "Registry returned HTTP 500 to the team creation. Team 'test-team' was not created."

gql_reset; gql_case createTeam.1 "reply 200" '{"errors":[{"message":"x","extensions":{"reason":"NOT_IN_AUTHORIZED_TEAMS_LIST"}}],"data":null}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "team creation refusal explained" 1 "Registry refused the team creation (NOT_IN_AUTHORIZED_TEAMS_LIST). Registry allows this only while no configured authorized team exists"

gql_reset; gql_case createTeam.1 "reply 200" '{"errors":[{"message":"x","extensions":{"reason":"TEAMS_LIMIT_EXCEEDED"}}],"data":null}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "team limit explained" 1 "maximum number of teams"

gql_reset; gql_case createTeam.1 "reply 200" '{"data":{"createTeam":{"name":"test-team"}}}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "claimed creation without the team stops" 1 "Registry reported the team as created, but it cannot be found."

# Association outcomes (AC-08, AC-12).
gql_reset; gql_case createAuthClient.1 lost
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "lost association reconciled by lookup" 0 "Association:  created (confirmed by lookup)"
expect_calls "lost association: never retried" 'createAuthClient(' 1

gql_reset; gql_case createAuthClient.1 fail
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "failed association stops" 1 "The association was not created."
expect "failed association keeps the team" 1 "Kept on the server: your Registry member record, team 'test-team'. Nothing is rolled back."
expect_calls "failed association: never retried" 'createAuthClient(' 1

gql_reset; gql_case createAuthClient.1 "reply 200" '{"errors":[{"message":"x","extensions":{"reason":"AUTH_CLIENT_DUPLICATE_FOUND"}}],"data":null}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "duplicate association explained" 1 "Registry refused the client association (AUTH_CLIENT_DUPLICATE_FOUND). Another team already owns this client ID"
expect_calls "duplicate association: never retried" 'createAuthClient(' 1

gql_reset; gql_case createAuthClient.1 "reply 502" ''
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "association HTTP 502 checked then stops" 1 "Registry returned HTTP 502 to the client association. Checking whether the association exists"

# Interrupted run, then rerun: the rerun finishes without duplicates (AC-12).
REG="$(state_dir)"; gql_reset; gql_case createAuthClient.1 fail
STUB_REG_STATE_DIR="$REG" STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "partial run stops after team creation" 1 "The association was not created."
STUB_REG_STATE_DIR="$REG" STUB_GQL_DIR="$GQL" STUB_AUTH_GET_STATUS=200 \
    STUB_AUTH_GET_BODY="{\"clientId\":\"bootstrap-test\",\"type\":\"USER_PKCE\",\"redirectUrls\":[\"$CALLBACK\"],\"clientSecret\":null}" \
    run "$YES
y
y
" "$BIN" "${BASE_ARGS[@]}"
expect "rerun after partial run completes" 0 "Resource bootstrap complete."
expect "rerun after partial run reuses team" 0 "Team 'test-team':  reused"
expect_calls "rerun after partial run: no second team" 'createTeam(' 0
if [ "$(cat "$REG/reg_client_version")" = 1 ]; then
    record "rerun after partial run: version 1 only" 1 ""
else
    record "rerun after partial run: version 1 only" 0 "version $(cat "$REG/reg_client_version")"
fi

# Final Auth-backed verification.
gql_reset; gql_case getAuthClientFull.1 "reply 200" '{"errors":[{"message":"x","extensions":{"reason":"AUTH_API_ERROR"}}],"data":null}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "verification error explains version pairing" 1 "Registry could not read the Auth settings of client 'bootstrap-test' (AUTH_API_ERROR). Registry must reach Auth, and both must come from the same 2.0.x release."
expect "verification error keeps association" 1 "the association of 'bootstrap-test' with 'test-team'. Nothing is rolled back."
expect_absent "verification error: no completion claim" "Resource bootstrap complete."

gql_reset; gql_case getAuthClientFull.1 "reply 200" '{"data":{"getAuthClient":{"clientId":"bootstrap-test","clientVersion":1,"type":null,"redirectUrls":null,"teams":[{"name":"test-team"}]}}}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "missing Auth settings stop" 1 "Registry returned no Auth settings for client 'bootstrap-test'"

gql_reset; gql_case getAuthClientFull.1 "reply 200" '{"data":{"getAuthClient":{"clientId":"bootstrap-test","clientVersion":1,"type":"USER_PKCE","redirectUrls":["http://localhost:1/other"],"teams":[{"name":"test-team"}]}}}'
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "redirect mismatch stops" 1 "Registry reports different Auth settings for client 'bootstrap-test' (type and redirects: USER_PKCE http://localhost:1/other)"

gql_reset; gql_case getAuthClientFull.1 fail
STUB_GQL_DIR="$GQL" run "$YES2" "$BIN" "${BASE_ARGS[@]}"
expect "verification transport failure stops" 1 "Could not reach Registry for the client verification"

# Output channel
rm -rf "$LOGS"; mkdir -p "$LOGS"
printf '%s' "$SECRET_VALUE" >"$LOGS/expected_secret"
printf '%s' "$TOKEN_OK" >"$LOGS/token"
printf '%s' "$MEMBER_OK" >"$LOGS/member"
OUT="$(printf 'y\ny\n' | env -i HOME="$WORK" TMPDIR="$WORK" PATH="$BIN" STUB_DISCOVERY_BODY="$DISCOVERY_OK" \
    STUB_REG_REDIRECT="$CALLBACK" \
    STUB_LOG_DIR="$LOGS" \
    "$TEST_BASH" "$SCRIPT" "${BASE_ARGS[@]}" 2>/dev/null)"
CODE=$?
if [ "$CODE" = 0 ] && [ -z "$OUT" ]; then
    record "all user-facing output goes to stderr" 1 ""
else
    record "all user-facing output goes to stderr" 0 "stdout was: $OUT (exit $CODE)"
fi

echo
echo "$PASS passed, $FAIL failed"
if [ "$FAIL" -ne 0 ]; then
    printf 'Failed:%s\n' "$FAILED_NAMES"
    exit 1
fi
