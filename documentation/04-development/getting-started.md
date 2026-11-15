# Getting started

Merged from root README bootstrap notes and former `docs/DEVELOPMENT.md`.

## Versions

| Component | Version | Notes |
|-----------|---------|-------|
| Paper | 1.21.11 | Target Minecraft version |
| Paper API | `1.21.11-R0.1-SNAPSHOT` | Compile-only |
| Java | 21+ | Required by Paper 1.21.11 |
| Gradle | Wrapper 9.8.0 | No system Gradle required |
| PostgreSQL | 16+ | Local via Docker Compose |
| Plugin package | `com.larpsmp.moneyevent` | |
| Server auth | Offline (`online-mode=false`) | Cracked clients reach the plugin gate |
| Plugin auth | Pre-join Paper Dialogs + Postgres | Blocks world join until signup/login |

## One-command setup (CachyOS / Arch)

```bash
./scripts/dev-server.sh
```

The script (idempotent):

1. Ensures project-root `.env` exists (copies from `.env.example` if needed)
2. Audits the machine (Java, Docker, curl, python, git, ports, Compose files)
3. Installs missing packages via `pacman` (may prompt for `sudo`)
4. Starts Postgres + pgAdmin with Docker Compose using `.env`
5. Syncs `dev-server/plugins/MoneyEvent/config.yml` from `.env`
6. Runs `./gradlew build` (compile + tests)
7. Downloads Paper 1.21.11 if needed and starts the server on `localhost:25565`

You still need a **Minecraft Java Edition 1.21.11** client.

| Service | Address | Credentials (`.env` defaults) |
|---------|---------|-------------------------------|
| Paper | `localhost:25565` | Sign up / Log in in-game |
| Postgres | `127.0.0.1:5433` | `larpsmp` / `larpsmp` |
| pgAdmin | http://localhost:5050 | `admin@larpsmp.dev` / `admin` |

Stop Paper with `stop` in the console. Docker services keep running until you `docker compose … down`.

## Secrets

```bash
cp .env.example .env
```

Priority: process environment → `.env` → `config.yml` fallbacks. See [Configuration and secrets](configuration-and-secrets.md).

Set `LARPSMP_RESEND_API_KEY` and `LARPSMP_RESEND_FROM_EMAIL` so verification email works.

## Manual pieces

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d
./gradlew build
```

JAR output: `build/libs/money-event-0.1.0.jar` (version may match `build.gradle.kts`).

## Java resolution order

`scripts/dev-server.sh` picks:

1. `$JAVA_HOME`
2. `/usr/lib/jvm/java-21-openjdk`
3. `/usr/lib/jvm/java-25-openjdk`
4. `tools/jdk-25` (project-local, gitignored)

Any Java **21+** is accepted.

## Docker notes

- Prefers an already working Docker (including rootless with `DOCKER_HOST`).
- Fresh machine: installs Docker and adds user to `docker` group; uses `sg docker` for same-session access.
- Postgres host port **5433**.

## Connect with Minecraft

1. Run `./scripts/dev-server.sh` and wait for the ready report.
2. Client 1.21.11 → Multiplayer → `localhost`.
3. **Sign up**, enter email verification code, later **Log in** on reconnect.

New accounts currently receive a **$200** wallet automatically.

## Local server files

Runtime under `dev-server/`. Tracked templates: `eula.txt.template`, `server.properties.template`. Worlds, logs, caches, Paper JARs, and plugin JARs are gitignored.

## Auth deep dive

See [Auth and identity](../03-architecture/auth-and-identity.md).
