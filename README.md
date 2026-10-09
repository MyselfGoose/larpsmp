# LarpSMP Money Event

Private Minecraft Java Edition event project foundation.

This repository contains the **Paper plugin + local development server** with **PostgreSQL-backed pre-join authentication**, **account-linked wallets** (starting balance, `/balance`, `/pay`, admin money commands, and an always-on Wallet HUD), and **account-keyed player bodies** (inventory/location/vitals persist per LarpSMP account, not per Minecraft client name).

## One-command setup (CachyOS / Arch)

On a fresh machine (or after pulling this branch):

```bash
./scripts/dev-server.sh
```

That script:

1. Ensures project-root `.env` exists (copies from `.env.example` if needed)
2. Audits the machine (Java, Docker, curl, python, git, ports, Compose files)
3. Installs missing packages via `pacman` (may prompt for `sudo`)
4. Starts Postgres + pgAdmin with Docker Compose using `.env`
5. Syncs `dev-server/plugins/MoneyEvent/config.yml` from `.env`
6. Runs `./gradlew build` (compile + tests)
7. Downloads Paper 1.21.11 if needed and starts the server on `localhost:25565`

**Secrets:** put DB passwords, Resend keys, storage URLs, etc. in `.env` (gitignored). Share that file with coworkers, or start from the committed template:

```bash
cp .env.example .env
```

When it finishes the readiness report, join Minecraft and use **Sign up**. New accounts automatically receive a **$200** wallet. Set `LARPSMP_RESEND_API_KEY` and `LARPSMP_RESEND_FROM_EMAIL` in `.env` so the verification email can be delivered.

| Service | Address | Credentials (from `.env` defaults) |
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
4. Use **Sign up**, enter the email verification code, then later **Log in** on reconnect.

Offline mode is enabled so cracked clients can reach the auth dialog; the plugin still requires a real database-backed account.

## Local server files

Runtime files live under [`dev-server/`](dev-server/). Tracked templates:

- `dev-server/eula.txt.template`
- `dev-server/server.properties.template`

Generated worlds, logs, caches, Paper JARs, and plugin JARs are **not** tracked by Git.

## What Git ignores

Gradle output (`.gradle/`, `build/`), IDE metadata, OS junk, secrets, and development-server runtime data (worlds, logs, cache, libraries, plugins, Paper JAR, generated configs).

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for developer details and [docs/AUTH_AND_DATABASE.md](docs/AUTH_AND_DATABASE.md) for authentication/database setup.
