# Development

## Versions

| Component | Version | Notes |
|-----------|---------|-------|
| Paper | 26.3 | Current supported Paper line |
| Paper API | `26.3.build.+` | Compile-only dependency |
| Java | 25 | Required by Paper 26.1+ |
| Gradle | Wrapper (9.8.0) | No system Gradle required |
| Plugin package | `com.larpsmp.moneyevent` | Minimal foundation only |

## Everyday workflow

```bash
./gradlew build
./scripts/dev-server.sh
```

Or just run the script; it builds first.

## Java resolution order

`scripts/dev-server.sh` picks Java in this order:

1. `$JAVA_HOME` (if it points at a Java 25 install)
2. `/usr/lib/jvm/java-25-openjdk`
3. `tools/jdk-25` (project-local, gitignored)

Install the system package when possible:

```bash
sudo pacman -S --needed jdk25-openjdk
```

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

With the server running, join from a matching Minecraft Java client:

- Address: `localhost`
- Port: `25565` (default)

## Intentionally out of scope

No economy, teams, capture points, auctions, admin commands, databases, Docker, or other game mechanics in this foundation step.
