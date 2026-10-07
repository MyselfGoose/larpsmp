# Authentication and Database

This plugin gates world join with Paper Dialogs during the configuration phase. Accounts live in **PostgreSQL**. Passwords are stored only as **Argon2id** hashes. Minecraft profile UUIDs are bound to accounts so offline-mode clients cannot freely switch identities onto another account.

**v1 defaults**

- Sign up creates an account, binds the connecting UUID, and **auto-logs the player in** (immediate world join).
- Every reconnect still requires an explicit **Log in** (`auth.auto-login-bound-uuid: false`).
- One Minecraft UUID ↔ one account. Mismatches are rejected.
- No seeded/demo users. Create the first account in-game via Sign up.

## Prerequisites

On **CachyOS / Arch**, run the one-command bootstrap (installs Java/Docker if needed, starts Compose, builds, launches Paper):

```bash
./scripts/dev-server.sh
```

You still need a Minecraft Java Edition **1.21.11** client.

If Docker is not available and you cannot use the bootstrap, any PostgreSQL 16+ instance works: create a database/user matching
`auth.database` in `config.yml` (or point the JDBC URL at your instance). The Compose file under
`docker/` remains the supported zero-setup path.

## 1. Start Postgres + pgAdmin

**Normal path:** `./scripts/dev-server.sh` starts the Compose stack automatically.

**Manual / debugging** from the repository root:

```bash
docker compose -f docker/docker-compose.yml up -d
```

Verify containers are healthy:

```bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml exec postgres pg_isready -U larpsmp -d larpsmp
```

Expected local ports:

| Service  | URL / address              | Credentials                          |
|----------|----------------------------|--------------------------------------|
| Postgres | `127.0.0.1:5433`           | db/user/password: `larpsmp`          |
| pgAdmin  | http://localhost:5050      | `admin@larpsmp.dev` / `admin`        |

> Host port **5433** is used so Docker Postgres does not collide with a system Postgres on 5432.
> Inside Docker (including from the pgAdmin container) the DB is still `postgres:5432`.

These credentials are **dev-only**. Do not reuse them in production.

## 2. Configure the plugin

Source defaults ship in [`src/main/resources/config.yml`](../src/main/resources/config.yml).

After the first server run, the live file is:

```text
dev-server/plugins/MoneyEvent/config.yml
```

Local Docker defaults:

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

1. Click **Sign up**.
2. Enter a new username, email, and password (password ≥ 8 characters).
3. Click **Create account**.
4. Confirm you enter the world (auto-login after signup).
5. There are **no pre-created DB users** — this signup is how the first account is created.

### Log in after reconnect

1. Disconnect from the server.
2. Reconnect with the **same** Minecraft profile.
3. Use **Log in** with the same username **or** email + password.
4. Confirm world join succeeds.

### Expected rejections

| Action | Expected result |
|--------|-----------------|
| Sign up with an existing username | Dialog error: username taken |
| Sign up with an existing email | Dialog error: email taken |
| Log in with wrong password | Dialog error: invalid credentials |
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

   After a successful signup you should see a row with:

   - `username` / `email` (lowercase-normalized)
   - `password_hash` starting with `$argon2id$…` (**never** plaintext)
   - `created_at` / `updated_at` / `last_login_at` populated

5. Right-click **account_minecraft_identities** → **View/Edit Data** → **All Rows**.

   Confirm:

   - `minecraft_uuid` matches the connecting profile
   - `minecraft_name` is the last seen name (cosmetic)
   - `account_id` references the row in `accounts`

Also inspect **schema_migrations** — you should see version `001` after the first plugin start.

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
docker compose -f docker/docker-compose.yml down -v
docker compose -f docker/docker-compose.yml up -d
```

Then restart the Paper server so migrations run again. Sign up to create a fresh first account.

## 6. Pointing at a remote Postgres later

No cloud provisioning is included. To use a remote database, change only plugin config (or inject secrets via your deploy process):

```yaml
auth:
  database:
    jdbc-url: "jdbc:postgresql://db.example.com:5432/larpsmp?sslmode=require"
    username: "larpsmp_app"
    password: "<secret>"
    pool-size: 10
```

Common JDBC SSL options: `sslmode=require`, `sslmode=verify-full` (with trust store configuration as needed). Keep production passwords out of git (`.env`, secrets files, and `credentials.*` are gitignored).

## 7. Troubleshooting

| Symptom | What to check |
|---------|----------------|
| Plugin can’t connect to Postgres | `docker compose … ps`, `pg_isready`, firewall, wrong `jdbc-url` / credentials in `config.yml` |
| Migrations failed | Plugin logs for SQL errors; ensure the DB user can `CREATE TABLE`; wipe volumes if schema is half-applied during development |
| pgAdmin can’t reach DB | Host must be `postgres` from inside Compose (not `127.0.0.1`). From the host machine, use `127.0.0.1:5433` with `psql` instead |
| Port conflicts on 5433 / 5050 | Change the left-hand ports in `docker/docker-compose.yml` and update `jdbc-url` |
| Port conflicts if you want host 5432 | Stop system Postgres (`sudo systemctl stop postgresql`), set Compose back to `5432:5432`, and update `jdbc-url` |
| Auth dialogs appear but signup fails | Check server logs (never plaintext passwords). Confirm migrations applied. Confirm unique username/email. Confirm password length ≥ 8 |
| “Authentication is unavailable” | Database init failed at plugin enable — fix Postgres and restart the server |

## Schema overview

| Table | Purpose |
|-------|---------|
| `accounts` | Username, email, Argon2id hash, timestamps |
| `account_minecraft_identities` | UUID binding (unique Minecraft UUID → one account) |
| `schema_migrations` | Applied migration versions |

## Security notes

- Passwords are never logged or stored in plaintext.
- All SQL uses parameterized statements.
- Failed login/signup attempts are rate-limited in memory per connecting profile UUID.
- Unique constraints are enforced in PostgreSQL, not only in Java.
