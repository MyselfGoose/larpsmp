# Persistence and schema

**Status:** Migrations **V001–V007 Implemented** (`V007` on `feature/economy-settlement`).

Migrations live under `src/main/resources/db/migrations/` and run via `MigrationRunner` on plugin enable when auth/DB is available.

## Migration list

| Version | File | Creates / changes |
|---------|------|-------------------|
| 001 | `V001__create_accounts.sql` | `accounts`, `account_minecraft_identities` |
| 002 | `V002__email_verification.sql` | email verified columns; `auth_email_challenges` |
| 003 | `V003__wallets.sql` | `wallets`, `wallet_transactions`; historical $200 backfill |
| 004 | `V004__account_player_states.sql` | `account_player_states` |
| 005 | `V005__teams.sql` | `teams`, `team_memberships`; seed Blue/Red/Yellow |
| 006 | `V006__competitor_tiers.sql` | `competitor_tiers` |
| 007 | `V007__economy_settlement.sql` | starting-balance flag, settings, kill money/prices, settlements, parent/child ledger cols |

Also: `schema_migrations` tracks applied versions.

## Table overview

| Table | Purpose |
|-------|---------|
| `accounts` | Username, email, Argon2id hash, verification flags, timestamps |
| `account_minecraft_identities` | UUID binding (unique Minecraft UUID → one account) |
| `auth_email_challenges` | Hashed one-time email codes |
| `wallets` | `account_id` PK, `balance >= 0`, `starting_balance_granted` |
| `wallet_transactions` | Money ledger (optional `parent_transaction_id`, external refs) |
| `economy_settings` | Kill-money global, team-totals visibility, reward rotation index |
| `economy_kill_prices` | Per-tier death prices (defaults 40/30/20/15) |
| `economy_kill_money_*` | Player overrides + event scopes/participants |
| `economy_settlements` | Idempotent death/rescue/capture settlement keys |
| `economy_admin_audit` | Kill-money / visibility / killreward admin toggles |
| `account_player_states` | Account-keyed body blobs + vitals |
| `teams` | Team definitions + captain |
| `team_memberships` | Roster links |
| `competitor_tiers` | Tier 1–4 per account |
| `schema_migrations` | Applied versions |

## Economy persistence notes

- Signup creates a **$0** wallet; competitor `$200` is granted once on first team assignment.
- Offline notification durability remains partially filesystem today.

**Confirmed intent:** economy data should save after every completed transaction — do not wait for twice-daily world backups.

## World backups (ops)

**Suggestion:** two world backups per day is acceptable; add automatic backups immediately before/after major events. Separate from Postgres backups.
