# Development

## Versions

| Component | Version | Notes |
|-----------|---------|-------|
| Paper | 1.21.11 | Target Minecraft version |
| Paper API | `1.21.11-R0.1-SNAPSHOT` | Compile-only dependency |
| Java | 21+ | Required by Paper 1.21.11 |
| Gradle | Wrapper (9.8.0) | No system Gradle required |
| PostgreSQL | 16+ | Local via Docker Compose |
| Plugin package | `com.larpsmp.moneyevent` | Auth + foundation |
| Server auth | Offline (`online-mode=false`) | Allows cracked clients to reach the plugin gate |
| Plugin auth | Pre-join Paper Dialogs + Postgres | Blocks world join until signup/login succeeds |

## Everyday workflow

```bash
docker compose -f docker/docker-compose.yml up -d
./gradlew build
./scripts/dev-server.sh
```

Or just run the script after the DB stack is up; it builds first.

## Java resolution order

`scripts/dev-server.sh` picks Java in this order:

1. `$JAVA_HOME` (if present)
2. `/usr/lib/jvm/java-21-openjdk`
3. `/usr/lib/jvm/java-25-openjdk`
4. `tools/jdk-25` (project-local, gitignored)

Any Java **21 or newer** is accepted.

## Project layout

- Plugin source: `src/main/java/com/larpsmp/moneyevent/`
- Auth package: `com.larpsmp.moneyevent.auth`
- DB helpers: `com.larpsmp.moneyevent.db`
- Migrations: `src/main/resources/db/migrations/`
- Plugin metadata: `src/main/resources/plugin.yml`
- Build: `build.gradle.kts`, Gradle Wrapper
- Local Paper server: `dev-server/`
- Docker stack: `docker/docker-compose.yml`
- Bootstrap script: `scripts/dev-server.sh`

## Authentication

See [AUTH_AND_DATABASE.md](AUTH_AND_DATABASE.md) for:

- Starting Postgres + pgAdmin
- Plugin JDBC configuration
- In-game signup/login testing
- Inspecting accounts in pgAdmin
- Resetting volumes and troubleshooting

Runtime libraries (PostgreSQL JDBC, HikariCP, BouncyCastle) are declared in `plugin.yml` `libraries:` so Paper downloads them at startup.

## Clean shutdown

In the Paper console:

```text
stop
```

Do not kill the process unless the server is hung; a clean stop flushes worlds and configs.

## Connecting

With Postgres and the server running, join from a **1.21.11** Minecraft Java client:

- Address: `localhost`
- Port: `25565` (default)

Use **Sign up** to create the first account (no seeded users). Use **Log in** on later connects.

Auth settings: `dev-server/plugins/MoneyEvent/config.yml` after first run (source defaults in `src/main/resources/config.yml`).

## Intentionally out of scope (this phase)

No economy, teams, capture points, auctions, admin commands, email verification, password-reset emails, OAuth, or separate auth microservice.
