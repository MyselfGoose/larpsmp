# Known gaps and technical debt

## Competition rules vs current code

| Gap | Rules | Code today | Risk |
|-----|-------|------------|------|
| Competitor role flag | Needed by contracts | Deferred — roster + `starting_balance_granted` used as proxy | Hard to exclude non-competitors systematically once roles land |
| Kill attribution → death API | Attribution must call `processDeathPayment` | Done (`KillAttributionService` + combat listeners) | — |
| Capture → split API | Capture must call `processCaptureReward` | Settlement API Done; capture engine Not started | No automatic capture payouts yet |
| Spawn protection | Confirmed respawn-only | Not implemented | Manual admin enforcement only |
| `plugin.yml` usage | Should list `tier` | Omits `tier` | Minor docs/UX mismatch |

## Auth / config debt

| Item | Detail |
|------|--------|
| `DevelopmentConsoleOpAuthorizer` | Temporary ops/console gate; replace with production adapter (“Azeem’s adapter” noted in planning) |
| Stale dialog copy risk | Some `config.yml` strings may still imply hard UUID bind; behavior is portable rebind — review message keys when editing auth UX |
| AUTH doc schema age | Old doc said migrations 001–004; code has 001–006 — fixed in new [schema doc](../03-architecture/persistence-and-schema.md) |
| README lag | Root README omitted teams until this documentation pass |

## UI / scoreboard

Minecraft allows **one** sidebar per player. Team Money and Wallet share one panel. Any future event HUD must coordinate with `BukkitBalanceSidebar` or it will overwrite.

## Persistence / ops

- Offline pay notifications are filesystem-based under the plugin data folder (not Postgres).
- No in-repo production deploy/runbook beyond remote JDBC notes.
- `LARPSMP_STORAGE_*` unused.

## Superseded design traps (do not reintroduce)

| Old idea | Replacement |
|----------|-------------|
| Team below $40 disqualified | Rescue system |
| Fully safe spawn for everyone | Respawn-only player-damage protection |
| Substitutes take over slots | No substitutes |
| Event kills on normal kill leaderboard | Do **not** count |
| Block liquids on capture points | Liquids allowed |
| Ambiguous single free-for-all gladiators as the only format | Day 4: 3v3s + 1v1 kit bans with kits |
