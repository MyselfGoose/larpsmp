# Known gaps and technical debt

## Competition rules vs current code

| Gap | Rules | Code today | Risk |
|-----|-------|------------|------|
| Competitor role flag | Needed by contracts | Done (`account_participation`) | — |
| Kill attribution → death API | Attribution must call `processDeathPayment` | Done (`KillAttributionService` + combat listeners) | — |
| Capture → split API | Capture must call `processCaptureReward` | Done (`CaptureService` → `processCaptureReward`) | — |
| Spawn protection | Confirmed respawn-only | Done (`spawn/` + `/larp spawn define`) | — |
| `plugin.yml` usage | Should list `tier` / `capture` / `spawn` | Lists `capture` + `spawn`; `tier` still via teams controller | Minor |

## Auth / config debt

| Item | Detail |
|------|--------|
| `DevelopmentConsoleOpAuthorizer` | Replaced by `AccountPermissionAuthorizer` (deprecated shim remains for older tests) |
| Stale dialog copy risk | Some `config.yml` strings may still imply hard UUID bind; behavior is portable rebind — review message keys when editing auth UX |
| AUTH doc schema age | Old doc said migrations 001–004; code has 001–006 — fixed in new [schema doc](../03-architecture/persistence-and-schema.md) |
| README lag | Root README omitted teams until this documentation pass |

## UI / scoreboard

Minecraft allows **one** sidebar per player (always right — no native left panel). `BukkitBalanceSidebar` stacks Top Teams, Top Players, and personal Team Money/Wallet in that single slot. Any future event HUD must coordinate with it or it will overwrite.

## Persistence / ops

- Offline pay notifications are filesystem-based under the plugin data folder (not Postgres).
- Single Paper process only — multi-node against one Postgres is unsupported (see [configuration-and-secrets](../04-development/configuration-and-secrets.md) and [production readiness audit](../audits/production-readiness-audit.md)).
- Production deploy still needs hosting, backups, roster, and map ops (Open).
- `LARPSMP_STORAGE_*` unused.

## Superseded design traps (do not reintroduce)

| Old idea | Replacement |
|----------|-------------|
| Vanilla commands resolving launcher nicknames | `AccountIdentityBinder` + `ServerCommandNameIndex` (`PlayerList.playersByName` rebind) |
| Team below $40 disqualified | Rescue system |
| Fully safe spawn for everyone | Respawn-only player-damage protection |
| Substitutes take over slots | No substitutes |
| Event kills on normal kill leaderboard | Do **not** count |
| Block liquids on capture points | Liquids allowed |
| Ambiguous single free-for-all gladiators as the only format | Day 4: 3v3s + 1v1 kit bans with kits |
