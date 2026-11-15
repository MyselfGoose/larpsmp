# Project layout

```text
LarpSMP/
  README.md                 # Short onboarding hub
  documentation/            # Canonical docs (this tree)
  docs/                     # Redirect stubs to documentation/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  .env.example              # Secrets template
  src/main/java/com/larpsmp/moneyevent/
    MoneyEventPlugin.java
    auth/
    command/
    config/
    db/
    display/
    email/
    notification/
    playerstate/
    team/
    wallet/
  src/main/resources/
    plugin.yml
    config.yml
    db/migrations/          # V001–V006
  src/test/java/…           # JUnit 5 tests
  docker/
    docker-compose.yml      # Postgres + pgAdmin
    pgadmin/
  scripts/dev-server.sh     # Bootstrap
  dev-server/               # Local Paper runtime (mostly gitignored)
```

## Important paths

| Path | Role |
|------|------|
| `src/main/java/.../wallet/MoneyService.java` | Money authority |
| `src/main/java/.../team/TeamService.java` | Roster authority |
| `src/main/java/.../auth/AuthService.java` | Signup/login/verify |
| `src/main/resources/db/migrations/` | Schema |
| `docker/docker-compose.yml` | Dev database stack |

Package responsibilities: [System overview](../03-architecture/system-overview.md).
