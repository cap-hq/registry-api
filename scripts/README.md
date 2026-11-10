# scripts

Operator tooling for running and exercising the Registry platform locally.

| Item | What it is | Guide |
|---|---|---|
| `registry-stack` | Bash script that builds and runs Registry Auth, Registry and Registry Rollup in Docker against local Postgres | [below](#registry-stack) |
| `bruno/` | Bruno API collection covering every GraphQL, REST and OAuth surface of the three services, plus a Bootstrap workflow for registering OAuth clients | [bruno/README.md](bruno/README.md) |
| `../bootstrap/bootstrap.sh` | Guided first-run script: signs the operator in, creates the admin team, links the seeded PKCE client to it | [../bootstrap/README.md](../bootstrap/README.md) |

---

## Runbook: fresh local stack to a working web-app login

The three tools are meant to be used in this order. Each step says **what it writes**
and **what must match it later** — most local-login failures are one of these
correlations being out of sync.

### 0. Prerequisites

- Docker with BuildKit — Docker Desktop or Colima (see note below), bash 3.2+, `curl`, `jq`.
- [Bruno](https://www.usebruno.com/) desktop app or `bru` CLI (collection work).
- `oauth2c` ≥ 1.21.1 (only for `bootstrap.sh`).
- Optional: a web application (any OIDC relying party) you want to sign in with.

> **Colima users:** the Auth server's issuer URL (`http://host.docker.internal:8200/auth`)
> must resolve on the host machine for `bootstrap.sh` and the browser PKCE sign-in flow.
> Docker Desktop adds this automatically; with Colima add it once:
> ```bash
> sudo sh -c 'echo "127.0.0.1 host.docker.internal" >> /etc/hosts'
> ```

### 1. Service secrets — `<module>/src/main/resources/secrets.yml`

Each Java module may have an untracked `secrets.yml` (gitignored via `**/secrets.yml`).
`registry-stack` passes it to `docker run --env-file`, so it is a flat `KEY=value` file,
not YAML. The one key that everything else depends on:

```
REGISTRY_API_SECRET=<any string>
```

| Who reads it | As |
|---|---|
| Registry, Registry Auth, Registry Rollup | the value they accept in the `X-Api-Secret` header (`api-secret: ${REGISTRY_API_SECRET}`) |
| `bootstrap.sh` (`local` target) | `REGISTRY_API_SECRET` env, else `registry-auth/src/main/resources/secrets.yml`, else a hidden prompt |
| Bruno | `REGISTRY_API_SECRET` in `scripts/bruno/.env` |

**Correlation:** the same string must be in all three places. A mismatch shows up as
`401`/`403` on `Registry Auth / Registered Clients` and `Bootstrap` requests.

Registry Auth additionally needs the Azure AD federation credentials
(`SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_ID` / `_CLIENT_SECRET`, tenant id) in its
`secrets.yml`. These authenticate the *user* against Azure AD; they are unrelated to the
OAuth client secret in step 4.

### 2. Start the stack — `registry-stack`

```bash
scripts/registry-stack everything up     # infra + auth + registry + rollup
scripts/registry-stack status
```

Day to day, prefer `scripts/registry-stack services down|up` (or `app NAME down|up`): it
recreates service containers and **keeps the databases**. Only `infra down` and
`everything down` delete data, and both ask for confirmation.

Ports: Registry `8100` (`/api`), Registry Auth `8200` (`/auth`), Registry Rollup `8150`
(`/rollup`), Adminer `8999`, Mailpit `8025`. Debuggers: auth `5005`, registry
`5006`, rollup `5007`.

Services run with `SPRING_PROFILES_ACTIVE=local,localdocker`; registry-auth additionally gets
the opt-in `bootstrap` profile, under which its Flyway seed migrations create the public PKCE
client `registry-user-pkce` with callback `http://localhost:9876/callback` — that is the only
OAuth client that exists on a fresh stack. Without `bootstrap` the Auth database gets the
schema only.

**Databases from before the Flyway flatten (October 2026) must be recreated.** Their
`flyway_schema_history` lists the old per-feature migrations, which no longer exist, so the
services refuse to start ("applied migration not resolved locally"). Run
`scripts/registry-stack infra down` and then `up` for a fresh database; the same applies to
any other Postgres on `localhost:5432` used under the `local` profile.

### 3. Create the admin team — `bootstrap/bootstrap.sh`

```bash
bootstrap/bootstrap.sh            # Enter at the prompt = local target
```

Signs you in as `registry-user-pkce` (PKCE, loopback callback on `:9876`), creates the
admin team with you as `OWNER`, and associates the seeded client with that team. The
script only creates the team: it does not grant admin rights. `AdminTask` operations are
authorised by `app.admin.authorized-teams` in the Registry config, which is empty
(fail-closed) by default and on `local`; add the team name there (case-sensitive) and
restart the Registry if you need admin access. Details in
[`../bootstrap/README.md`](../bootstrap/README.md).

### 4. Register your web app's OAuth client — Bruno `Bootstrap` folder

A browser-facing web application is normally a **confidential** authorization-code
client; it does not exist on a fresh stack and `bootstrap.sh` creates only the
`USER_PKCE` client it needs. Clients are never created directly in Registry Auth from the
collection — they go through Registry, owned by the admin team, and Registry mirrors them
into Auth. Use the Bruno collection:

1. `cp scripts/bruno/.env.example scripts/bruno/.env` and fill in
   `BOOTSTRAP_TEAM_NAME` (the team from step 3), `BOOTSTRAP_CLIENT_ID`,
   `BOOTSTRAP_CLIENT_NAME`, `BOOTSTRAP_CLIENT_TYPE=USER`,
   `BOOTSTRAP_REDIRECT_URL=<your app's callback URL>` (e.g.
   `http://localhost:3000/api/auth/callback/<provider>`).
2. Select environment **Local**, run `Bootstrap / 1 Preflight *`, then `3`–`8` to read back
   what `bootstrap.sh` did (team, your member record, the PKCE client).
3. Sign in via the collection's OAuth (you are an `OWNER` of the team), then run
   `Bootstrap / 9 Create client (via Registry GraphQL)`. The response contains
   `clientSecret` **exactly once** — Auth stores only a BCrypt hash and can never show it
   again.
4. Re-run `6 Verify client in Registry` / `7 Verify client in Registry Auth` with
   `BOOTSTRAP_CLIENT_ID` set to the new client. For `USER` clients,
   `Registry Auth / OAuth2 & OIDC / POST token - client credentials` returning
   `400 unauthorized_client` is the *good* result (secret accepted; grant simply not
   allowed for a user client). `401 invalid_client` means the secret is wrong.

Lost the secret? `Registry / Auth Clients / resetAuthClientSecret` (or
`Registry Auth / Registered Clients / GET client secret reset`) rotates it and returns the
new plaintext.

### 5. Give your web app the secret

Configure the application with the client id and the `clientSecret` from step 4 (and its
own session key, if it has one). Secrets passed to containers via `--env-file`/`-e` are
fixed at `docker run`; **recreate the container after changing them**.

**Correlation:** the app's client secret == plaintext returned by Registry Auth for
`BOOTSTRAP_CLIENT_ID`. Every time the client is created or its secret reset — including
after `everything down`, which wipes the Auth database — the app must be updated and
restarted.

### 6. Log in

Sign in through your application. Flow: app → Registry Auth `/oauth2/authorize` →
Azure AD (federated user login) → back to Auth → code → app exchanges the code at
`/oauth2/token` using `client_id` + client secret → Auth enriches the token with claims
from Registry (the member/team created in step 3).

---

## Correlation map

| Value | Source of truth | Must equal |
|---|---|---|
| `REGISTRY_API_SECRET` | `*/src/main/resources/secrets.yml` of each service | `scripts/bruno/.env` `REGISTRY_API_SECRET`; `bootstrap.sh` secret |
| OAuth client id | Registry Auth DB (`oauth2_registered_client`), created via Bruno | your app's OIDC provider config; Bruno `BOOTSTRAP_CLIENT_ID` |
| OAuth client secret | returned once by Registry Auth on create/reset | your app's client-secret setting; Bruno `.env` `BOOTSTRAP_CLIENT_SECRET` (optional) |
| Redirect URL | registered on the client in Auth | callback URL the browser is actually redirected to |
| Issuer | Auth `issuer` (`http://host.docker.internal:8200/auth` locally) | your app's issuer/OIDC endpoint; Registry/Rollup JWT issuer config; `bootstrap.sh --auth-issuer` |
| Browser Auth URL | `http://localhost:8200/auth` | your app's browser-facing Auth URL; Bruno `authBrowserUrl` |
| Seeded PKCE client | Flyway seed `registry-user-pkce` + `http://localhost:9876/callback` | `bootstrap.sh`; Bruno root OAuth (`oauthPkceClientId`, `oauthCallbackUrl`) |

## Troubleshooting

| Symptom | Where to look | Usual cause |
|---|---|---|
| App callback error; Auth log `invalid_client … client_id` | `docker logs registry-auth` | Client not registered — run Bruno `Bootstrap / 9` |
| Auth log `invalid_client … client_secret` | same | app's client secret stale → step 5 (update **and** restart the app) |
| Auth log `invalid_grant`/`redirect_uri` | same | Registered redirect URL ≠ app callback → `Registry Auth / Registered Clients / PUT client` |
| Bruno admin calls `401` | `X-Api-Secret` | `scripts/bruno/.env` `REGISTRY_API_SECRET` ≠ service value |
| Registry admin REST `404 EXTERNAL_USER_NOT_FOUND` | — | Called with `X-Api-Secret`; needs a user JWT with `AdminTask` (log in via Bruno OAuth) |
| Client-credentials token `500` for a client created only in Auth | `registry-auth` log | Auth enriches claims from Registry; create service clients via `Bootstrap / 9` (Registry GraphQL) instead |
| Rollup `getTeamRollup` error `EnvironmentCredential` | `registry-rollup` log | No Azure credentials locally — expected |

Verify a client secret directly:

```bash
curl -s -u "<clientId>:<secret>" -d grant_type=client_credentials \
  http://localhost:8200/auth/oauth2/token
# 401 invalid_client     -> wrong secret
# 400 unauthorized_client -> secret OK (USER client can't use this grant)
```

---

## registry-stack

```
scripts/registry-stack                               interactive menu
scripts/registry-stack infra up|down [--yes]         Postgres ×2, Mailpit, Adminer
scripts/registry-stack app NAME up|down [--force]    NAME: registry|registry-auth|registry-rollup
scripts/registry-stack services up|down [--force]    all three services; databases untouched
scripts/registry-stack everything up|down [--force|--yes]   infra + services
scripts/registry-stack status
```

What keeps your data and what does not:

| Command | Removes | Databases |
|---|---|---|
| `app NAME down`, `services down` | service container(s) | **kept** — teams, members, OAuth clients, tokens survive |
| `infra down` | Postgres, Mailpit, Adminer, network | **deleted** (asks for confirmation; `--yes` skips) |
| `everything down` | services + infra | **deleted** (asks for confirmation; `--yes` skips) |

- Java apps are rebuilt (Gradle + `docker build`) only when their inputs changed since the
  last successful build (stamps in `.registry-stack/`). `--force` rebuilds regardless.
- After a database wipe, the seed migrations recreate only `registry-user-pkce`; redo
  steps 3–5.
- `app NAME down && up` recreates one container; use it after editing `secrets.yml`.
- All containers join the `cap-local` Docker network. Services reach each other by
  container name (Docker DNS). `host.docker.internal` is used only for the Auth issuer
  URL (embedded in tokens) and must resolve on the **host machine** — Docker Desktop
  does this automatically; Colima requires a one-time `/etc/hosts` entry (see below).

Environment variables (all optional):

| Var | Purpose |
|---|---|
| `REGISTRY_JAVA_CACERTS` | directory with a Java `cacerts` trusting a corporate proxy CA |
| `REGISTRY_IMAGE_PREFIX` | registry prefix for base images (default Docker Hub) |

Per-service inputs: `<module>/src/main/resources/secrets.yml` (`--env-file`, see step 1)
and `application-localdocker.yml`.
