# Auth and identity

Merged from the former `docs/AUTH_AND_DATABASE.md`, verified against current code. Schema tables beyond wallets are also listed in [Persistence and schema](persistence-and-schema.md).

**Status:** Pre-join Postgres auth **Implemented**.

## Model

The plugin gates world join with **Paper Dialogs** during the configuration phase. Accounts live in **PostgreSQL**. Passwords are stored only as **Argon2id** hashes.

The **LarpSMP username/email + password** is the portable identity: after a successful login, the connecting Minecraft profile is **rebound** to that account so players can join from any machine.

Gameplay body state is stored **per account**, not in vanilla UUID `playerdata` as the source of truth. See [Player state](player-state.md).

## v1 defaults

- Sign up creates an **unverified** account, binds the connecting UUID, and emails a verification code (Resend).
- Player must enter the code before joining the world.
- Every reconnect requires explicit **Log in** (`auth.auto-login-bound-uuid: false`).
- Logging in on an unverified account re-opens verify-email (with resend).
- **Forgot password** supports password reset and username recovery via email codes.
- A Minecraft UUID links to at most one account; **successful login or signup moves that link**.
- Username and email must be unique; passwords may be shared.
- No seeded demo users — first account is created in-game via Sign up.
- Signup creates a linked wallet with **$0**; competitor **$200** is granted once when the account becomes a competitor.
- Requires `LARPSMP_RESEND_API_KEY` and `LARPSMP_RESEND_FROM_EMAIL` in `.env`.

## Local services

| Service | Address | Dev credentials |
|---------|---------|-----------------|
| Paper | `localhost:25565` | Sign up / Log in |
| Postgres | `127.0.0.1:5433` | `larpsmp` / `larpsmp` |
| pgAdmin | http://localhost:5050 | `admin@larpsmp.dev` / `admin` |

Host port **5433** avoids colliding with system Postgres on 5432. Inside Compose, the DB is `postgres:5432`.

## In-game flows

### Sign up

1. Configure Resend in `.env`.
2. Sign up with username, email, password (≥ 8 chars).
3. Enter 6-digit email code.
4. Enter world after verify.

### Log in / recovery

- Log in with username **or** email + password.
- Forgot password → change password or find username via email code.
- Concurrent login: **reject** the new connection if that account is already online (prevents item duplication).
- Login from a different Minecraft UUID **succeeds** and rebinds the profile (portable login).

### Expected rejections

| Action | Result |
|--------|--------|
| Existing username/email on signup | Dialog error |
| Resend unset | Email unavailable |
| Wrong password | Invalid credentials |
| Unverified login | Verify dialog |
| Back to main menu | Disconnect |
| Account already online | Rejected |

## Universal account identity

After successful configuration-phase auth, the **LarpSMP username** is the only name players and operators should use:

| Surface | Behavior |
|---------|----------|
| Tab list / display name / join / quit | Account username |
| Death messages | Launcher nickname rewritten to account username |
| `/pay`, `/balance`, `/larp …` | Account username |
| Vanilla commands (`/gamemode`, `/tp`, `/give`, …) | Account username (command-name index rebound) |
| Server log pre-auth line (`UUID of player …`) | May still show the launcher nickname once at connect |

Implementation (`playerstate`):

1. **`AccountIdentityBinder.bindConfigurationProfile`** — renames the connecting Paper profile during auth so `PlayerList` indexes the account username at world join.
2. **`AccountIdentityBinder.bindOnlinePlayer`** — applies `setPlayerProfile` + display/tab names after join (and again next tick so later join handlers cannot leave the launcher name authoritative).
3. **`ServerCommandNameIndex`** — re-keys Paper’s `playersByName` map (vanilla Brigadier targets). Access path: `CraftServer#getHandle()` → `PlayerList#playersByName`, value from `CraftPlayer#getHandle()` (`ServerPlayer`). Keys are lower-cased with `Locale.ROOT`. `Player#setPlayerProfile` alone does **not** update that map.

Boot warms the reflective accessors so logs show `Account command-name index ready (…)`. If discovery fails, profile/display rename still runs, but vanilla command targets may keep needing the launcher nickname until fixed.

Client / launcher nicknames remain bind metadata on `AccountSession.launcherName()` and in `account_minecraft_identities` (`MoneyJoinListener` keeps last-seen Minecraft name as the launcher nick, not the rebound account username).

**Residual Paper limits:** some advancement/packet edge cases can still briefly leak the offline client nickname. Pre-auth handshake logs (`UUID of player …`) are outside plugin control.

## pgAdmin browsing

Compose pre-registers the `larpsmp` server (`docker/pgadmin/servers.json`). Browse `Servers → larpsmp → Databases → larpsmp → Schemas → public → Tables`.

Inspect after signup: `accounts`, `auth_email_challenges`, `account_minecraft_identities`, `wallets`, `wallet_transactions`, `account_player_states`, and (on teams branch) `teams`, `team_memberships`, `competitor_tiers`, `schema_migrations`.

Migrations applied today: **001–015**.

## Reset local data

```bash
docker compose --env-file .env -f docker/docker-compose.yml down -v
docker compose --env-file .env -f docker/docker-compose.yml up -d
```

Restart Paper so migrations run; sign up again.

## Remote Postgres

Set `LARPSMP_JDBC_URL`, `LARPSMP_DB_USER`, `LARPSMP_DB_PASSWORD` in `.env`. Prefer SSL (`sslmode=require` / `verify-full`) in production. No cloud provisioning is included in-repo.

## Security notes

- Passwords never logged or stored plaintext.
- Email codes hashed at rest; plaintext only in outbound email.
- Forgot-password submit always shows a generic response (no account enumeration).
- Parameterized SQL.
- In-memory rate limits per connecting profile UUID.
- Email resend cooldown; challenges expire with max attempts.
- Uniqueness enforced in PostgreSQL.

## Troubleshooting (common)

| Symptom | Check |
|---------|-------|
| Plugin can’t connect | Compose health, JDBC URL, credentials |
| Migrations failed | Logs; DB CREATE privilege; wipe volumes if half-applied in dev |
| pgAdmin can’t reach DB | Host `postgres` from inside Compose |
| Port conflicts | Change ports in `.env` |
| Auth unavailable | DB init failed at enable — fix Postgres and restart |
