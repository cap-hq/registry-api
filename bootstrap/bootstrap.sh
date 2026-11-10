#!/usr/bin/env bash
# SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
# SPDX-License-Identifier: Apache-2.0
#
# Registry guided bootstrap.
#
# RUN ONCE, ON A NEW INSTALLATION. This script exists to take a greenfield
# Registry deployment from "empty" to "has an operator-owned team": it
# verifies the seeded public PKCE client, then creates (or verifies) the
# operator membership, team, and client association using only the public
# Auth and Registry APIs. It is not a routine administration tool. Rerunning
# it is safe (it verifies and creates nothing new), but once the team exists
# it has nothing left to do. Run ./bootstrap.sh --help for usage.
#
# Compatible with bash 3.2 (macOS default) and later.

set -euo pipefail
set +x
shopt -s extglob

readonly SCRIPT_NAME="${0##*/}"
case "$0" in
    */*) SCRIPT_DIR="$(cd "${0%/*}" && pwd)" ;;
    *) SCRIPT_DIR="$(pwd)" ;;
esac
readonly SCRIPT_DIR

readonly EXIT_OK=0
readonly EXIT_FAILURE=1
readonly EXIT_USAGE=2
readonly EXIT_DEPENDENCY=3
readonly EXIT_CANCELLED=4

readonly DEFAULT_CLIENT_ID="registry-user-pkce"
readonly DEFAULT_TEAM_NAME="registry-admin"
readonly DEFAULT_REDIRECT_URL="http://localhost:9876/callback"

# Local docker preview stack (scripts/registry-stack): the services listen on
# fixed host ports, and containers reach each other through host.docker.internal.
readonly LOCAL_AUTH_ISSUER="http://host.docker.internal:8200/auth"
readonly LOCAL_AUTH_BROWSER_ENDPOINT="http://localhost:8200/auth"
readonly LOCAL_REGISTRY_URL="http://localhost:8100/api/graphql"
# registry-stack feeds this gitignored env-file to the Auth container, so it
# holds the same X-Api-Secret the server validates. Relative to this script.
readonly LOCAL_SECRETS_FILE="${BOOTSTRAP_SECRETS_FILE:-$SCRIPT_DIR/../registry-auth/src/main/resources/secrets.yml}"
readonly API_SECRET_ENV_NAME="REGISTRY_API_SECRET"
readonly HTTP_TIMEOUT_SECONDS=15

# Oldest oauth2c release this script has been verified against.
readonly OAUTH2C_MIN_VERSION="1.21.1"
readonly OAUTH2C_INSTALL_DOCS="https://github.com/cloudentity/oauth2c#installation"
readonly OAUTH2C_PACKAGES_URL="https://repology.org/project/oauth2c/versions"
# Oldest jq with @base64d, used to read the access token's claims.
readonly JQ_MIN_VERSION="1.6"
# Registry calls stop with a rerun instruction once the access token has less
# than this many seconds left; tokens are never refreshed.
readonly TOKEN_EXPIRY_MARGIN_SECONDS=60
# Overridable so tests can exercise each distribution and WSL.
readonly OS_RELEASE_FILE="${BOOTSTRAP_OS_RELEASE_FILE:-/etc/os-release}"
readonly PROC_VERSION_FILE="${BOOTSTRAP_PROC_VERSION_FILE:-/proc/version}"

# Mirrors Registry AuthClientServiceValidator; checked before any Auth write so
# Registry can always associate the client created in Auth.
readonly CLIENT_ID_MAX_LENGTH=30
readonly CLIENT_ID_REGEX='^[a-zA-Z][a-zA-Z0-9-]+$'

# Mirrors Registry default team validation constraints (application.yaml and
# TeamServiceValidator). The server stays authoritative; this only catches
# mistakes before anything is written.
readonly TEAM_NAME_MIN_LENGTH=2
readonly TEAM_NAME_MAX_LENGTH=70
readonly TEAM_NAME_REGEX='^[a-zA-Z][a-zA-Z0-9_-]+$'
readonly TEAM_NAME_RESERVED_PREFIX="personal"

readonly RESOURCE_DESCRIPTION="Created by the Registry guided bootstrap"

readonly URL_REGEX='^(https?)://([^/?#@]+)(/[^?#]*)?$'
readonly HOSTPORT_REGEX='^(\[[0-9a-fA-F:]+\]|[^:]+)(:([0-9]+))?$'

# "local" (registry-stack presets, no confirmation) or "other" (prompted).
TARGET=""
AUTH_ISSUER=""
REGISTRY_URL=""
CLIENT_ID=""
TEAM_NAME=""
REDIRECT_URL=""
AUTH_BROWSER_ENDPOINT=""
API_SECRET_FILE=""

AUTH_ADMIN_BASE=""
CALLBACK_HOST=""
CALLBACK_PORT=""
PLATFORM=""
DISTRO_FAMILY=""

# Set by parse_url.
URL_SCHEME=""
URL_HOST=""
URL_PORT=""
URL_PATH=""

# Set by http_request.
HTTP_STATUS=""
HTTP_BODY=""

# Held in memory only, never exported, and sent to curl through a process
# substitution so it never appears in argv, the environment, or on disk.
API_SECRET=""

# Auth client state: "seeded" once the lookup finds a compatible client.
CLIENT_STATE=""
# Comma+space list for display, JSON array for the Registry association.
CLIENT_REDIRECTS=""
CLIENT_REDIRECTS_JSON=""
FETCH_RESULT=""

# Like API_SECRET: memory only, sent to curl through a process substitution and
# to jq on stdin. The refresh and ID tokens are discarded on receipt.
ACCESS_TOKEN=""
TOKEN_DEADLINE=0
OPERATOR_EXTERNAL_ID=""

# Private (0700) directory for the sanitized sign-in transcript; holds no
# credentials and is removed on exit.
WORK_DIR=""

# Registry stages: TEAM_STATE/ASSOC_STATE hold the last authoritative read
# (absent | owned / absent | ours); *_OUTCOME is what the final report shows.
TEAM_STATE=""
TEAM_OUTCOME=""
ASSOC_STATE=""
ASSOC_OUTCOME=""
WRITE_WHY=""

# --------------------------------------------------------------------------
# Output and lifecycle
# --------------------------------------------------------------------------

say() { printf '%s\n' "$*" >&2; }
info() { say "  $*"; }
die() {
    local code="$1"
    shift
    say "ERROR: $*"
    exit "$code"
}

cleanup() {
    API_SECRET=""
    ACCESS_TOKEN=""
    if [ -n "$WORK_DIR" ]; then
        rm -rf "$WORK_DIR"
    fi
}

# clean TEXT -> prints TEXT without terminal escape sequences or other control
# characters, so server- and tool-supplied text cannot drive the terminal.
clean() {
    local text="$1"
    text="${text//$'\033'\[*([0-9;?])[A-Za-z]/}"
    printf '%s' "${text//[[:cntrl:]]/}"
}

on_signal() {
    say ""
    say "Interrupted. Anything already created on the server is kept;"
    say "rerun $SCRIPT_NAME to verify it and continue."
    exit "$EXIT_CANCELLED"
}

trap cleanup EXIT
trap on_signal INT TERM HUP

usage() {
    cat <<EOF
Usage: $SCRIPT_NAME [options]

Guided bootstrap for a NEW Registry deployment. Run it once, after the
first start of a greenfield installation, to create the operator-owned team
that the seeded PKCE client belongs to. It is not a routine administration
tool; rerunning it only verifies what exists.

First choose the target: the local docker preview stack
(scripts/registry-stack), whose endpoints are known and which runs through
without confirmation, or any other deployment, where missing values are
prompted for and nothing is created until you confirm the targets (y/N).

Options:
  --target local|other    local: issuer $LOCAL_AUTH_ISSUER,
                          browser endpoint $LOCAL_AUTH_BROWSER_ENDPOINT,
                          Registry $LOCAL_REGISTRY_URL, defaults for the
                          rest, API secret from \$$API_SECRET_ENV_NAME or
                          ../registry-auth/src/main/resources/secrets.yml.
                          Prompted when omitted (Enter = local), unless
                          --auth-issuer or --registry-url is given (other).
  --auth-issuer URL       Auth server issuer, e.g. https://auth.example.org/auth
  --registry-url URL      Registry GraphQL endpoint, e.g.
                          https://registry.example.org/api/graphql
  --client-id ID          Seeded PKCE client ID (default: $DEFAULT_CLIENT_ID).
                          The client must already exist in Auth; this script
                          never creates one.
  --team-name NAME        Team to own the client (default: $DEFAULT_TEAM_NAME)
  --redirect-url URL      Loopback callback URL (default: $DEFAULT_REDIRECT_URL)
  --auth-browser-endpoint URL
                          Base URL the browser uses to reach the same Auth
                          server, when it differs from --auth-issuer (local
                          Docker: issuer http://host.docker.internal:8200/auth,
                          browser http://localhost:8200/auth). Default: issuer.
  --api-secret-file PATH  File holding the Auth X-Api-Secret, readable only by
                          you (chmod 600). Without it you are prompted (hidden).
  -h, --help              Show this help.

Rules:
  - Remote services must use https. Plain http is accepted only for
    localhost, 127.0.0.1, [::1], or host.docker.internal.
  - A single trailing slash on --auth-issuer is removed; the issuer reported
    by the server's discovery document must then match exactly, and its
    token_endpoint must be <issuer>/oauth2/token.
  - The API secret is never accepted as a command-line argument.
  - Requested scopes are fixed to openid,email.
  - The access token is used only during this run. It is never printed,
    saved, exported, or refreshed; if it expires, rerun to sign in again.

Requires: bash 3.2+, curl, jq $JQ_MIN_VERSION+, oauth2c $OAUTH2C_MIN_VERSION+. Supported on macOS, Linux, and
Windows through WSL.

Exit codes: 0 success, 1 failure, 2 invalid usage or input,
            3 missing dependency or unsupported platform, 4 cancelled.
EOF
}

# --------------------------------------------------------------------------
# Argument parsing and prompts
# --------------------------------------------------------------------------

# set_option_once NAME CURRENT VALUE -> prints VALUE; rejects repeats and blanks.
set_option_once() {
    local name="$1" current="$2" value="$3"
    if [ -n "$current" ]; then
        die "$EXIT_USAGE" "$name was given more than once."
    fi
    if [ -z "$value" ]; then
        die "$EXIT_USAGE" "$name requires a value."
    fi
    printf '%s' "$value"
}

parse_args() {
    local arg name value
    while [ "$#" -gt 0 ]; do
        arg="$1"
        shift
        case "$arg" in
            -h|--help)
                usage
                exit "$EXIT_OK"
                ;;
            --api-secret|--api-secret=*)
                die "$EXIT_USAGE" "The API secret is not accepted as an argument. Use --api-secret-file or the hidden prompt."
                ;;
            --target|--target=*|\
            --auth-issuer|--auth-issuer=*|--registry-url|--registry-url=*|--client-id|--client-id=*|\
            --team-name|--team-name=*|--redirect-url|--redirect-url=*|--api-secret-file|--api-secret-file=*|\
            --auth-browser-endpoint|--auth-browser-endpoint=*)
                case "$arg" in
                    *=*)
                        name="${arg%%=*}"
                        value="${arg#*=}"
                        ;;
                    *)
                        name="$arg"
                        if [ "$#" -eq 0 ]; then
                            die "$EXIT_USAGE" "$name requires a value."
                        fi
                        value="$1"
                        shift
                        ;;
                esac
                case "$name" in
                    --target)
                        TARGET="$(set_option_once "$name" "$TARGET" "$value")"
                        case "$TARGET" in
                            local|other) ;;
                            *) die "$EXIT_USAGE" "--target must be local or other, not '$TARGET'." ;;
                        esac
                        ;;
                    --auth-issuer) AUTH_ISSUER="$(set_option_once "$name" "$AUTH_ISSUER" "$value")" ;;
                    --registry-url) REGISTRY_URL="$(set_option_once "$name" "$REGISTRY_URL" "$value")" ;;
                    --client-id) CLIENT_ID="$(set_option_once "$name" "$CLIENT_ID" "$value")" ;;
                    --team-name) TEAM_NAME="$(set_option_once "$name" "$TEAM_NAME" "$value")" ;;
                    --redirect-url) REDIRECT_URL="$(set_option_once "$name" "$REDIRECT_URL" "$value")" ;;
                    --api-secret-file) API_SECRET_FILE="$(set_option_once "$name" "$API_SECRET_FILE" "$value")" ;;
                    --auth-browser-endpoint) AUTH_BROWSER_ENDPOINT="$(set_option_once "$name" "$AUTH_BROWSER_ENDPOINT" "$value")" ;;
                esac
                ;;
            *)
                say "Unknown argument: $arg"
                say "Run $SCRIPT_NAME --help for usage."
                exit "$EXIT_USAGE"
                ;;
        esac
    done
}

# prompt_value LABEL DEFAULT -> prints the entered value, or DEFAULT if blank.
prompt_value() {
    local label="$1" default="$2" answer=""
    if [ -n "$default" ]; then
        printf '%s [%s]: ' "$label" "$default" >&2
    else
        printf '%s: ' "$label" >&2
    fi
    if ! IFS= read -r answer; then
        say ""
        die "$EXIT_CANCELLED" "Input ended before $label was provided. Nothing was changed."
    fi
    if [ -z "$answer" ]; then
        answer="$default"
    fi
    if [ -z "$answer" ]; then
        die "$EXIT_USAGE" "$label is required."
    fi
    printf '%s' "$answer"
}

# prompt_optional LABEL -> prints the entered value; blank means "none".
prompt_optional() {
    local label="$1" answer=""
    printf '%s [none]: ' "$label" >&2
    if ! IFS= read -r answer; then
        say ""
        die "$EXIT_CANCELLED" "Input ended before $label was provided. Nothing was changed."
    fi
    printf '%s' "$answer"
}

# other_prep_table -> tells the operator what will be asked for, what each
# value is used for, and where to find it, before the prompts start.
other_prep_table() {
    say "Target: other deployment. You will be asked for:"
    info "Auth issuer URL         - Auth discovery, admin API, tokens."
    info "                          The issuer the Auth server advertises (ends in /auth)."
    info "Auth browser endpoint   - optional; only if your browser reaches Auth on a"
    info "                          different URL than this machine does (Enter = same)."
    info "Registry GraphQL URL    - team and client association calls (.../api/graphql)."
    info "Client ID               - seeded USER_PKCE client; Enter keeps $DEFAULT_CLIENT_ID."
    info "Team name               - admin team to create; Enter keeps $DEFAULT_TEAM_NAME."
    info "Callback URL            - where sign-in returns; must be a registered redirect;"
    info "                          Enter keeps $DEFAULT_REDIRECT_URL."
    info "Auth X-Api-Secret       - asked (hidden) after the URLs are checked, unless"
    info "                          --api-secret-file is given. It is the $API_SECRET_ENV_NAME"
    info "                          value the Auth server was started with."
    say ""
}

# choose_target -> sets TARGET. Explicit service URLs mean "other".
choose_target() {
    local answer=""
    if [ -n "$TARGET" ]; then
        return
    fi
    if [ -n "$AUTH_ISSUER" ] || [ -n "$REGISTRY_URL" ]; then
        TARGET="other"
        return
    fi
    say "Where is Registry running?"
    info "1) local docker preview stack (scripts/registry-stack)"
    info "2) other (cloud or any other deployment)"
    printf 'Target [1]: ' >&2
    if ! IFS= read -r answer; then
        say ""
        die "$EXIT_CANCELLED" "Input ended before the target was chosen. Nothing was changed."
    fi
    case "$answer" in
        ""|1|local) TARGET="local" ;;
        2|other) TARGET="other" ;;
        *) die "$EXIT_USAGE" "Target must be 1 (local) or 2 (other), not '$answer'." ;;
    esac
}

# apply_local_presets -> fills every unset value for the local stack, so the
# run needs no prompts. Flags given on the command line win.
apply_local_presets() {
    : "${AUTH_ISSUER:=$LOCAL_AUTH_ISSUER}"
    : "${AUTH_BROWSER_ENDPOINT:=$LOCAL_AUTH_BROWSER_ENDPOINT}"
    : "${REGISTRY_URL:=$LOCAL_REGISTRY_URL}"
    : "${CLIENT_ID:=$DEFAULT_CLIENT_ID}"
    : "${TEAM_NAME:=$DEFAULT_TEAM_NAME}"
    : "${REDIRECT_URL:=$DEFAULT_REDIRECT_URL}"
    say "Target: local docker preview stack"
    info "Auth issuer:      $AUTH_ISSUER"
    info "Auth in browser:  $AUTH_BROWSER_ENDPOINT"
    info "Registry GraphQL: $REGISTRY_URL"
    info "Client ID:        $CLIENT_ID    Team: $TEAM_NAME    Callback: $REDIRECT_URL"
}

collect_inputs() {
    choose_target
    if [ "$TARGET" = "local" ]; then
        apply_local_presets
        return
    fi
    if [ -z "$AUTH_ISSUER" ] || [ -z "$REGISTRY_URL" ] || [ -z "$CLIENT_ID" ] \
        || [ -z "$TEAM_NAME" ] || [ -z "$REDIRECT_URL" ]; then
        other_prep_table
    fi
    if [ -z "$AUTH_ISSUER" ]; then
        AUTH_ISSUER="$(prompt_value "Auth issuer URL" "")"
        if [ -z "$AUTH_BROWSER_ENDPOINT" ]; then
            AUTH_BROWSER_ENDPOINT="$(prompt_optional "Auth browser endpoint")"
        fi
    fi
    if [ -z "$REGISTRY_URL" ]; then
        REGISTRY_URL="$(prompt_value "Registry GraphQL URL" "")"
    fi
    if [ -z "$CLIENT_ID" ]; then
        CLIENT_ID="$(prompt_value "Client ID" "$DEFAULT_CLIENT_ID")"
    fi
    if [ -z "$TEAM_NAME" ]; then
        TEAM_NAME="$(prompt_value "Team name" "$DEFAULT_TEAM_NAME")"
    fi
    if [ -z "$REDIRECT_URL" ]; then
        REDIRECT_URL="$(prompt_value "Callback URL" "$DEFAULT_REDIRECT_URL")"
    fi
}

# --------------------------------------------------------------------------
# Validation
# --------------------------------------------------------------------------

# parse_url URL -> sets URL_SCHEME, URL_HOST, URL_PORT, URL_PATH. Returns 1 if
# the URL is malformed or has user info, a query, or a fragment.
parse_url() {
    local url="$1" authority
    URL_SCHEME=""
    URL_HOST=""
    URL_PORT=""
    URL_PATH=""
    if [[ ! "$url" =~ $URL_REGEX ]]; then
        return 1
    fi
    URL_SCHEME="${BASH_REMATCH[1]}"
    authority="${BASH_REMATCH[2]}"
    URL_PATH="${BASH_REMATCH[3]}"
    if [[ ! "$authority" =~ $HOSTPORT_REGEX ]]; then
        return 1
    fi
    URL_HOST="${BASH_REMATCH[1]}"
    URL_PORT="${BASH_REMATCH[3]}"
    if [ -z "$URL_HOST" ]; then
        return 1
    fi
    if [ -n "$URL_PORT" ] && { [ "$URL_PORT" -lt 1 ] || [ "$URL_PORT" -gt 65535 ]; }; then
        return 1
    fi
    return 0
}

is_loopback_host() {
    local host
    host="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
    case "$host" in
        localhost|127.0.0.1|'[::1]'|host.docker.internal) return 0 ;;
        *) return 1 ;;
    esac
}

validate_service_url() {
    local label="$1" url="$2"
    if ! parse_url "$url"; then
        die "$EXIT_USAGE" "$label is not a valid http(s) URL without user info, query, or fragment: $url"
    fi
    if [ "$URL_SCHEME" = "http" ] && ! is_loopback_host "$URL_HOST"; then
        die "$EXIT_USAGE" "$label must use https for remote hosts (http is allowed only for localhost, 127.0.0.1, or [::1]): $url"
    fi
}

validate_inputs() {
    local prefix

    case "$AUTH_ISSUER" in
        */) AUTH_ISSUER="${AUTH_ISSUER%/}" ;;
    esac
    validate_service_url "Auth issuer URL" "$AUTH_ISSUER"
    AUTH_ADMIN_BASE="$AUTH_ISSUER/v1"

    if [ -n "$AUTH_BROWSER_ENDPOINT" ]; then
        case "$AUTH_BROWSER_ENDPOINT" in
            */) AUTH_BROWSER_ENDPOINT="${AUTH_BROWSER_ENDPOINT%/}" ;;
        esac
        validate_service_url "Auth browser endpoint URL" "$AUTH_BROWSER_ENDPOINT"
    fi

    validate_service_url "Registry GraphQL URL" "$REGISTRY_URL"
    if [ -z "$URL_PATH" ] || [ "$URL_PATH" = "/" ]; then
        die "$EXIT_USAGE" "Registry GraphQL URL must include the endpoint path, e.g. /api/graphql: $REGISTRY_URL"
    fi

    if [ "${#CLIENT_ID}" -gt "$CLIENT_ID_MAX_LENGTH" ] || [[ ! "$CLIENT_ID" =~ $CLIENT_ID_REGEX ]]; then
        die "$EXIT_USAGE" "Client ID must start with a letter, contain only letters, digits, or hyphens, and be at most $CLIENT_ID_MAX_LENGTH characters: $CLIENT_ID"
    fi

    if [ "${#TEAM_NAME}" -lt "$TEAM_NAME_MIN_LENGTH" ] || [ "${#TEAM_NAME}" -gt "$TEAM_NAME_MAX_LENGTH" ] \
        || [[ ! "$TEAM_NAME" =~ $TEAM_NAME_REGEX ]]; then
        die "$EXIT_USAGE" "Team name must start with a letter, contain only letters, digits, '_' or '-', and be $TEAM_NAME_MIN_LENGTH-$TEAM_NAME_MAX_LENGTH characters: $TEAM_NAME"
    fi
    prefix="$(printf '%s' "${TEAM_NAME:0:${#TEAM_NAME_RESERVED_PREFIX}}" | tr '[:upper:]' '[:lower:]')"
    if [ "$prefix" = "$TEAM_NAME_RESERVED_PREFIX" ]; then
        die "$EXIT_USAGE" "Team name must not start with 'Personal' (reserved for personal teams): $TEAM_NAME"
    fi

    if ! parse_url "$REDIRECT_URL" || [ "$URL_SCHEME" != "http" ] || ! is_loopback_host "$URL_HOST" \
        || [ -z "$URL_PORT" ] || [ -z "$URL_PATH" ] || [ "$URL_PATH" = "/" ]; then
        die "$EXIT_USAGE" "Callback URL must be http on localhost, 127.0.0.1, or [::1] with an explicit port and path, e.g. $DEFAULT_REDIRECT_URL: $REDIRECT_URL"
    fi
    CALLBACK_HOST="$URL_HOST"
    CALLBACK_PORT="$URL_PORT"

    if [ -n "$API_SECRET_FILE" ]; then
        validate_secret_file "$API_SECRET_FILE"
    fi
}

# Only inspects the file; its contents are read in the credential stage.
validate_secret_file() {
    local file="$1" mode
    if [ ! -f "$file" ]; then
        die "$EXIT_USAGE" "API secret file does not exist or is not a regular file: $file"
    fi
    if [ ! -r "$file" ]; then
        die "$EXIT_USAGE" "API secret file is not readable: $file"
    fi
    if [ ! -s "$file" ]; then
        die "$EXIT_USAGE" "API secret file is empty: $file"
    fi
    mode="$(ls -ld -- "$file")"
    mode="${mode%% *}"
    case "${mode:4:6}" in
        ------) ;;
        *) die "$EXIT_USAGE" "API secret file must not be accessible by group or others (run: chmod 600 '$file'): $file" ;;
    esac
}

# --------------------------------------------------------------------------
# Platform and dependencies
# --------------------------------------------------------------------------

# os_release_value KEY FILE -> prints KEY's value from an os-release file
# without sourcing it (the file is data, not shell).
os_release_value() {
    local key="$1" file="$2" line value
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            "$key="*)
                value="${line#*=}"
                value="${value#[\"\']}"
                value="${value%[\"\']}"
                printf '%s' "$value" | tr '[:upper:]' '[:lower:]'
                return 0
                ;;
        esac
    done <"$file"
}

# detect_distro_family -> sets DISTRO_FAMILY from os-release ID and ID_LIKE.
detect_distro_family() {
    local file="" candidate ids id
    DISTRO_FAMILY="unknown"
    for candidate in "$OS_RELEASE_FILE" /usr/lib/os-release; do
        if [ -r "$candidate" ]; then
            file="$candidate"
            break
        fi
    done
    [ -n "$file" ] || return 0
    ids="$(os_release_value ID "$file") $(os_release_value ID_LIKE "$file")"
    for id in $ids; do
        case "$id" in
            debian|ubuntu) DISTRO_FAMILY="debian" ;;
            fedora|rhel|centos|rocky|almalinux|amzn|ol) DISTRO_FAMILY="fedora" ;;
            alpine) DISTRO_FAMILY="alpine" ;;
            suse|opensuse|opensuse-leap|opensuse-tumbleweed|sles) DISTRO_FAMILY="suse" ;;
            arch|manjaro) DISTRO_FAMILY="arch" ;;
            *) continue ;;
        esac
        return 0
    done
}

detect_platform() {
    local kernel
    kernel="$(uname -s 2>/dev/null || printf 'unknown')"
    case "$kernel" in
        Darwin) PLATFORM="macos" ;;
        Linux)
            PLATFORM="linux"
            if [ -r "$PROC_VERSION_FILE" ]; then
                case "$(tr '[:upper:]' '[:lower:]' <"$PROC_VERSION_FILE" 2>/dev/null || true)" in
                    *microsoft*) PLATFORM="wsl" ;;
                esac
            fi
            detect_distro_family
            ;;
        MINGW*|MSYS*|CYGWIN*)
            die "$EXIT_DEPENDENCY" "Native Windows shells are not supported. Run $SCRIPT_NAME from WSL."
            ;;
        *)
            die "$EXIT_DEPENDENCY" "Unsupported platform '$kernel'. Supported: macOS, Linux, Windows through WSL."
            ;;
    esac
}

install_hint() {
    local tool="$1"
    case "$PLATFORM:$tool" in
        macos:oauth2c) printf 'brew install cloudentity/tap/oauth2c' ;;
        macos:*) printf 'brew install %s' "$tool" ;;
        *:oauth2c)
            printf 'see %s (Linux install script, distro packages at %s, or: go install github.com/cloudentity/oauth2c@latest)' \
                "$OAUTH2C_INSTALL_DOCS" "$OAUTH2C_PACKAGES_URL"
            ;;
        *:*)
            case "$DISTRO_FAMILY" in
                debian) printf 'sudo apt-get install %s' "$tool" ;;
                fedora) printf 'sudo dnf install %s' "$tool" ;;
                alpine) printf 'sudo apk add %s' "$tool" ;;
                suse) printf 'sudo zypper install %s' "$tool" ;;
                arch) printf 'sudo pacman -S %s' "$tool" ;;
                *) printf "install '%s' with your distribution's package manager" "$tool" ;;
            esac
            ;;
    esac
}

# upgrade_hint TOOL
upgrade_hint() {
    case "$PLATFORM:$1" in
        macos:oauth2c) printf 'brew upgrade cloudentity/tap/oauth2c' ;;
        macos:*) printf 'brew upgrade %s' "$1" ;;
        *) install_hint "$1" ;;
    esac
}

# version_at_least VERSION MINIMUM -> succeeds when X.Y.Z VERSION >= MINIMUM.
version_at_least() {
    local IFS=. have want i
    read -r -a have <<<"$1"
    read -r -a want <<<"$2"
    for i in 0 1 2; do
        if [ "$((10#${have[$i]:-0}))" -gt "$((10#${want[$i]:-0}))" ]; then return 0; fi
        if [ "$((10#${have[$i]:-0}))" -lt "$((10#${want[$i]:-0}))" ]; then return 1; fi
    done
    return 0
}

# check_oauth2c_version -> proves the oauth2c on PATH runs and is new enough.
# Release builds print "oauth2c version X.Y.Z (...)"; source builds
# (go install) print "oauth2c version master" and can only be warned about.
check_oauth2c_version() {
    local output first version
    if ! output="$(oauth2c version </dev/null 2>&1)"; then
        say "Found oauth2c at $(command -v oauth2c), but 'oauth2c version' failed."
        info "oauth2c: $(install_hint oauth2c)"
        exit "$EXIT_DEPENDENCY"
    fi
    first="${output%%$'\n'*}"
    if [[ "$first" =~ ^oauth2c\ version\ v?([0-9]+\.[0-9]+\.[0-9]+) ]]; then
        version="${BASH_REMATCH[1]}"
        if ! version_at_least "$version" "$OAUTH2C_MIN_VERSION"; then
            say "oauth2c $version is older than the minimum supported $OAUTH2C_MIN_VERSION."
            info "oauth2c: $(upgrade_hint oauth2c)"
            exit "$EXIT_DEPENDENCY"
        fi
    elif [[ "$first" =~ ^oauth2c\ version\  ]]; then
        say "Warning: cannot confirm the oauth2c version ('$first'); $OAUTH2C_MIN_VERSION or later is required."
    else
        say "The oauth2c on PATH ($(command -v oauth2c)) did not identify itself as oauth2c."
        info "oauth2c: $(install_hint oauth2c)"
        exit "$EXIT_DEPENDENCY"
    fi
}

# check_jq_version -> proves the jq on PATH runs, is new enough, and decodes
# base64 the way the token claim check needs.
check_jq_version() {
    local output first version probe
    if ! output="$(jq --version </dev/null 2>&1)"; then
        say "Found jq at $(command -v jq), but 'jq --version' failed."
        info "jq: $(install_hint jq)"
        exit "$EXIT_DEPENDENCY"
    fi
    first="${output%%$'\n'*}"
    if [[ "$first" =~ ^jq-([0-9]+\.[0-9]+(\.[0-9]+)?) ]]; then
        version="${BASH_REMATCH[1]}"
        if ! version_at_least "$version" "$JQ_MIN_VERSION"; then
            say "jq $version is older than the minimum supported $JQ_MIN_VERSION."
            info "jq: $(upgrade_hint jq)"
            exit "$EXIT_DEPENDENCY"
        fi
    elif [[ "$first" =~ ^jq- ]]; then
        say "Warning: cannot confirm the jq version ('$(clean "$first")'); $JQ_MIN_VERSION or later is required."
    else
        say "The jq on PATH ($(command -v jq)) did not identify itself as jq."
        info "jq: $(install_hint jq)"
        exit "$EXIT_DEPENDENCY"
    fi
    probe="$(printf '{"a":"eA=="}' | jq -r '.a | @base64d' 2>/dev/null || true)"
    if [ "$probe" != "x" ]; then
        say "The jq on PATH ($(command -v jq)) failed a functional check (JSON with @base64d)."
        info "jq: $(upgrade_hint jq)"
        exit "$EXIT_DEPENDENCY"
    fi
}

check_dependencies() {
    local tool missing=""
    for tool in curl jq oauth2c; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            missing="$missing $tool"
        fi
    done
    if [ -n "$missing" ]; then
        say "Missing required tools:$missing"
        for tool in $missing; do
            info "$tool: $(install_hint "$tool")"
        done
        say "$SCRIPT_NAME never installs packages. Install the tools above and rerun."
        exit "$EXIT_DEPENDENCY"
    fi
    check_jq_version
    check_oauth2c_version
}

# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------

# transport_error OUTPUT -> sets HTTP_BODY to curl's error text without the
# status line that --write-out still appends on failure.
transport_error() {
    local text="${1%$'\n'*}"
    while [ "${text%$'\n'}" != "$text" ]; do
        text="${text%$'\n'}"
    done
    HTTP_BODY="$text"
}

# request_headers HAS_BODY WITH_SECRET WITH_TOKEN -> prints one request header
# per line. Uses only builtins, so credentials never become process arguments.
request_headers() {
    printf 'Accept: application/json\n'
    if [ "$1" = "1" ]; then
        printf 'Content-Type: application/json\n'
    fi
    if [ "$2" = "1" ]; then
        printf 'X-Api-Secret: %s\n' "$API_SECRET"
    fi
    if [ "$3" = "1" ]; then
        printf 'Authorization: Bearer %s\n' "$ACCESS_TOKEN"
    fi
}

# http_request METHOD URL [BODY] [WITH_SECRET] [WITH_TOKEN] -> sets HTTP_STATUS
# and HTTP_BODY.
# Headers reach curl through a process substitution (-H @file). Redirects are
# never followed, so credentials cannot leak to another origin.
# Returns 1 on transport failure (DNS, TLS, timeout, refused); HTTP_BODY then
# holds curl's error message.
http_request() {
    local method="$1" url="$2" body="${3-}" with_secret="${4-0}" with_token="${5-0}" proto output
    HTTP_STATUS=""
    HTTP_BODY=""
    if parse_url "$url" && [ "$URL_SCHEME" = "http" ]; then
        proto="=http"
    else
        proto="=https"
    fi
    if [ -n "$body" ]; then
        output="$(curl --silent --show-error --proto "$proto" --max-time "$HTTP_TIMEOUT_SECONDS" \
            --request "$method" --header @<(request_headers 1 "$with_secret" "$with_token") \
            --data-binary @- --write-out '\n%{http_code}' -- "$url" 2>&1 <<<"$body")" || {
            transport_error "$output"
            return 1
        }
    else
        output="$(curl --silent --show-error --proto "$proto" --max-time "$HTTP_TIMEOUT_SECONDS" \
            --request "$method" --header @<(request_headers 0 "$with_secret" "$with_token") \
            --write-out '\n%{http_code}' -- "$url" 2>&1 </dev/null)" || {
            transport_error "$output"
            return 1
        }
    fi
    HTTP_STATUS="${output##*$'\n'}"
    if [ "$HTTP_STATUS" = "$output" ]; then
        HTTP_BODY=""
    else
        HTTP_BODY="${output%$'\n'*}"
    fi
    return 0
}

# --------------------------------------------------------------------------
# Preflight checks
# --------------------------------------------------------------------------

check_discovery() {
    local url="$AUTH_ISSUER/.well-known/openid-configuration" issuer methods token_endpoint
    if ! http_request GET "$url"; then
        die "$EXIT_FAILURE" "Could not reach the Auth discovery endpoint $url: $HTTP_BODY"
    fi
    case "$HTTP_STATUS" in
        200) ;;
        3??) die "$EXIT_FAILURE" "Auth discovery returned a redirect ($HTTP_STATUS); redirects are not followed. Use the exact issuer URL." ;;
        *) die "$EXIT_FAILURE" "Auth discovery returned HTTP $HTTP_STATUS from $url." ;;
    esac
    if ! issuer="$(jq -er '.issuer | strings' <<<"$HTTP_BODY" 2>/dev/null)"; then
        die "$EXIT_FAILURE" "Auth discovery response from $url is not a valid OpenID configuration."
    fi
    if [ "$issuer" != "$AUTH_ISSUER" ]; then
        die "$EXIT_FAILURE" "Issuer mismatch: discovery reports '$issuer' but '$AUTH_ISSUER' was supplied. Use the exact issuer."
    fi
    if ! jq -e '(.authorization_endpoint | strings) and (.token_endpoint | strings)' <<<"$HTTP_BODY" >/dev/null 2>&1; then
        die "$EXIT_FAILURE" "Auth discovery is missing authorization_endpoint or token_endpoint."
    fi
    methods="$(jq -r 'if has("code_challenge_methods_supported") then (.code_challenge_methods_supported | join(",")) else "unlisted" end' <<<"$HTTP_BODY")"
    case ",$methods," in
        *,S256,*|,unlisted,) ;;
        *) die "$EXIT_FAILURE" "Auth server does not advertise PKCE S256 (code_challenge_methods_supported: $methods)." ;;
    esac
    # run_oauth2c derives the token endpoint from the issuer when the browser
    # endpoint is overridden, so the server must advertise exactly that URL.
    token_endpoint="$(jq -r '.token_endpoint' <<<"$HTTP_BODY")"
    if [ "$token_endpoint" != "$AUTH_ISSUER/oauth2/token" ]; then
        die "$EXIT_FAILURE" "Auth discovery advertises token_endpoint '$token_endpoint'; this script requires '$AUTH_ISSUER/oauth2/token'."
    fi
}

check_registry_reachable() {
    if ! http_request POST "$REGISTRY_URL" '{"query":"{__typename}"}'; then
        die "$EXIT_FAILURE" "Could not reach Registry at $REGISTRY_URL: $HTTP_BODY"
    fi
    case "$HTTP_STATUS" in
        200|401|403) ;;
        3??) die "$EXIT_FAILURE" "Registry returned a redirect ($HTTP_STATUS); redirects are not followed. Use the exact GraphQL URL." ;;
        *) die "$EXIT_FAILURE" "Registry returned HTTP $HTTP_STATUS from $REGISTRY_URL; expected the GraphQL endpoint." ;;
    esac
}

# A successful connection means something is already listening on the port.
port_in_use() {
    local host="$1" port="$2"
    (exec 3<>"/dev/tcp/$host/$port") 2>/dev/null
}

check_callback_port() {
    local host
    case "$CALLBACK_HOST" in
        '[::1]') host="::1" ;;
        *) host="127.0.0.1" ;;
    esac
    if port_in_use "$host" "$CALLBACK_PORT"; then
        die "$EXIT_FAILURE" "Callback port $CALLBACK_PORT on $CALLBACK_HOST is already in use. Free it or choose another --redirect-url; an existing client's callback is never changed automatically."
    fi
}

# --------------------------------------------------------------------------
# API secret
# --------------------------------------------------------------------------

# local_secret_from_env_file -> prints the REGISTRY_API_SECRET=value line's
# value from the registry-stack env-file, or nothing. Format is docker
# --env-file: KEY=value, no quotes, # comments.
local_secret_from_env_file() {
    local line
    [ -r "$LOCAL_SECRETS_FILE" ] || return 0
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            "$API_SECRET_ENV_NAME="*) printf '%s' "${line#*=}"; return 0 ;;
        esac
    done <"$LOCAL_SECRETS_FILE"
}

# find_local_secret -> sets LOCAL_SECRET_VALUE and LOCAL_SECRET_SOURCE when the
# local stack's secret can be found without asking: environment first, then
# the env-file registry-stack hands to the Auth container. Returns 1 if none.
# (Sets globals rather than printing so the source survives; a $(...) call
# would lose it to the subshell.)
find_local_secret() {
    LOCAL_SECRET_VALUE=""
    LOCAL_SECRET_SOURCE=""
    if [ -n "${REGISTRY_API_SECRET-}" ]; then
        LOCAL_SECRET_VALUE="$REGISTRY_API_SECRET"
        LOCAL_SECRET_SOURCE="environment variable $API_SECRET_ENV_NAME"
        return 0
    fi
    LOCAL_SECRET_VALUE="$(local_secret_from_env_file)"
    if [ -n "$LOCAL_SECRET_VALUE" ]; then
        LOCAL_SECRET_SOURCE="$LOCAL_SECRETS_FILE"
        return 0
    fi
    return 1
}
LOCAL_SECRET_VALUE=""
LOCAL_SECRET_SOURCE=""

local_secret_recommendations() {
    say "The Auth X-Api-Secret for the local stack was not found."
    info "Set it one of these ways, then rerun (or enter it now):"
    info "- add the line $API_SECRET_ENV_NAME=<shared-secret> to"
    info "  $LOCAL_SECRETS_FILE"
    info "  (gitignored; scripts/registry-stack passes it to the Auth container;"
    info "  see scripts/README.md, Service secrets), or"
    info "- export $API_SECRET_ENV_NAME=<shared-secret> in this shell."
    info "It must equal the value the Auth server was started with."
}

read_api_secret() {
    local secret=""
    if [ -n "$API_SECRET_FILE" ]; then
        IFS= read -r secret <"$API_SECRET_FILE" || true
    elif [ "$TARGET" = "local" ] && find_local_secret; then
        secret="$LOCAL_SECRET_VALUE"
        say "Auth API secret taken from $LOCAL_SECRET_SOURCE."
    else
        if [ "$TARGET" = "local" ]; then
            local_secret_recommendations
        fi
        say "The Auth API secret is sent only to $AUTH_ADMIN_BASE (discovery verified)."
        printf 'Auth API secret (input hidden): ' >&2
        if ! IFS= read -rs secret; then
            say ""
            die "$EXIT_CANCELLED" "Input ended before the API secret was provided. Nothing was changed."
        fi
        say ""
    fi
    secret="${secret%$'\r'}"
    if [ -z "$secret" ]; then
        die "$EXIT_USAGE" "The API secret is empty."
    fi
    if [[ "$secret" == *[[:cntrl:]]* ]]; then
        die "$EXIT_USAGE" "The API secret contains control characters."
    fi
    API_SECRET="$secret"
}

# --------------------------------------------------------------------------
# Auth client (stage 2)
# --------------------------------------------------------------------------

problem_reason() {
    jq -r 'if type == "object" then (.reason // empty) else empty end' <<<"$HTTP_BODY" 2>/dev/null || true
}

# describe_http_failure CONTEXT -> dies with a message for an unexpected status.
describe_http_failure() {
    local context="$1" reason
    reason="$(problem_reason)"
    case "$HTTP_STATUS" in
        403)
            die "$EXIT_FAILURE" "$context: Auth rejected the API secret (HTTP 403${reason:+ $reason}). Use the X-Api-Secret configured for this Auth deployment."
            ;;
        3??)
            die "$EXIT_FAILURE" "$context: Auth returned a redirect ($HTTP_STATUS); redirects are not followed. Check the issuer URL."
            ;;
        *)
            die "$EXIT_FAILURE" "$context: Auth returned HTTP $HTTP_STATUS${reason:+ ($reason)}."
            ;;
    esac
}

# fetch_auth_client CONTEXT -> sets FETCH_RESULT to "absent" or "present"
# (record in HTTP_BODY). Only 404 + CLIENT_NOT_FOUND means absent; any other
# failure is fatal.
fetch_auth_client() {
    local context="$1"
    if ! http_request GET "$AUTH_ADMIN_BASE/client/$CLIENT_ID" "" 1; then
        die "$EXIT_FAILURE" "$context: could not reach the Auth admin API: $HTTP_BODY"
    fi
    case "$HTTP_STATUS" in
        200)
            FETCH_RESULT="present"
            ;;
        404)
            if [ "$(problem_reason)" = "CLIENT_NOT_FOUND" ]; then
                FETCH_RESULT="absent"
            else
                die "$EXIT_FAILURE" "$context: Auth returned 404 without reason CLIENT_NOT_FOUND from $AUTH_ADMIN_BASE/client/$CLIENT_ID. Check the issuer URL."
            fi
            ;;
        *)
            describe_http_failure "$context"
            ;;
    esac
}

# assess_auth_client BODY -> returns 0 if the client is a compatible public
# PKCE client listing the exact callback (sets CLIENT_REDIRECTS); otherwise
# prints why to stderr and returns 1.
assess_auth_client() {
    local body="$1" problem
    if ! jq -e 'type == "object"' <<<"$body" >/dev/null 2>&1; then
        say "Auth returned a malformed client record."
        return 1
    fi
    problem="$(jq -r --arg id "$CLIENT_ID" --arg cb "$REDIRECT_URL" '
        if .clientId != $id then "it reports clientId \(.clientId | tostring), not \($id)"
        elif .type != "USER_PKCE" then "it is type \(.type | tostring), not USER_PKCE"
        elif .clientSecret != null then "it has a client secret, so it is not a public PKCE client"
        elif ((.redirectUrls // []) | type) != "array" then "its redirectUrls are malformed"
        elif ((.redirectUrls // []) | any(.[]; . == $cb)) | not then
            "it does not list the callback \($cb) (registered: \((.redirectUrls // []) | join(", ") | if . == "" then "none" else . end))"
        else "" end' <<<"$body")"
    if [ -n "$problem" ]; then
        say "Existing Auth client '$CLIENT_ID' is not compatible: $problem."
        return 1
    fi
    CLIENT_REDIRECTS="$(jq -r '.redirectUrls | join(", ")' <<<"$body")"
    CLIENT_REDIRECTS_JSON="$(jq -c '.redirectUrls' <<<"$body")"
    return 0
}

incompatible_client() {
    die "$EXIT_FAILURE" "Nothing was changed. This script never modifies a client: have an administrator correct '$CLIENT_ID' (the seeded client comes from registry-auth's bootstrap profile, db/seed), or choose another --client-id."
}

# The client is seeded by registry-auth's Flyway db/seed location, which is
# active only under the opt-in `bootstrap` profile; this script never creates one.
lookup_auth_client() {
    fetch_auth_client "Auth client lookup"
    if [ "$FETCH_RESULT" = "absent" ]; then
        die "$EXIT_FAILURE" "Auth has no client '$CLIENT_ID'. This script does not create clients; it expects the one seeded by registry-auth under its 'bootstrap' Spring profile (Flyway location classpath:db/seed, file V2.2026.10.20__seed_registry_user_pkce.sql). Start the Auth server once with SPRING_PROFILES_ACTIVE=<env>,bootstrap against this database, or pass --client-id for a client you created."
    fi
    if ! assess_auth_client "$HTTP_BODY"; then
        incompatible_client
    fi
    CLIENT_STATE="seeded"
    say "Auth client '$CLIENT_ID' found (USER_PKCE, no secret; redirects: $CLIENT_REDIRECTS)."
}

# --------------------------------------------------------------------------
# Sign-in and operator identity (stage 3)
# --------------------------------------------------------------------------

make_work_dir() {
    WORK_DIR="$(umask 077 && mktemp -d "${TMPDIR:-/tmp}/registry-bootstrap.XXXXXX")" \
        || die "$EXIT_FAILURE" "Could not create a private temporary directory."
}

# relay_login_output LOG -> shows oauth2c's stderr live with control sequences
# removed, and keeps the same sanitized lines in LOG for error classification.
# In --silent mode oauth2c's stderr carries only the sign-in URL and errors.
relay_login_output() {
    local log="$1" line
    while IFS= read -r line || [ -n "$line" ]; do
        line="$(clean "$line")"
        [ -n "$line" ] || continue
        printf '  %s\n' "$line" >&2
        printf '%s\n' "$line" >>"$log"
    done
}

# run_oauth2c LOG -> prints the token response (stdout only). --silent is
# required: every other oauth2c output mode writes the access token to stderr.
run_oauth2c() {
    local log="$1"
    local args=("$AUTH_ISSUER" --client-id "$CLIENT_ID" --response-types code --response-mode query
        --grant-type authorization_code --auth-method none --scopes openid,email --pkce
        --redirect-url "$REDIRECT_URL" --silent --no-browser)
    # oauth2c skips discovery once any endpoint is given explicitly, so the
    # token endpoint must be supplied too (check_discovery verified its value).
    if [ -n "$AUTH_BROWSER_ENDPOINT" ]; then
        args+=(--authorization-endpoint "$AUTH_BROWSER_ENDPOINT/oauth2/authorize"
            --token-endpoint "$AUTH_ISSUER/oauth2/token")
    fi
    { oauth2c "${args[@]}" 2>&1 1>&3 3>&- </dev/null | relay_login_output "$log"; exit "${PIPESTATUS[0]}"; } 3>&1
}

# login_failure LOG -> dies with an explanation of oauth2c's last ERROR line.
# oauth2c exits 1 for every failure, so the message text is all there is.
login_failure() {
    local log="$1" line message=""
    while IFS= read -r line; do
        case "$line" in
            *ERROR*) message="${line#*ERROR}" ;;
        esac
    done <"$log"
    message="${message#"${message%%[![:space:]]*}"}"
    case "$message" in
        *access_denied*)
            die "$EXIT_CANCELLED" "Sign-in was declined or cancelled in the browser ($message). Rerun $SCRIPT_NAME to continue."
            ;;
        Interrupted*)
            die "$EXIT_CANCELLED" "Sign-in was interrupted. Rerun $SCRIPT_NAME to continue."
            ;;
        timeout*)
            die "$EXIT_FAILURE" "Sign-in did not finish before the browser timeout. Rerun $SCRIPT_NAME to try again."
            ;;
        *"dial tcp"*|*"no such host"*|*"connection refused"*|*"tls:"*|*x509*)
            die "$EXIT_FAILURE" "oauth2c could not reach the Auth server: $message"
            ;;
        "")
            die "$EXIT_FAILURE" "Sign-in failed without an error message from oauth2c."
            ;;
        *)
            say "ERROR: Sign-in failed: $message"
            info "If you signed in successfully, Auth could not build your token. Check that:"
            info "- your directory account has an employeeId (first sign-in creates your Registry member),"
            info "- Auth can reach Registry and both use the same X-Api-Secret."
            info "Rerun $SCRIPT_NAME after fixing the cause."
            exit "$EXIT_FAILURE"
            ;;
    esac
}

# accept_token_response RESPONSE -> sets ACCESS_TOKEN and TOKEN_DEADLINE from a
# Bearer JWT response with expires_in; everything else in it is dropped.
accept_token_response() {
    local fields="" expires
    fields="$(printf '%s' "$1" | jq -r '
        if type == "object"
            and (.access_token | type) == "string"
            and (.access_token | test("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"))
            and ((.token_type // "") | ascii_downcase) == "bearer"
            and (.expires_in | type) == "number" and .expires_in > 0
        then "\(.expires_in | floor) \(.access_token)" else empty end' 2>/dev/null)" || fields=""
    if [ -z "$fields" ]; then
        die "$EXIT_FAILURE" "oauth2c returned an unexpected token response (expected a Bearer JWT with expires_in). Rerun $SCRIPT_NAME."
    fi
    expires="${fields%% *}"
    ACCESS_TOKEN="${fields#* }"
    TOKEN_DEADLINE=$(( $(date +%s) + expires ))
}

# token_claim NAME -> prints a string claim from the access token payload, or
# nothing. The payload is only read here; Registry verifies the signature.
token_claim() {
    printf '%s' "$ACCESS_TOKEN" | jq -Rr --arg name "$1" '
        def b64url: gsub("-"; "+") | gsub("_"; "/")
            | if length % 4 == 2 then . + "==" elif length % 4 == 3 then . + "=" else . end
            | @base64d;
        split(".")[1] | b64url | fromjson | .[$name] // empty | strings' 2>/dev/null || true
}

# require_fresh_token -> stops before a Registry call the token cannot finish.
require_fresh_token() {
    if [ $(( TOKEN_DEADLINE - $(date +%s) )) -lt "$TOKEN_EXPIRY_MARGIN_SECONDS" ]; then
        die "$EXIT_FAILURE" "Your sign-in has expired or is about to. Nothing more was changed; rerun $SCRIPT_NAME to sign in again and continue."
    fi
}

sign_in() {
    local log response="" rc=0
    log="$WORK_DIR/sign-in.log"
    : >"$log"
    say ""
    say "Signing in as the operator (scopes: openid,email)..."
    info "Open the URL below in a browser on this machine and sign in."
    if response="$(run_oauth2c "$log")"; then
        rc=0
    else
        rc=$?
    fi
    if [ "$rc" -ne 0 ]; then
        response=""
        login_failure "$log"
    fi
    accept_token_response "$response"
    response=""
    OPERATOR_EXTERNAL_ID="$(token_claim externalId)"
    if [ -z "$OPERATOR_EXTERNAL_ID" ]; then
        die "$EXIT_FAILURE" "Signed in, but the access token has no externalId claim, so Registry would treat you as a service client. Auth could not resolve your Registry member; check that your directory account has an employeeId and that Auth can reach Registry."
    fi
    say "Signed in."
}

graphql_reason() {
    jq -r 'if type == "object" and (.errors | type) == "array" then (.errors[0].extensions.reason // .errors[0].message // "UNKNOWN") else empty end' <<<"$HTTP_BODY" 2>/dev/null || true
}

# verify_operator -> resolves the signed-in operator through Registry with the
# operator's own token and asks them to confirm who they are.
verify_operator() {
    local query reason member answer=""
    require_fresh_token
    query="$(jq -nc --arg id "$OPERATOR_EXTERNAL_ID" \
        '{query: ("{ getMemberByExternalId(externalId: " + ($id | tojson) + ") { externalId externalAlias email name } }")}')"
    say "Looking up your Registry member..."
    if ! http_request POST "$REGISTRY_URL" "$query" 0 1; then
        die "$EXIT_FAILURE" "Could not reach Registry at $REGISTRY_URL: $HTTP_BODY"
    fi
    case "$HTTP_STATUS" in
        200) ;;
        401|403)
            die "$EXIT_FAILURE" "Registry rejected your access token (HTTP $HTTP_STATUS). Registry must trust this Auth issuer (jwt.sources). If the token expired, rerun $SCRIPT_NAME to sign in again."
            ;;
        *)
            die "$EXIT_FAILURE" "Registry returned HTTP $HTTP_STATUS to the member lookup."
            ;;
    esac
    reason="$(graphql_reason)"
    case "$reason" in
        "") ;;
        EXTERNAL_USER_NOT_FOUND|MEMBER_NOT_FOUND)
            die "$EXIT_FAILURE" "Registry has no member for your account ($reason). Your directory account needs an employeeId so Registry can create your member record."
            ;;
        *)
            die "$EXIT_FAILURE" "Registry refused the member lookup ($(clean "$reason"))."
            ;;
    esac
    if ! member="$(jq -er --arg id "$OPERATOR_EXTERNAL_ID" '
            .data.getMemberByExternalId
            | select(type == "object" and .externalId == $id)
            | [(.name // "-"), (.externalAlias // "-"), (.email // "-")] | join("\u001f")' <<<"$HTTP_BODY" 2>/dev/null)"; then
        die "$EXIT_FAILURE" "Registry returned a member that does not match your token's externalId. Nothing more was changed."
    fi
    say ""
    say "Signed in as:"
    info "Name:        $(clean "${member%%$'\037'*}")"
    member="${member#*$'\037'}"
    info "Alias:       $(clean "${member%%$'\037'*}")"
    info "Email:       $(clean "${member#*$'\037'}")"
    info "External ID: $(clean "$OPERATOR_EXTERNAL_ID")"
    printf 'Is this you, and should the bootstrap continue as this user? [y/N]: ' >&2
    if ! IFS= read -r answer; then
        answer=""
    fi
    case "$answer" in
        y|Y|yes|YES|Yes) ;;
        *)
            say "Cancelled. The Auth client and your Registry member record are kept; nothing else was changed."
            exit "$EXIT_CANCELLED"
            ;;
    esac
}

# --------------------------------------------------------------------------
# Team, client association, and report (stages 5-7)
# --------------------------------------------------------------------------

# halt MESSAGE -> stops after sign-in. Nothing is rolled back, so it lists what
# the server is known to hold and how to continue.
halt() {
    local kept="your Registry member record"
    if [ "$TEAM_STATE" = "owned" ]; then
        kept="$kept, team '$TEAM_NAME'"
    fi
    if [ "$ASSOC_STATE" = "ours" ]; then
        kept="$kept, the association of '$CLIENT_ID' with '$TEAM_NAME'"
    fi
    say "ERROR: $1"
    say "Kept on the server: $kept. Nothing is rolled back."
    say "Fix the cause and rerun $SCRIPT_NAME; it verifies what exists and continues."
    exit "$EXIT_FAILURE"
}

# reason_hint REASON -> extra guidance for Registry refusals the operator must
# resolve outside this script.
reason_hint() {
    case "$1" in
        ACCESS_DENIED|NOT_IN_*AUTHORIZED_TEAMS_LIST*)
            printf ' Registry allows this only while no configured authorized team exists, or for members of one. Check the deployment'"'"'s authorized-teams setting; this script does not change it.'
            ;;
        TEAMS_LIMIT_EXCEEDED)
            printf ' You belong to the maximum number of teams Registry allows.'
            ;;
        AUTH_CLIENT_DUPLICATE_FOUND)
            printf ' Another team already owns this client ID; choose another --client-id.'
            ;;
    esac
}

# registry_call LABEL BODY -> POSTs BODY to Registry with the operator's token.
# Returns 0 on HTTP 200, 1 on transport failure (HTTP_BODY holds the error),
# 2 on any other status. A rejected token stops the run.
registry_call() {
    require_fresh_token
    if ! http_request POST "$REGISTRY_URL" "$2" 0 1; then
        return 1
    fi
    case "$HTTP_STATUS" in
        200) return 0 ;;
        401|403)
            halt "Registry rejected your access token during the $1 (HTTP $HTTP_STATUS). If the token expired, rerun $SCRIPT_NAME to sign in again."
            ;;
    esac
    return 2
}

# registry_read LABEL BODY -> registry_call for reads: any failure stops the
# run, because a failed read never means "absent".
registry_read() {
    local rc=0
    registry_call "$1" "$2" || rc=$?
    case "$rc" in
        1) halt "Could not reach Registry for the $1: $(clean "$HTTP_BODY")" ;;
        2) halt "Registry returned HTTP $HTTP_STATUS to the $1." ;;
    esac
}

# registry_write LABEL BODY -> sends a mutation once (never retried) and sets
# WRITE_WHY to why its outcome is unclear, or "" when Registry confirmed it.
# The caller must re-read before deciding anything.
registry_write() {
    local rc=0 reason
    WRITE_WHY=""
    registry_call "$1" "$2" || rc=$?
    case "$rc" in
        1) WRITE_WHY="No response to the $1 ($(clean "$HTTP_BODY"))." ;;
        2) WRITE_WHY="Registry returned HTTP $HTTP_STATUS to the $1." ;;
        *)
            reason="$(graphql_reason)"
            if [ -n "$reason" ]; then
                WRITE_WHY="Registry refused the $1 ($(clean "$reason")).$(reason_hint "$reason")"
            elif ! jq -e '.data | type == "object"' <<<"$HTTP_BODY" >/dev/null 2>&1; then
                WRITE_WHY="Registry returned an unexpected response to the $1."
            fi
            ;;
    esac
}

# read_team -> sets TEAM_STATE to absent or owned. A team the operator does
# not own, or any unclear answer, stops the run.
read_team() {
    local query reason verdict
    query="$(jq -nc --arg n "$TEAM_NAME" \
        '{query: ("{ getTeamByName(name: " + ($n | tojson) + ") { name type members { externalId role } authClients { clientId clientVersion } } }")}')"
    registry_read "team lookup" "$query"
    reason="$(graphql_reason)"
    case "$reason" in
        "") ;;
        TEAM_NOT_FOUND)
            TEAM_STATE="absent"
            return 0
            ;;
        *)
            halt "Registry refused the team lookup ($(clean "$reason")).$(reason_hint "$reason")"
            ;;
    esac
    verdict="$(jq -r --arg n "$TEAM_NAME" --arg id "$OPERATOR_EXTERNAL_ID" '
        .data.getTeamByName
        | if type != "object" or (.name | type) != "string" or (.name | ascii_downcase) != ($n | ascii_downcase)
              or (.members | type) != "array" then "malformed"
          elif .type != "SHARED" then "type:" + (.type | tostring)
          else ([.members[] | select(.externalId == $id) | .role][0] // "none") as $role
              | if $role == "OWNER" then "owned" else "role:" + ($role | tostring) end
          end' <<<"$HTTP_BODY" 2>/dev/null)" || verdict="malformed"
    case "$verdict" in
        owned)
            TEAM_STATE="owned"
            ;;
        role:none)
            halt "Team '$TEAM_NAME' already exists and you are not a member. The script never adds you to a team you do not own; choose another --team-name or ask its owner."
            ;;
        role:*)
            halt "Team '$TEAM_NAME' already exists and your role is $(clean "${verdict#role:}"), not OWNER. Ask an owner to make you OWNER, or choose another --team-name."
            ;;
        type:*)
            halt "Team '$TEAM_NAME' exists but is a $(clean "${verdict#type:}") team, not SHARED. Choose another --team-name."
            ;;
        *)
            halt "Registry returned an unexpected response to the team lookup."
            ;;
    esac
}

create_team() {
    local mutation why
    mutation="$(jq -nc --arg n "$TEAM_NAME" --arg d "$RESOURCE_DESCRIPTION" \
        '{query: ("mutation { createTeam(team: {name: " + ($n | tojson) + ", type: SHARED, description: " + ($d | tojson) + "}) { name } }")}')"
    say "Creating team '$TEAM_NAME'..."
    registry_write "team creation" "$mutation"
    why="$WRITE_WHY"
    if [ -n "$why" ]; then
        say "$why Checking whether team '$TEAM_NAME' exists..."
    fi
    read_team
    if [ "$TEAM_STATE" != "owned" ]; then
        halt "${why:-Registry reported the team as created, but it cannot be found.} Team '$TEAM_NAME' was not created."
    fi
    if [ -n "$why" ]; then
        TEAM_OUTCOME="created (confirmed by lookup)"
    else
        TEAM_OUTCOME="created"
    fi
}

ensure_team() {
    say "Looking up team '$TEAM_NAME'..."
    read_team
    if [ "$TEAM_STATE" = "owned" ]; then
        TEAM_OUTCOME="reused"
        say "Team '$TEAM_NAME' exists and you are its OWNER; reusing it."
    else
        create_team
        say "Team '$TEAM_NAME' is ready; you are its OWNER."
    fi
}

# read_association -> sets ASSOC_STATE to absent or ours. Selects only fields
# Registry stores itself, so the answer never depends on Registry reaching
# Auth. Without clientVersion Registry returns the latest version, so anything
# but version 1 means extra versions exist. Another owner, extra versions, or
# an unclear answer stops the run.
read_association() {
    local query reason verdict
    query="$(jq -nc --arg c "$CLIENT_ID" \
        '{query: ("{ getAuthClient(clientId: " + ($c | tojson) + ") { clientId clientVersion teams { name } } }")}')"
    registry_read "client lookup" "$query"
    reason="$(graphql_reason)"
    case "$reason" in
        "") ;;
        AUTH_CLIENT_NOT_FOUND)
            ASSOC_STATE="absent"
            return 0
            ;;
        *)
            halt "Registry refused the client lookup ($(clean "$reason"))."
            ;;
    esac
    verdict="$(jq -r --arg c "$CLIENT_ID" --arg t "$TEAM_NAME" '
        .data.getAuthClient
        | if type != "object" or .clientId != $c or (.clientVersion | type) != "number"
              or (.teams | type) != "array" then "malformed"
          elif (.teams | length) == 0 then "orphan"
          elif any(.teams[]; (.name | ascii_downcase) == ($t | ascii_downcase)) | not
              then "other:" + ([.teams[].name | tostring] | join(", "))
          elif .clientVersion != 1 then "version:" + (.clientVersion | tostring)
          else "ours" end' <<<"$HTTP_BODY" 2>/dev/null)" || verdict="malformed"
    case "$verdict" in
        ours)
            ASSOC_STATE="ours"
            ;;
        orphan)
            halt "Registry has client '$CLIENT_ID' without an owning team. Inspect it in Registry before rerunning."
            ;;
        other:*)
            halt "Client '$CLIENT_ID' is already associated with team $(clean "${verdict#other:}"), not '$TEAM_NAME'. The script never moves a client between teams; choose another --client-id or --team-name."
            ;;
        version:*)
            halt "Registry already holds version $(clean "${verdict#version:}") of client '$CLIENT_ID'; the bootstrap expects only version 1. Inspect the extra versions in Registry."
            ;;
        *)
            halt "Registry returned an unexpected response to the client lookup."
            ;;
    esac
}

# create_association -> one createAuthClient call. Registry creates another
# client version when the call is repeated, so it is never retried; an unclear
# outcome is settled by reading back.
create_association() {
    local mutation why
    # Registry keeps its own copy of the redirect list; mirror Auth's so the
    # two agree (the seeded client also lists the UI callback).
    mutation="$(jq -nc --arg t "$TEAM_NAME" --arg c "$CLIENT_ID" --arg d "$RESOURCE_DESCRIPTION" --argjson r "$CLIENT_REDIRECTS_JSON" \
        '{query: ("mutation { createAuthClient(authClient: {teamName: " + ($t | tojson) + ", clientId: " + ($c | tojson)
            + ", description: " + ($d | tojson) + ", type: USER_PKCE, redirectUrls: " + ($r | tojson) + "}) { clientId clientVersion } }")}')"
    say "Associating client '$CLIENT_ID' with team '$TEAM_NAME'..."
    registry_write "client association" "$mutation"
    why="$WRITE_WHY"
    if [ -n "$why" ]; then
        say "$why Checking whether the association exists..."
    fi
    read_association
    if [ "$ASSOC_STATE" != "ours" ]; then
        halt "${why:-Registry reported the association as created, but it cannot be found.} The association was not created."
    fi
    if [ -n "$why" ]; then
        ASSOC_OUTCOME="created (confirmed by lookup)"
    else
        ASSOC_OUTCOME="created"
    fi
}

ensure_association() {
    say "Looking up client '$CLIENT_ID' in Registry..."
    read_association
    if [ "$ASSOC_STATE" = "ours" ]; then
        ASSOC_OUTCOME="already present"
        say "Client '$CLIENT_ID' is already associated with team '$TEAM_NAME'; skipping."
    else
        create_association
        say "Client '$CLIENT_ID' is associated with team '$TEAM_NAME' (version 1)."
    fi
}

# verify_registration -> reads version 1 including fields Registry fetches from
# Auth, proving Registry can reach Auth for this client and sees the intended
# settings.
verify_registration() {
    local query reason verdict
    query="$(jq -nc --arg c "$CLIENT_ID" \
        '{query: ("{ getAuthClient(clientId: " + ($c | tojson) + ", clientVersion: 1) { clientId clientVersion type redirectUrls teams { name } } }")}')"
    say "Verifying the client's Auth settings through Registry..."
    registry_read "client verification" "$query"
    reason="$(graphql_reason)"
    if [ -n "$reason" ]; then
        halt "Registry could not read the Auth settings of client '$CLIENT_ID' ($(clean "$reason")). Registry must reach Auth, and both must come from the same 2.0.x release."
    fi
    verdict="$(jq -r --arg c "$CLIENT_ID" --arg r "$REDIRECT_URL" '
        .data.getAuthClient
        | if type != "object" or .clientId != $c or .clientVersion != 1 then "malformed"
          elif .type == null then "noauth"
          elif .type != "USER_PKCE" or ((.redirectUrls // []) | index([$r])) == null
              then "mismatch:" + (.type | tostring) + " " + ((.redirectUrls // []) | map(tostring) | join(","))
          else "ok" end' <<<"$HTTP_BODY" 2>/dev/null)" || verdict="malformed"
    case "$verdict" in
        ok) ;;
        noauth)
            halt "Registry returned no Auth settings for client '$CLIENT_ID'. Registry must reach Auth, and both must come from the same 2.0.x release."
            ;;
        mismatch:*)
            halt "Registry reports different Auth settings for client '$CLIENT_ID' (type and redirects: $(clean "${verdict#mismatch:}")); expected USER_PKCE with $REDIRECT_URL."
            ;;
        *)
            halt "Registry returned an unexpected response to the client verification."
            ;;
    esac
}

report() {
    say ""
    say "Bootstrap resources verified:"
    info "Auth client '$CLIENT_ID':  $CLIENT_STATE (USER_PKCE; redirects: $CLIENT_REDIRECTS)"
    info "Registry member:  you ($(clean "$OPERATOR_EXTERNAL_ID"))"
    info "Team '$TEAM_NAME':  $TEAM_OUTCOME; you are OWNER"
    info "Association:  $ASSOC_OUTCOME; version 1 owned by '$TEAM_NAME', Auth settings readable through Registry"
    say ""
    say "Still operator-managed (not verified by this script):"
    info "- Admin rights: owning '$TEAM_NAME' grants none by itself. Add it to the"
    info "  deployment's authorized/admin team settings if it should administer Registry."
    info "- Setup access: restore your normal access restrictions now that setup is done."
    say ""
    say "Resource bootstrap complete. Rerunning $SCRIPT_NAME is safe; it verifies and creates nothing new."
}

# --------------------------------------------------------------------------
# Confirmation
# --------------------------------------------------------------------------

confirm_targets() {
    local answer=""
    say ""
    say "Bootstrap targets"
    info "Platform:         $PLATFORM"
    info "Auth issuer:      $AUTH_ISSUER (discovery verified)"
    info "Auth admin API:   $AUTH_ADMIN_BASE"
    if [ -n "$AUTH_BROWSER_ENDPOINT" ]; then
        info "Auth in browser:  $AUTH_BROWSER_ENDPOINT/oauth2/authorize"
    fi
    info "Registry GraphQL: $REGISTRY_URL (reachable; not yet authenticated)"
    info "Client ID:        $CLIENT_ID (USER_PKCE)"
    info "Team name:        $TEAM_NAME"
    info "Callback URL:     $REDIRECT_URL"
    info "Scopes:           openid,email"
    say ""
    say "Planned actions (each is checked first and skipped if already correct):"
    info "1. Look up the seeded Auth client '$CLIENT_ID' (sends the Auth API secret to"
    info "   $AUTH_ADMIN_BASE only) and use it without changes (USER_PKCE, no secret)."
    info "2. Sign you in with a printed URL (no browser is opened). Your first sign-in creates your"
    info "   Registry member record and personal team from the directory."
    info "3. Create or verify team '$TEAM_NAME' with you as OWNER."
    info "4. Associate client '$CLIENT_ID' with team '$TEAM_NAME'."
    say ""
    say "Prerequisites you are responsible for:"
    info "- Restrict access to these services during setup; this script cannot."
    info "- Naming the team '$TEAM_NAME' does not grant admin rights; configure"
    info "  authorized/admin teams in the deployment yourself."
    info "- Run this only against a fresh deployment. On an existing one it"
    info "  would add an unneeded team."
    say ""
    if [ "$TARGET" = "local" ]; then
        say "Local stack: proceeding without confirmation."
        return
    fi
    printf 'Proceed? [y/N]: ' >&2
    if ! IFS= read -r answer; then
        answer=""
    fi
    case "$answer" in
        y|Y|yes|YES|Yes) ;;
        *)
            say "Cancelled. Nothing was changed."
            exit "$EXIT_CANCELLED"
            ;;
    esac
}

# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

main() {
    parse_args "$@"
    detect_platform
    check_dependencies
    collect_inputs
    validate_inputs
    say "Checking Auth discovery..."
    check_discovery
    say "Checking Registry reachability..."
    check_registry_reachable
    say "Checking callback port $CALLBACK_PORT..."
    check_callback_port
    confirm_targets
    read_api_secret
    say "Looking up Auth client '$CLIENT_ID'..."
    lookup_auth_client
    check_callback_port
    make_work_dir
    sign_in
    verify_operator
    say "Operator verified."
    say "Checking Registry for an existing client association..."
    read_association
    ensure_team
    ensure_association
    verify_registration
    report
}

main "$@"
