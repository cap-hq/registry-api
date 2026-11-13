# Registry Bruno collection

A [Bruno](https://www.usebruno.com/) collection covering every GraphQL, REST and OAuth
surface of **Registry**, **Registry Auth** and **Registry Rollup**, plus privileged
operations (**Registry Admin**) and a **Bootstrap** workflow for registering OAuth clients
on a fresh stack. Works in the Bruno desktop app and with the `bru` CLI.

For how this fits with `registry-stack` and `bootstrap.sh`, see [`../README.md`](../README.md).

## Setup

```bash
cp scripts/bruno/.env.example scripts/bruno/.env   # gitignored; holds all secrets/site values
```

`scripts/registry-stack` also requires this file before startup and checks that its
`REGISTRY_API_SECRET` matches the value in each module's `secrets.yml`. Keep it
non-empty and identical everywhere.

Fill in `.env` (every key is explained inline), then open `scripts/bruno` as a collection
in Bruno and pick an environment:

| Environment | Targets | Notes |
|---|---|---|
| **Local** | Registry `http://localhost:8100/api`, Auth `http://localhost:8200/auth`, Rollup `http://localhost:8150/rollup` | matches `scripts/registry-stack` |
| **Cloud** | `https://registry|auth|rollup.example.com/...` placeholders | edit the URLs in `environments/Cloud.bru` for your deployment |

Only non-secret defaults live in `environments/*.bru`; everything site-specific is read as
`{{process.env.NAME}}` from `.env`. Never put real IDs, hostnames or secrets in the
tracked `.bru` files.

### `.env` keys at a glance

| Key | Used by | Must match |
|---|---|---|
| `REGISTRY_API_SECRET` | `X-Api-Secret` header on `Registry Auth / Registered Clients`, `Bootstrap` (steps 1–2) | `REGISTRY_API_SECRET` the services were started with |
| `OAUTH_USER_SCOPES` | root OAuth login (PKCE) | scopes your user client is allowed to request |
| `OAUTH_CLIENT_SCOPES` | client-credentials token requests | scopes granted to the service client (may be empty) |
| `MEMBER_EXTERNAL_ID` / `_ALIAS` / `EMAIL` | member/team requests | your identity-provider object id / alias / email |
| `DEV_USER_EXTERNAL_ID` | optional `X-External-User-Id` header | dev bypass; not enabled on the `localdocker` stack |
| `MANAGER_EXTERNAL_ID`, `SLACK_CHANNEL_ID` | Lines of Business, Admin lookups | — |
| `BOOTSTRAP_CLIENT_ID`, `BOOTSTRAP_TEAM_NAME` | Bootstrap folders | what `bootstrap.sh` created (defaults `registry-user-pkce` / `registry-admin`) |
| `DEMO_CLIENT_*`, `DEMO_REDIRECT_URL` | `Bootstrap PKCE` steps 8/10, Auth client-credentials calls | the demo client step 8 registers (default `registry-user`, `USER`) |

## Authentication model

The collection root uses **OAuth2 Authorization Code + PKCE** against Registry Auth with the
seeded public client `registry-user-pkce` (`oauthPkceClientId`) and callback
`oauthCallbackUrl`. Click *Get Access Token* on the collection once; every request with
`auth: inherit` then sends that bearer token.

Exceptions:

| Folder | Auth | Why |
|---|---|---|
| `Registry Auth / Registered Clients` | `X-Api-Secret` header | Auth's client-management REST is secret-protected, not user-protected |
| `Bootstrap` (steps 1–2) | `X-Api-Secret` header | same endpoints; `Bootstrap PKCE` (3–10) inherits the user JWT |
| `Registry Auth / OAuth2 & OIDC / POST token - client credentials`, `introspect`, `revoke` | HTTP Basic `demoClientId:demoClientSecret` | token endpoint authenticates the client itself |
| `Registry Admin / REST` | inherit (user JWT) | these endpoints check `AdminTask` on the *user*; `X-Api-Secret` yields `404 EXTERNAL_USER_NOT_FOUND` |

Dev shortcut: if Registry is running with a profile that enables the `X-External-User-Id`
dev bypass (the `localdocker` stack does not), un-comment that header in `collection.bru`
(remove the `~`) to act as `DEV_USER_EXTERNAL_ID` without OAuth. Registry only — Auth and
Rollup still need a token.

## Folders

| Folder | Contents |
|---|---|
| `Registry/` | 65 GraphQL operations: Teams, Team Members, Members, Applications, Application Transfers, APIs, Auth Clients, Users, Change Events, Inputs, Lines of Business (reads), Diagnostics (health/schema) |
| `Registry Auth/` | **OAuth2 & OIDC** (discovery, JWKS, client-credentials token, introspect, revoke, userinfo); **Registered Clients** (9 REST ops under `/v1`); **Keys & Schema** (v1 JWKS, GraphQL SDL, health) |
| `Registry Rollup/` | `getTeamRollup`, `getApplicationRollup`, `getMemberRollup`, health |
| `Registry Admin/` | privileged ops grouped once: Registry GraphQL (LOB create/update/delete, external-id updates, change-event sync), Registry Auth GraphQL (client search, secret expirations, auth events, JWKS admin, partition manager), 16 Registry admin REST endpoints |
| `Bootstrap/`, `Bootstrap PKCE/` | numbered setup/verification workflow, see next section |

Every request carries `tests {}` asserting the HTTP status and, for GraphQL, that
`errors` is absent — so `bru run` doubles as a smoke test.

## Bootstrap workflow

Purpose: check a fresh stack, then read back what `bootstrap/bootstrap.sh` did — your member
record, the team it created, and the `USER_PKCE` client it registered through Registry — and
optionally register a demo client the same way. Set `BOOTSTRAP_CLIENT_ID` and
`BOOTSTRAP_TEAM_NAME` in `.env` if you changed the script's defaults.

Two folders because the auth differs: **`Bootstrap`** (1–2) talks to Registry Auth's
secret-protected REST with `X-Api-Secret`; **`Bootstrap PKCE`** (3–10) runs as *you* with the
collection's PKCE token — click *Get Access Token* on the collection first.

| Step | Folder | Request | What it proves / does |
|---|---|---|---|
| 1 | Bootstrap | `Preflight - Auth openid-configuration`, `Auth jwks`, `Registry health` | services reachable (Auth `/health` is not public, so JWKS is used) |
| 2 | Bootstrap | `Inspect seeded PKCE client` | seed migrations ran; `X-Api-Secret` correct |
| 3 | Bootstrap PKCE | `Get teams` | every team with members and clients — your personal team plus the bootstrap team after a fresh run |
| 4 | Bootstrap PKCE | `Get bootstrap team` | `BOOTSTRAP_TEAM_NAME`: you as OWNER, `BOOTSTRAP_CLIENT_ID` attached as `USER_PKCE` |
| 5 | Bootstrap PKCE | `Get auth clients in Registry` | every Registry-owned client and its team, including the bootstrap client the script registered |
| 6 | Bootstrap PKCE | `Verify client in Registry` | the bootstrap client's full Registry record |
| 7 | Bootstrap PKCE | `Get members` | your member record, created by the script's sign-in, with team roles |
| 8 | Bootstrap PKCE | `Create client (via Registry GraphQL)` | **optional** — registers `DEMO_CLIENT_ID` (`registry-user`, type `DEMO_CLIENT_TYPE`) on the bootstrap team, mirrored into Auth. Confidential types return `clientSecret` once — paste it into `DEMO_CLIENT_SECRET` |
| 10 | Bootstrap PKCE | `Cleanup - delete client (via Registry)` | **optional, destructive** — removes the demo client from Registry and, mirrored, from Auth. Never touches the bootstrap client |

Client types: `USER_PKCE` (public, browser/CLI, no secret), `USER` (confidential web app,
secret + redirect URLs), `NON_USER` (service, client-credentials only, no redirect URLs).

Clients are never created directly in Registry Auth from this collection; `bootstrap.sh` owns
that path. Lost a confidential client's secret? `Registry / Auth Clients /
resetAuthClientSecret` (or `Registry Auth / Registered Clients / GET client secret reset`) rotates
it and returns a new plaintext.

## CLI

```bash
cd scripts/bruno
bru run "Bootstrap" --env Local                       # steps 1–2 (X-Api-Secret only)
bru run "Bootstrap PKCE" --env Local                  # steps 3–10; needs a user token (see below)
bru run "Registry/Teams" --env Local
bru run "Registry Rollup/GET actuator health.bru" --env Local
bru run Registry --env Local --output /tmp/registry.json
```

`bru` reads `.env` from the collection directory automatically. Requests that need the
inherited OAuth token require a token obtained in the desktop app or `--env-var` overrides.

## Conventions

- One canonical request per API operation; privileged operations live only under
  `Registry Admin`. The Bootstrap folders are the deliberate exception (they wrap a few
  Auth/Registry calls with `BOOTSTRAP_*` / `DEMO_*` variables).
- GraphQL bodies are pretty-printed (one field per line) and use variables rather than
  inline IDs; demo names (`teamName`, `applicationName`, …) are plain environment vars you
  can edit per run.
- Registry blocks repeated `__type`/`fields` introspection as "bad faith" — introspect one
  type per request if you need to.
- Destructive requests (delete, transfer, sync, partition trigger) are standalone and
  never run as folder setup scripts.
