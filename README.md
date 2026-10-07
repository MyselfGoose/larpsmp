# LarpSMP Money Event

Private Minecraft Java Edition event project foundation.

This repository currently contains the **Paper plugin + local development server** infrastructure with **PostgreSQL-backed pre-join authentication**. Game mechanics beyond auth are intentionally not implemented yet.

## One-command setup (CachyOS / Arch)

On a fresh machine (or after pulling this branch):

```bash
./scripts/dev-server.sh
```

That script:

1. Audits the machine (Java, Docker, curl, python, git, ports, Compose files)
2. Installs missing packages via `pacman` (may prompt for `sudo`)
3. Starts Postgres + pgAdmin with Docker Compose
4. Syncs `dev-server/plugins/MoneyEvent/config.yml` to Docker JDBC defaults
5. Runs `./gradlew build` (compile + tests)
6. Downloads Paper 1.21.11 if needed and starts the server on `localhost:25565`

When it finishes the readiness report, join Minecraft and use **Sign up** (no seeded users).

| Service | Address | Credentials |
|---------|---------|-------------|
| Paper | `localhost:25565` | Sign up / Log in in-game |
| Postgres | `127.0.0.1:5433` | `larpsmp` / `larpsmp` |
| pgAdmin | http://localhost:5050 | `admin@larpsmp.dev` / `admin` |

Stop Paper by typing `stop` in the server console. Docker Postgres + pgAdmin keep running.

Full auth / pgAdmin guide: [docs/AUTH_AND_DATABASE.md](docs/AUTH_AND_DATABASE.md).

## Prerequisites (installed automatically on CachyOS/Arch)

The bootstrap script installs these if missing:

- **Java 21+** (`jdk21-openjdk`)
- **Docker Engine + Compose** (`docker`, `docker-compose`)
- **git**, **curl**, **python**

You still need a **Minecraft Java Edition 1.21.11** client (not installed by the script).

## Manual build only

```bash
./gradlew build
```

The plugin JAR is written to `build/libs/` (for example `money-event-0.1.0.jar`).

## Connect with Minecraft

1. Run `./scripts/dev-server.sh` and wait for the ready report.
2. Start a **Minecraft Java Edition 1.21.11** client.
3. Multiplayer → Direct Connection → `localhost`.
4. Use **Sign up**, then later **Log in** on reconnect.

Offline mode is enabled so cracked clients can reach the auth dialog; the plugin still requires a real database-backed account.

## Local server files

Runtime files live under [`dev-server/`](dev-server/). Tracked templates:

- `dev-server/eula.txt.template`
- `dev-server/server.properties.template`

Generated worlds, logs, caches, Paper JARs, and plugin JARs are **not** tracked by Git.

## What Git ignores

Gradle output (`.gradle/`, `build/`), IDE metadata, OS junk, secrets, and development-server runtime data (worlds, logs, cache, libraries, plugins, Paper JAR, generated configs).

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for developer details and [docs/AUTH_AND_DATABASE.md](docs/AUTH_AND_DATABASE.md) for authentication/database setup.
