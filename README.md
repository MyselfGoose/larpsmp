# LarpSMP Money Event

Private Minecraft Java Edition event project foundation.

This repository currently contains the **Paper plugin + local development server** infrastructure with **PostgreSQL-backed pre-join authentication**. Game mechanics beyond auth are intentionally not implemented yet.

## Prerequisites

- **Java 21+** (required by Paper 1.21.11; Java 25 works)
  - Preferred on Arch/CachyOS: `sudo pacman -S --needed jdk21-openjdk`
  - Or use a JDK under `tools/jdk-25` (gitignored; used automatically by `scripts/dev-server.sh`)
- **Docker Engine + Compose** (Postgres + pgAdmin for accounts)
- Git
- curl / python3 (used by the dev-server bootstrap script)

## Build the plugin

```bash
./gradlew build
```

The plugin JAR is written to `build/libs/` (for example `money-event-0.1.0.jar`).

## Start local database

```bash
docker compose -f docker/docker-compose.yml up -d
```

- Postgres: `127.0.0.1:5433` (db/user/password: `larpsmp`)
- pgAdmin: http://localhost:5050 (`admin@larpsmp.dev` / `admin`)

Full setup, pgAdmin walkthrough, and troubleshooting: [docs/AUTH_AND_DATABASE.md](docs/AUTH_AND_DATABASE.md).

## Start the development server

```bash
./scripts/dev-server.sh
```

This script:

1. Resolves Java 21+
2. Builds the plugin with Gradle
3. Downloads the Paper 1.21.11 server JAR into `dev-server/` if needed
4. Accepts the Minecraft EULA for local development
5. Ensures offline mode (`online-mode=false`) for cracked/offline clients
6. Copies the plugin JAR into `dev-server/plugins/`
7. Starts Paper (`localhost:25565`)

Stop the server by typing `stop` in the console.

## Connect with Minecraft

1. Start Postgres + pgAdmin (see above).
2. Start a **Minecraft Java Edition 1.21.11** client (official or cracked launcher).
3. Multiplayer → Direct Connection → `localhost` (or `127.0.0.1`).
4. Before you enter the world, a **Login** dialog appears (configuration phase).

### Creating an account

There are **no seeded test users**. Use **Sign up** in the dialog with a username, email, and password. On success you are auto-logged in and enter the world. On reconnect, use **Log in** with the same credentials.

Account rows (Argon2id hashes + Minecraft UUID bindings) can be inspected in pgAdmin — see [docs/AUTH_AND_DATABASE.md](docs/AUTH_AND_DATABASE.md).

Auth settings: `plugins/MoneyEvent/config.yml` after first run (defaults in `src/main/resources/config.yml`).

Offline mode is enabled so cracked clients can reach the auth dialog; the plugin gate still requires a real database-backed login or signup.

## Local server files

Runtime files live under [`dev-server/`](dev-server/). Tracked templates:

- `dev-server/eula.txt.template`
- `dev-server/server.properties.template`

Generated worlds, logs, caches, Paper JARs, and plugin JARs are **not** tracked by Git.

## What Git ignores

Gradle output (`.gradle/`, `build/`), IDE metadata, OS junk, secrets, and development-server runtime data (worlds, logs, cache, libraries, plugins, Paper JAR, generated configs).

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for a short developer workflow and [docs/AUTH_AND_DATABASE.md](docs/AUTH_AND_DATABASE.md) for authentication/database setup.
