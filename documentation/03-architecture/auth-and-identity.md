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
- Signup creates a linked wallet with **$200** in the same DB transaction (**Implemented**; competition wants competitor-only grants — [known gaps](../05-status/known-gaps-and-debt.md)).
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

## Display names

After auth, tab list, display name, join/quit, death-message rewriting, `/pay` lookups, and wallet labels use the **LarpSMP username**. The plugin also rewrites the Paper **GameProfile** name. Client nicknames remain bind metadata.

**Paper limits:** some vanilla systems may briefly show the offline client name; advancements/packets can leak the original nickname in edge cases.

## pgAdmin browsing

Compose pre-registers the `larpsmp` server (`docker/pgadmin/servers.json`). Browse `Servers → larpsmp → Databases → larpsmp → Schemas → public → Tables`.

Inspect after signup: `accounts`, `auth_email_challenges`, `account_minecraft_identities`, `wallets`, `wallet_transactions`, `account_player_states`, and (on teams branch) `teams`, `team_memberships`, `competitor_tiers`, `schema_migrations`.

Migrations applied today: **001–006** (older auth doc said 001–004 only — **Superseded**).

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
