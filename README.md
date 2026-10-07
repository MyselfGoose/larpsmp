# LarpSMP Money Event

Private Minecraft Java Edition event project foundation.

This repository currently contains only the **Paper plugin + local development server** infrastructure. Game mechanics are intentionally not implemented yet.

## Prerequisites

- **Java 21+** (required by Paper 1.21.11; Java 25 works)
  - Preferred on Arch/CachyOS: `sudo pacman -S --needed jdk21-openjdk`
  - Or use a JDK under `tools/jdk-25` (gitignored; used automatically by `scripts/dev-server.sh`)
- Git
- curl / python3 (used by the dev-server bootstrap script)

## Build the plugin

```bash
./gradlew build
```

The plugin JAR is written to `build/libs/` (for example `money-event-0.1.0.jar`).

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

1. Start a **Minecraft Java Edition 1.21.11** client (official or cracked launcher).
2. Multiplayer → Direct Connection → `localhost` (or `127.0.0.1`).
3. Before you enter the world, a **Login** dialog appears (configuration phase). Join logs only appear after a successful login.

### Test login credentials

| Field | Value |
|-------|-------|
| Username | `test` |
| Email | `test@larpsmp.local` |
| Password | `test123` |

You can sign in with either the username or the email plus the password. The Sign up screen is UI-only in this phase (it validates fields, then explains that registration is not enabled yet).

Credentials and messages live in `plugins/MoneyEvent/config.yml` (defaults ship in the plugin jar).

Offline mode is enabled so cracked clients can reach the auth dialog; the plugin gate still requires the test login.

## Local server files

Runtime files live under [`dev-server/`](dev-server/). Tracked templates:

- `dev-server/eula.txt.template`
- `dev-server/server.properties.template`

Generated worlds, logs, caches, Paper JARs, and plugin JARs are **not** tracked by Git.

## What Git ignores

Gradle output (`.gradle/`, `build/`), IDE metadata, OS junk, secrets, and development-server runtime data (worlds, logs, cache, libraries, plugins, Paper JAR, generated configs).

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for a short developer workflow.
