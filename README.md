# LarpSMP Money Event

Private Minecraft Java Edition event project foundation.

This repository currently contains only the **Paper plugin + local development server** infrastructure. Game mechanics are intentionally not implemented yet.

## Prerequisites

- **Java 25** (required by Paper 26.3)
  - Preferred on Arch/CachyOS: `sudo pacman -S --needed jdk25-openjdk`
  - Or use a JDK 25 under `tools/jdk-25` (gitignored; used automatically by `scripts/dev-server.sh`)
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

1. Resolves Java 25
2. Builds the plugin with Gradle
3. Downloads the Paper 26.3 server JAR into `dev-server/` if needed
4. Accepts the Minecraft EULA for local development
5. Copies the plugin JAR into `dev-server/plugins/`
6. Starts Paper (`localhost:25565`)

Stop the server by typing `stop` in the console.

## Connect with Minecraft

1. Start a Minecraft Java Edition client matching Paper 26.3 / the current Minecraft release line.
2. Multiplayer → Direct Connection → `localhost` (or `127.0.0.1`).

## Local server files

Runtime files live under [`dev-server/`](dev-server/). Tracked templates:

- `dev-server/eula.txt.template`
- `dev-server/server.properties.template`

Generated worlds, logs, caches, Paper JARs, and plugin JARs are **not** tracked by Git.

## What Git ignores

Gradle output (`.gradle/`, `build/`), IDE metadata, OS junk, secrets, and development-server runtime data (worlds, logs, cache, libraries, plugins, Paper JAR, generated configs).

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for a short developer workflow.
