# Persistence and schema

**Status:** Migrations **V001–V011 Implemented** (`V011` on `feature/auctions`).

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
| 008 | `V008__account_plugin_admins.sql` | account-based plugin admins |
| 009 | `V009__combat_kills.sql` | attributed kills ledger (`event_kill` for future `/top kills`) |
| 010 | `V010__capture_points.sql` | capture sessions, day completions, temp blocks, runtime pointer |
| 011 | `V011__auctions.sql` | auction sessions, representatives, lots, bids, runtime pointer |

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
| `account_plugin_admins` | LarpSMP-account plugin ops |
| `combat_kills` | Attributed deaths (`death_id`, cause, `event_kill`, settlement outcome) |
| `capture_sessions` | Active/historical capture activations (`capture_id`, progress, status) |
| `capture_day_completions` | Points already captured for a UTC day key |
| `capture_temp_blocks` | Temporary platform blocks with prior block data + expiry |
| `capture_runtime` | Singleton active `capture_id` + paused flag |
| `auction_sessions` | Auction event runs (`SETUP` / `ACTIVE` / `CLOSED`) |
| `auction_representatives` | Fixed per-team reps for a session |
| `auction_lots` | Lot theme, bids, payment/delivery state |
| `auction_bids` | Append-only bid history (previous-bidder fallback) |
| `auction_runtime` | Singleton active session + lot pointers |
| `schema_migrations` | Applied versions |

## Economy persistence notes

- Signup creates a **$0** wallet; competitor `$200` is granted once on first team assignment.
- Offline notification durability remains partially filesystem today.

**Confirmed intent:** economy data should save after every completed transaction — do not wait for twice-daily world backups.

## World backups (ops)

**Suggestion:** two world backups per day is acceptable; add automatic backups immediately before/after major events. Separate from Postgres backups.
