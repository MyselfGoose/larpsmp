# Persistence and schema

**Status:** Migrations **V001–V006 Implemented** on `feature/teams`.

Migrations live under `src/main/resources/db/migrations/` and run via `MigrationRunner` on plugin enable when auth/DB is available.

## Migration list

| Version | File | Creates / changes |
|---------|------|-------------------|
| 001 | `V001__create_accounts.sql` | `accounts`, `account_minecraft_identities` |
| 002 | `V002__email_verification.sql` | email verified columns; `auth_email_challenges` |
| 003 | `V003__wallets.sql` | `wallets`, `wallet_transactions`; backfill $200 |
| 004 | `V004__account_player_states.sql` | `account_player_states` |
| 005 | `V005__teams.sql` | `teams`, `team_memberships`; seed Blue/Red/Yellow |
| 006 | `V006__competitor_tiers.sql` | `competitor_tiers` |

Also: `schema_migrations` tracks applied versions.

## Table overview

| Table | Purpose |
|-------|---------|
| `accounts` | Username, email, Argon2id hash, verification flags, timestamps |
| `account_minecraft_identities` | UUID binding (unique Minecraft UUID → one account) |
| `auth_email_challenges` | Hashed one-time email codes |
| `wallets` | `account_id` PK, `balance >= 0` |
| `wallet_transactions` | Money ledger |
| `account_player_states` | Account-keyed body blobs + vitals |
| `teams` | Team definitions + captain |
| `team_memberships` | Roster links |
| `competitor_tiers` | Tier 1–4 per account |
| `schema_migrations` | Applied versions |

## Economy persistence expectations (future)

Beyond current wallets/ledger, planned durable state includes:

- Starting-balance-granted flag that distinguishes competitors (today: wallet created for every account)
- Reward remainder rotation index
- Kill prices and kill-money switches
- Team-total visibility flag
- Completed death / rescue / capture / auction payment IDs
- Offline notification durability (partially filesystem today)

**Confirmed intent:** economy data should save after every completed transaction — do not wait for twice-daily world backups.

## World backups (ops)

**Suggestion:** two world backups per day is acceptable; add automatic backups immediately before/after major events. Separate from Postgres backups.
