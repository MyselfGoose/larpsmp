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

On **CachyOS / Arch**, one command is enough:

```bash
./scripts/dev-server.sh
```

The bootstrap script (idempotent):

1. **Preflight** — loads project-root `.env` (creates from `.env.example` if missing), verifies repo layout and `pacman`
2. **Audit** — reports missing packages, Java, Docker, ports, Compose files
3. **Remediate** — `sudo pacman -S --needed …`, starts Docker, docker group via `sg` so re-login is not required
4. **Compose** — `docker compose --env-file .env -f docker/docker-compose.yml up -d`, waits for Postgres health
5. **Config** — seeds/repairs `dev-server/plugins/MoneyEvent/config.yml` from `.env` DB settings
6. **Build** — `./gradlew build` (fails the script if tests fail)
7. **Paper** — downloads Paper if needed, enforces offline-mode, deploys the plugin JAR, starts the server (inherits exported `.env`)

### Secrets (`.env`)

All important credentials live in **one file** at the repo root:

| File | Git | Purpose |
|------|-----|---------|
| `.env.example` | Committed | Template with every key |
| `.env` | Ignored | Real shared secrets (DB, pgAdmin, Resend, storage, …) |

```bash
cp .env.example .env   # first time / coworker onboarding
# edit .env, then share the filled file privately if needed
```

The plugin reads `LARPSMP_*` from the process environment first, then `.env`, then `config.yml` fallbacks.

Re-running on an already-setup machine skips installs and only ensures the stack is healthy before build + start.

### Manual pieces (debugging)

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d
./gradlew build
```

## Java resolution order

`scripts/dev-server.sh` picks Java in this order:

1. `$JAVA_HOME` (if present)
2. `/usr/lib/jvm/java-21-openjdk`
3. `/usr/lib/jvm/java-25-openjdk`
4. `tools/jdk-25` (project-local, gitignored)

Any Java **21 or newer** is accepted. If none exist, the script installs `jdk21-openjdk` via pacman.

## Docker notes

- Prefers an **already working** Docker (including rootless with `DOCKER_HOST`).
- On a fresh machine, installs system Docker and adds the user to the `docker` group.
- Same-session access uses `sg docker` so you do not need to log out immediately after group membership changes.
- Postgres is published on host port **5433** (avoids conflicts with a system Postgres on 5432).

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

See [AUTH_AND_DATABASE.md](AUTH_AND_DATABASE.md). The startup script starts Compose automatically; that doc covers pgAdmin browsing and troubleshooting.

Runtime libraries (PostgreSQL JDBC, HikariCP, BouncyCastle) are declared in `plugin.yml` `libraries:` so Paper downloads them at startup.

## Clean shutdown

In the Paper console:

```text
stop
```

Do not kill the process unless the server is hung; a clean stop flushes worlds and configs. Docker services remain up until you run `docker compose -f docker/docker-compose.yml down`.

## Connecting

With the bootstrap finished, join from a **1.21.11** Minecraft Java client:

- Address: `localhost`
- Port: `25565` (default)

Use **Sign up** to create the first account (no seeded users). Use **Log in** on later connects.

## Intentionally out of scope (this phase)

No economy, teams, capture points, auctions, admin commands, email verification, password-reset emails, OAuth, or separate auth microservice.
