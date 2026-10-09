# Authentication and Database

This plugin gates world join with Paper Dialogs during the configuration phase. Accounts live in **PostgreSQL**. Passwords are stored only as **Argon2id** hashes. The **LarpSMP username/email + password** is the portable identity: after a successful login, the connecting Minecraft profile is rebound to that account so players can join from any machine.

**v1 defaults**

- Sign up creates an **unverified** account, binds the connecting UUID, and emails a verification code (Resend).
- The player must enter that code in the auth dialog before they can join the world.
- Every reconnect still requires an explicit **Log in** (`auth.auto-login-bound-uuid: false`).
- Logging in with a correct password on an unverified account re-opens the verify-email dialog (with resend).
- **Forgot password** on the login dialog supports password reset and username recovery via email codes.
- A Minecraft UUID is linked to at most one account at a time; **successful login or signup moves that link** to the current account.
- Sign up requires a unique username and email only (passwords may be shared). Creating another account from the same Minecraft character reassigns that character to the new account; the previous account stays intact and can be reached again via **Log in**.
- No seeded/demo users. Create the first account in-game via Sign up.
- Signup also creates a linked wallet with starting balance **$200** (same DB transaction).
- Requires `LARPSMP_RESEND_API_KEY` and `LARPSMP_RESEND_FROM_EMAIL` in `.env`.

## Prerequisites

On **CachyOS / Arch**, run the one-command bootstrap (installs Java/Docker if needed, starts Compose, builds, launches Paper):

```bash
./scripts/dev-server.sh
```

You still need a Minecraft Java Edition **1.21.11** client.

If Docker is not available and you cannot use the bootstrap, any PostgreSQL 16+ instance works: set
`LARPSMP_JDBC_URL` / `LARPSMP_DB_USER` / `LARPSMP_DB_PASSWORD` in `.env` (or matching `auth.database` in
`config.yml`). The Compose file under `docker/` remains the supported zero-setup path.

## Secrets (`.env`)

All shared credentials live in the project-root **`.env`** file (gitignored). The committed template is
[`.env.example`](../.env.example).

```bash
cp .env.example .env   # coworker first-time setup, then paste shared values
```

| Variable | Used by |
|----------|---------|
| `LARPSMP_POSTGRES_*` / `LARPSMP_JDBC_URL` / `LARPSMP_DB_*` | Docker Postgres + plugin JDBC |
| `LARPSMP_PGADMIN_*` | pgAdmin login UI |
| `LARPSMP_RESEND_API_KEY` / `LARPSMP_RESEND_FROM_EMAIL` | Resend transactional email (signup verify + recovery) |
| `LARPSMP_EMAIL_CODE_PEPPER` | Optional pepper for hashing email codes |
| `LARPSMP_STORAGE_*` | Future remote storage API |

Resolution order in the plugin: process environment → `.env` file → `config.yml` fallbacks.

## 1. Start Postgres + pgAdmin

**Normal path:** `./scripts/dev-server.sh` starts the Compose stack automatically.

**Manual / debugging** from the repository root:

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d
```

Verify containers are healthy:

```bash
docker compose --env-file .env -f docker/docker-compose.yml ps
docker compose --env-file .env -f docker/docker-compose.yml exec postgres pg_isready -U larpsmp -d larpsmp
```

Expected local ports (defaults from `.env.example`):

| Service  | URL / address              | Credentials                          |
|----------|----------------------------|--------------------------------------|
| Postgres | `127.0.0.1:5433`           | db/user/password: `larpsmp`          |
| pgAdmin  | http://localhost:5050      | `admin@larpsmp.dev` / `admin`        |

> Host port **5433** is used so Docker Postgres does not collide with a system Postgres on 5432.
> Inside Docker (including from the pgAdmin container) the DB is still `postgres:5432`.

These credentials are **dev-only**. Do not reuse them in production.

## 2. Configure the plugin

Prefer editing **`.env`**. Non-secret dialog copy still lives in
[`src/main/resources/config.yml`](../src/main/resources/config.yml).

After the first server run, the live YAML is:

```text
dev-server/plugins/MoneyEvent/config.yml
```

(`./scripts/dev-server.sh` copies JDBC settings from `.env` into that file as well.)

Local Docker fallbacks in YAML (overridden by `.env`):

```yaml
auth:
  enabled: true
  database:
    jdbc-url: "jdbc:postgresql://127.0.0.1:5433/larpsmp"
    username: "larpsmp"
    password: "larpsmp"
    pool-size: 5
```

On plugin enable the server:

1. Opens a HikariCP pool
2. Runs classpath migrations under `db/migrations/`
3. Wires the dialog auth listener

If Postgres is unreachable, the plugin **does not** allow unauthenticated joins. Connecting players are disconnected with a clear message.

> If you already ran the server before this auth change, replace or merge `dev-server/plugins/MoneyEvent/config.yml` with the new defaults (or delete that file and restart so `saveDefaultConfig()` recreates it).

## 3. Start the Paper server and test auth in-game

1. From the repo root run:

   ```bash
   ./scripts/dev-server.sh
   ```

   This starts Docker services (if needed), builds/tests, and launches Paper.

2. Confirm the console shows something like:

   ```text
   PostgreSQL authentication ready (jdbc:postgresql://127.0.0.1:5433/larpsmp).
   Pre-join authentication enabled (database-backed).
   ```

3. Join with a **1.21.11** Minecraft client at `localhost:25565`.
4. You should see the **Login** dialog (configuration phase — you are not in the world yet).

### Sign up (creates the first account)

1. Ensure `.env` has a valid `LARPSMP_RESEND_API_KEY` and `LARPSMP_RESEND_FROM_EMAIL`.
2. Click **Sign up**.
3. Enter a new username, email, and password (password ≥ 8 characters).
4. Click **Create account**.
5. Check the inbox for a LarpSMP verification email and enter the 6-digit code.
6. Confirm you enter the world after a successful verify.
7. There are **no pre-created DB users** — this signup is how the first account is created.

### Log in after reconnect

1. Disconnect from the server.
2. Reconnect with the **same** Minecraft profile.
3. Use **Log in** with the same username **or** email + password.
4. Confirm world join succeeds (verified accounts only).

### Forgot password / username

1. On the login dialog click **Forgot password**.
2. Choose **Change password** or **Find username**.
3. Enter the account email → a code is sent when the email matches an account (UI always shows a generic “if an account exists…” message).
4. Enter the code.
5. Change password: set a new password, then log in. Find username: dialog shows the username.

### Expected rejections

| Action | Expected result |
|--------|-----------------|
| Sign up with an existing username | Dialog error: username taken |
| Sign up with an existing email | Dialog error: email taken |
| Sign up / recovery with Resend unset | Dialog error: email unavailable |
| Log in with wrong password | Dialog error: invalid credentials |
| Log in to unverified account | Verify-email dialog (not world join) |
| Wrong / expired verification code | Dialog error; resend available |
| **Back to main menu** | Disconnect with “Returned to the main menu.” |
| Log in to an account from a **different** Minecraft UUID than the one bound at signup | Rejected (account linked to another profile) |

## 4. Using pgAdmin to view data

The Compose stack pre-registers the `larpsmp` Postgres server inside pgAdmin
(see `docker/pgadmin/servers.json`). You normally only need to log in and browse.

1. Open http://localhost:5050
2. Log in with:
   - Email: `admin@larpsmp.dev`
   - Password: `admin`
3. In the left tree, expand:

   ```text
   Servers → larpsmp → Databases → larpsmp → Schemas → public → Tables
   ```

   If prompted for a database password when expanding `larpsmp`, enter `larpsmp`
   (and optionally save it).

4. Right-click **accounts** → **View/Edit Data** → **All Rows**.

   After a successful signup (before verify) you should see a row with:

   - `username` / `email` (lowercase-normalized)
   - `password_hash` starting with `$argon2id$…` (**never** plaintext)
   - `email_verified = false` and `email_verified_at` null until the code succeeds
   - `created_at` / `updated_at` populated; `last_login_at` set when email is verified / on later logins

   Also inspect **auth_email_challenges** for active/consumed code hashes (never plaintext codes).

5. Right-click **account_minecraft_identities** → **View/Edit Data** → **All Rows**.

   Confirm:

   - `minecraft_uuid` matches the connecting profile
   - `minecraft_name` is the last seen name (cosmetic)
   - `account_id` references the row in `accounts`

Also inspect **wallets** / **wallet_transactions** after signup — each account should have balance `200` and a `STARTING_BALANCE` ledger row.

Also inspect **schema_migrations** — you should see versions `001`, `002`, and `003` after the first plugin start with wallets.

### If the `larpsmp` server is missing

Register it manually:

- Right-click **Servers** → **Register** → **Server…**
- **General** → Name: `larpsmp`
- **Connection**:
  - Host: `postgres` (Compose service name — not `127.0.0.1`)
  - Port: `5432`
  - Maintenance DB: `larpsmp`
  - Username / password: `larpsmp` / `larpsmp`

## 5. Resetting local data

Wipe Postgres + pgAdmin volumes and start clean:

```bash
docker compose --env-file .env -f docker/docker-compose.yml down -v
docker compose --env-file .env -f docker/docker-compose.yml up -d
```

Then restart the Paper server so migrations run again. Sign up to create a fresh first account.

## 6. Pointing at a remote Postgres later

No cloud provisioning is included. Change values in **`.env`**:

```bash
LARPSMP_JDBC_URL=jdbc:postgresql://db.example.com:5432/larpsmp?sslmode=require
LARPSMP_DB_USER=larpsmp_app
LARPSMP_DB_PASSWORD=<secret>
LARPSMP_DB_POOL_SIZE=10
```

Common JDBC SSL options: `sslmode=require`, `sslmode=verify-full` (with trust store configuration as needed). Keep production passwords out of git (`.env` is gitignored; commit only `.env.example`).

## 7. Troubleshooting

| Symptom | What to check |
|---------|----------------|
| Plugin can’t connect to Postgres | `docker compose … ps`, `pg_isready`, firewall, wrong `LARPSMP_JDBC_URL` / `LARPSMP_DB_*` in `.env` |
| Migrations failed | Plugin logs for SQL errors; ensure the DB user can `CREATE TABLE`; wipe volumes if schema is half-applied during development |
| pgAdmin can’t reach DB | Host must be `postgres` from inside Compose (not `127.0.0.1`). From the host machine, use `127.0.0.1:5433` with `psql` instead |
| Port conflicts on 5433 / 5050 | Change `LARPSMP_POSTGRES_PORT` / `LARPSMP_PGADMIN_PORT` in `.env` and matching `LARPSMP_JDBC_URL` |
| Port conflicts if you want host 5432 | Stop system Postgres (`sudo systemctl stop postgresql`), set `LARPSMP_POSTGRES_PORT=5432` and update `LARPSMP_JDBC_URL` |
| Auth dialogs appear but signup fails | Check server logs (never plaintext passwords). Confirm migrations applied. Confirm unique username/email. Confirm password length ≥ 8 |
| “Authentication is unavailable” | Database init failed at plugin enable — fix Postgres and restart the server |

## Schema overview

| Table | Purpose |
|-------|---------|
| `accounts` | Username, email, Argon2id hash, email verification flags, timestamps |
| `account_minecraft_identities` | UUID binding (unique Minecraft UUID → one account) |
| `auth_email_challenges` | Hashed one-time email codes (signup / password reset / username recovery) |
| `wallets` | Account-linked balance (`account_id` PK, starting balance granted at signup) |
| `wallet_transactions` | Money ledger (transfers, admin give/take/set, starting balance) |
| `schema_migrations` | Applied migration versions |

## Security notes

- Passwords are never logged or stored in plaintext.
- Email verification codes are hashed at rest; plaintext codes exist only in the outbound email.
- Forgot-password email submit always shows a generic response (no account enumeration).
- All SQL uses parameterized statements.
- Failed login/signup/verify attempts are rate-limited in memory per connecting profile UUID.
- Email resend is cooldown-limited; challenges expire and have max attempts.
- Unique constraints are enforced in PostgreSQL, not only in Java.
