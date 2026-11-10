# First-time setup checklist

A checklist for the **one-time** bring-up of a new (greenfield) Registry installation —
the steps around `bootstrap/bootstrap.sh`, not how to deploy to Kubernetes or any other
platform. Work through it top to bottom once; nothing here applies to an installation that
already has teams and clients.

## 1. Before the first start

- [ ] Empty PostgreSQL databases for `registry` and `registry-auth` (and `registry-rollup` if
      deployed). Flyway creates the schema on first start from each module's `V1` baseline.
- [ ] One shared `REGISTRY_API_SECRET` value, set identically on every service. It is the
      `X-Api-Secret` the services use to call each other and the credential `bootstrap.sh`
      uses to verify the seeded client. See `scripts/README.md` (Service secrets) and each module's `application.yml`.
- [ ] Azure AD app registration for sign-in (`SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_ID` /
      `_SECRET` and tenant on `registry-auth`; see `registry-auth/src/main/resources/application.yml`).
- [ ] The operator who will run the bootstrap has a directory account with an `employeeId`;
      their first sign-in is what creates their Registry member record.
- [ ] An **environment profile of your own**. The `dev` and `prod` profiles in this repository
      carry CVS-specific values (Cloud SQL URLs, IAM users, `authorized-teams` lists naming CVS
      teams). A new installation must not run on them; copy what you need into your own
      `application-<env>.yml`.

## 2. Profiles for the first start

- [ ] Start **`registry-auth`** with `SPRING_PROFILES_ACTIVE=<env>,bootstrap`.
      The `bootstrap` profile (`application-bootstrap.yml`) adds the Flyway location
      `classpath:db/seed`, which creates the public PKCE client `registry-user-pkce` (V2) with the
      bootstrap callback `http://localhost:9876/callback` (V3). Without it no OAuth client exists
      and `bootstrap.sh` stops.
    - Leaving `bootstrap` on after the first start is harmless: Flyway records V2/V3 once and the
      SQL is idempotent. Never add it to an installation that already manages its own clients.
    - `scripts/registry-stack` does this automatically for the local Docker stack.
- [ ] Start `registry` with `SPRING_PROFILES_ACTIVE=<env>`. Leave `authorized-teams` unset and
      `app.admin.authorized-teams`, `app.api.authorized-teams` and `app.api.admin.authorized-teams`
      **empty** for now; they are an optional tightening you can apply after the team exists
      (step 5).
- [ ] Start `registry-rollup` if you deploy it (no bootstrap-specific settings).

## 3. Verify the seed before running the script

```bash
curl -s -H "X-Api-Secret: $REGISTRY_API_SECRET" -H 'Accept: application/json' \
  https://<auth-host>/auth/v1/client/registry-user-pkce
```

Expect HTTP 200 with `"type":"USER_PKCE"`, `"clientSecret":null` and both redirect URLs
(`http://localhost:3000`, `http://localhost:9876/callback`). A 404 with
`"reason":"CLIENT_NOT_FOUND"` means `registry-auth` was not started with the `bootstrap` profile
against this database.

## 4. Run the bootstrap once

From a workstation with `bash`, `curl`, `jq` 1.6+ and `oauth2c` 1.21.1+ (see
[README.md](README.md) for prerequisites and all options):

```bash
bootstrap/bootstrap.sh --target other \
  --auth-issuer https://<auth-host>/auth \
  --registry-url https://<registry-host>/api/graphql \
  --api-secret-file ~/.config/registry/api-secret   # chmod 600; or omit for a hidden prompt
```

The script prints the planned actions and asks `Proceed? [y/N]`, then:

1. verifies the seeded client (never creates or modifies one);
2. prints a sign-in URL — open it in a browser on the same machine (nothing is opened for you);
   your first sign-in creates your Registry member record;
3. creates the team (default `registry-admin`, override with `--team-name`) with you as OWNER;
4. associates `registry-user-pkce` with that team;
5. prints a report of what was verified or created.

Exit code 0 and the final line `Resource bootstrap complete` mean the installation now has an
operator-owned team. Running the script a second time is safe and should report every resource
as reused/verified with nothing created — that is the expected outcome, not a reason to run it
again.

## 5. After the bootstrap

- [ ] Optionally restrict access: add your team to `authorized-teams` and/or
      `app.admin.authorized-teams` on `registry`. This only ever narrows who may use the API;
      the bootstrap does not need it and nothing grants admin rights by itself.
- [ ] Remove any setup-time access you opened (network rules, temporary accounts, the
      `--api-secret-file` on your workstation).
- [ ] Keep `REGISTRY_API_SECRET` out of source control and shell history.
