# Development

## Versions

| Component | Version | Notes |
|-----------|---------|-------|
| Paper | 1.21.11 | Target Minecraft version |
| Paper API | `1.21.11-R0.1-SNAPSHOT` | Compile-only dependency |
| Java | 21+ | Required by Paper 1.21.11 |
| Gradle | Wrapper (9.8.0) | No system Gradle required |
| Plugin package | `com.larpsmp.moneyevent` | Minimal foundation only |
| Auth | Offline (`online-mode=false`) | Allows cracked clients |

## Everyday workflow

```bash
./gradlew build
./scripts/dev-server.sh
```

Or just run the script; it builds first.

## Java resolution order

`scripts/dev-server.sh` picks Java in this order:

1. `$JAVA_HOME` (if present)
2. `/usr/lib/jvm/java-21-openjdk`
3. `/usr/lib/jvm/java-25-openjdk`
4. `tools/jdk-25` (project-local, gitignored)

Any Java **21 or newer** is accepted.

## Project layout

- Plugin source: `src/main/java/com/larpsmp/moneyevent/`
- Plugin metadata: `src/main/resources/plugin.yml`
- Build: `build.gradle.kts`, Gradle Wrapper
- Local Paper server: `dev-server/`
- Bootstrap script: `scripts/dev-server.sh`

## Clean shutdown

In the Paper console:

```text
stop
```

Do not kill the process unless the server is hung; a clean stop flushes worlds and configs.

## Connecting

With the server running, join from a **1.21.11** Minecraft Java client (official or cracked):

- Address: `localhost`
- Port: `25565` (default)

## Intentionally out of scope

No economy, teams, capture points, auctions, admin commands, databases, Docker, or other game mechanics in this foundation step.
