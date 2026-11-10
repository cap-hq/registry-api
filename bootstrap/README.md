# bootstrap

`bootstrap.sh` is a guided first-run script for a **new** Registry deployment. Run it
**once**, after the first start of a greenfield installation: it signs you in as the operator,
creates (or verifies) the admin team with you as OWNER, and associates the seeded PKCE client
with that team. It is not a routine administration tool; rerunning it only verifies what
exists. It never opens a browser: it prints the sign-in URL and uses `oauth2c` to complete
the PKCE flow on a loopback callback.

```bash
bootstrap/bootstrap.sh            # prompts for the target; Enter = local docker stack
bootstrap/bootstrap.sh --help     # all options, rules, exit codes
```

## Prerequisites

- bash 3.2+, `curl`, `jq` 1.6+, `oauth2c` 1.21.1+; macOS, Linux, or Windows via WSL.
- **Colima users:** `host.docker.internal` must resolve on the host for Auth discovery
  and the PKCE sign-in URL. Docker Desktop adds this automatically; with Colima:
  ```bash
  sudo sh -c 'echo "127.0.0.1 host.docker.internal" >> /etc/hosts'
  ```
- The Auth server must already hold the seeded client `registry-user-pkce` with the
  callback `http://localhost:9876/callback` registered. Both come from the Flyway seed
  migrations in `registry-auth/src/main/resources/db/seed/` (V2 client, V3 callback),
  which run only when the Auth server is started with the opt-in `bootstrap` Spring
  profile (`SPRING_PROFILES_ACTIVE=<env>,bootstrap`). Without that profile no client
  exists and the script stops; it does not create clients. `scripts/registry-stack`
  activates the profile for the local stack automatically.
- The Auth `X-Api-Secret` (the `REGISTRY_API_SECRET` value the Auth server was
  started with).

## Targets

| Target | How chosen | Behaviour |
|---|---|---|
| `local` | Enter at the prompt, or `--target local` | Presets issuer `http://host.docker.internal:8200/auth`, browser endpoint `http://localhost:8200/auth`, Registry `http://localhost:8100/api/graphql`; secret from `REGISTRY_API_SECRET`, else `registry-auth/src/main/resources/secrets.yml`, else a hidden prompt; runs without a confirmation step. |
| `other` | `2` at the prompt, `--target other`, or any `--auth-issuer`/`--registry-url` | Prints a table of what will be asked and why, prompts for each missing value, shows the summary, then asks `Proceed? [y/N]`. |

Remote services must use `https`; plain `http` is accepted only for loopback hosts and
`host.docker.internal`. The secret is never taken from the command line — use
`--api-secret-file` or the hidden prompt.

## Tests

```bash
bash bootstrap/tests/run-tests.sh
```

Pure bash, no framework. The script runs under an isolated `PATH` with stubbed `curl`,
`jq`, and `oauth2c` and a fake Registry/Auth state machine; expect roughly five minutes.
