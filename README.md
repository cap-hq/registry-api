# The Registry

The Registry is an application and team registry with an OAuth2 authorization
server and organizational rollup services. This monorepo contains the three
Java/Spring Boot services and tools for running and exercising them locally.

## What's inside?

### Services

- `registry`: the core GraphQL API for applications, teams, members, and auth clients
- `registry-auth`: the OAuth2 and OpenID Connect authorization server
- `registry-rollup`: team, application, and member rollups using Registry data and Microsoft Entra ID (Azure AD)

### Tools

- [Local-stack tooling](scripts/README.md): build and run the services in Docker
- [Bootstrap](bootstrap/README.md): guided setup for a fresh deployment
- [Bruno collection](scripts/bruno/README.md): GraphQL, REST, and OAuth requests for the services

## Development

### Prerequisites

- Java 25
- Docker for Testcontainers and the local stack
- The included Gradle wrapper; no separate Gradle installation is required

The [local-stack guide](scripts/README.md) lists additional tools and environment
settings needed for bootstrap and interactive sign-in.

### Microsoft Entra ID (Azure) app registration

Registry Auth federates user sign-in to Microsoft Entra ID. Every installation,
including a local one, needs an app registration in your own tenant. The values
below go into `registry-auth/src/main/resources/secrets.yml` (see the
[local-stack guide](scripts/README.md), step 1). Nothing in this repository ships
a tenant or client of its own.

In the Azure portal, under **Microsoft Entra ID → App registrations**, create or
open the application and collect:

**Overview**

| Portal value | `secrets.yml` key |
|---|---|
| Application (client) ID | `SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_ID` |
| Directory (tenant) ID | `SPRING_CLOUD_AZURE_PROFILE_TENANT_ID` |

**Certificates & secrets**

- Add a **New client secret**. Optional description pattern, which the services
  can use to log which secret is in use: `<env>-client-secret-<YYYYMMDD>`.
- Copy the secret **value** (shown once) → `SPRING_CLOUD_AZURE_CREDENTIAL_CLIENT_SECRET`.

**Authentication**

- Add the Web redirect URI `http://localhost:8200/auth/login/oauth2/code/azure-idp`.
  For a deployed instance, add that instance's
  `https://<host>/auth/login/oauth2/code/azure-idp` as well.

**API permissions** (Microsoft Graph)

| Permission | Why |
|---|---|
| `openid`, `profile`, `email` | sign-in and basic identity claims |
| `User.Read.All` | full profile properties, reports and managers for all users |
| `User.ReadBasic.All` | limited, non-sensitive profile subset for all users |
| `Group.Read.All` | all groups, their properties and memberships in the tenant |
| `GroupMember.Read.All` | basic group properties and memberships |
| `Application.Read.All` | applications and service principals; used to log which app secret is in use |

Grant admin consent for the tenant-wide permissions.

### Build and test

From the repository root:

```bash
./gradlew build
```

To run one module's tests:

```bash
./gradlew :registry:test
./gradlew :registry-auth:test
./gradlew :registry-rollup:test
```

### Local stack

Follow the environment setup in the [local-stack guide](scripts/README.md) before
starting the services:

```bash
scripts/registry-stack everything up
scripts/registry-stack status
```

Keep deployment-specific settings and credentials in local, untracked files.
See the [bootstrap guide](bootstrap/README.md) for initial setup after startup.

## Project information

- [License](LICENSE)
- [Maintainers](MAINTAINERS.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
