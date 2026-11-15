# Configuration and secrets

## Resolution order

1. Process environment (`LARPSMP_*`)
2. Project-root `.env`
3. `config.yml` fallbacks

## `.env` variables

From [`.env.example`](../../.env.example):

| Variable | Used by |
|----------|---------|
| `LARPSMP_POSTGRES_*` | Docker Postgres |
| `LARPSMP_JDBC_URL` / `LARPSMP_DB_*` | Plugin JDBC |
| `LARPSMP_PGADMIN_*` | pgAdmin UI |
| `LARPSMP_RESEND_API_KEY` / `LARPSMP_RESEND_FROM_EMAIL` | Email verify + recovery |
| `LARPSMP_EMAIL_CODE_PEPPER` | Optional code hashing pepper |
| `LARPSMP_STORAGE_*` | Future remote storage (unused) |

`.env` is gitignored. Share filled values privately with coworkers.

### Resend notes

- Verified domain sender can email any recipient.
- Sandbox `onboarding@resend.dev` can only email the Resend account owner’s address.

## `config.yml`

Source template: `src/main/resources/config.yml`. Live copy after first run: `dev-server/plugins/MoneyEvent/config.yml` (bootstrap syncs JDBC from `.env`).

Major sections:

- `player-state` — autosave seconds, default spawn
- `auth` — enabled flag, timeout, auto-login, database, signup limits, rate limits, email challenge settings, dialog message strings

Non-secret dialog copy stays in YAML; secrets stay in `.env`.

## Plugin libraries

Declared in `plugin.yml` `libraries:`:

- PostgreSQL JDBC 42.7.4
- HikariCP 6.2.1
- BouncyCastle 1.79
