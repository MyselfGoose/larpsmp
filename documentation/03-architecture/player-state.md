# Player state (account-keyed bodies)

**Status:** **Implemented**.

## Purpose

Authenticated players load and save Minecraft “body” state from PostgreSQL table `account_player_states`, keyed by `accounts.id`. Switching LarpSMP accounts on the same client should feel like switching characters.

Stored:

- World location (world name + x/y/z/yaw/pitch)
- Inventory, armor, offhand, ender chest (BYTEA blobs)
- Health, food, saturation, exhaustion
- XP level / total / progress
- Game mode
- Active potion effects
- Schema version + updated_at

## Lifecycle

1. **Auth success (configure phase)** — reject if account already has pending/active session; register session; preload from DB or defaults; allow join.
2. **Spawn / join** — apply location and body on the main thread; set display/tab names to LarpSMP username.
3. **Autosave** — every `player-state.autosave-seconds` (default 60).
4. **Quit / plugin disable** — sync UPSERT, then clear Bukkit inventory shell so vanilla `playerdata/<uuid>.dat` cannot leak items to the next account on the same client UUID.
5. **Account switch** — previous account saved on quit; new login loads only the new account’s row.

## Concurrent login

**Reject** the new connection if that `account_id` is already online or still pending admit. Active session is not kicked. Prevents double-login duplication.

## Crash behavior

Atomic UPSERTs. On crash, last successful autosave or quit save restores (same trade-off class as vanilla). No separate WAL for body state.

## First-time defaults

No row → empty inventories, full health/food, Survival, spawn from `player-state.default-spawn` (empty world name = server default-world spawn).

## Related listeners

Death handling currently: strip teams admin tool if needed; rewrite death message names to LarpSMP usernames. **Not** death-money settlement.
