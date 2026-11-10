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
