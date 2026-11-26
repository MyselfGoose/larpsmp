# Development roadmap (by branch)

You are here: **`feature/gladiators`** (Day 4 Gladiators engine).  
**After this lands:** [`feature/roles-and-authz`](#13-featureroles-and-authz--queued).

Status key on this page:

| Marker             | Meaning                                                |
| ------------------ | ------------------------------------------------------ |
| **DONE**           | Merged into `main`; do not rebuild                     |
| **NEXT**           | Start this branch now                                  |
| **QUEUED**         | After the previous branch lands                        |
| **PARALLEL**       | Can run alongside another branch if ownership is clear |
| **OPS / NON-CODE** | Needed for launch; not a feature branch                |

Branch from **`main`** every time. Merge back to **`main`** when a branch is done. One feature theme per branch.

---

## Progress at a glance

```text
DONE     feature/authentication   → auth, Resend, .env, bootstrap
DONE     feat/money               → wallets, /pay, admin money, HUD basics
DONE     (account bodies)         → account-keyed inventory/location on main
DONE     feature/teams            → teams, captains, tiers, team wealth HUD
DONE     documentation            → documentation/ tree on main
────────────────────────────────────────────────────────────────
DONE     feature/economy-settlement
DONE     feature/kill-attribution
DONE     feature/capture-points
DONE     feature/spawn-protection
DONE     feature/display-and-leaderboards
DONE     feature/auctions
DONE     feature/ace-hunt
NEXT     feature/gladiators          (light automation; admin-first OK)
QUEUED   feature/roles-and-authz
OPS      roster, calendar, map, hosting, playtest
```

---

## 1. `feature/authentication` — DONE

**Merged into `main`.** Pre-join Paper dialogs + Postgres accounts.

| Work item | Status |
|-----------|--------|
| Signup / login / email verify / recovery | DONE |
| Argon2id passwords, rate limits | DONE |
| Portable Minecraft UUID rebind | DONE |
| Resend email delivery | DONE |
| `.env` secrets + Docker Postgres/pgAdmin | DONE |
| `dev-server.sh` bootstrap | DONE |

**Do not reopen** unless fixing auth bugs.

---

## 2. `feat/money` — DONE (foundation)

**Merged into `main`.** Personal wallets and basic money commands.

| Work item | Status |
|-----------|--------|
| Wallet per account, `$200` on signup | DONE |
| `MoneyService` add/remove/set/transfer | DONE |
| Ledger types: `STARTING_BALANCE`, `ADD`, `REMOVE`, `SET`, `TRANSFER` | DONE |
| `/balance`, `/balance <player>`, `/pay` (offline OK) | DONE |
| `/larp money give\|take\|set` | DONE |
| Offline payment notifications | DONE |
| Personal Wallet sidebar section | DONE |

**Known leftover (fix later in economy-settlement):** every signup gets `$200`, not only competitors.

---

## 3. Account-keyed bodies — DONE

**On `main`** (landed via the money/bodies line before teams).

| Work item | Status |
|-----------|--------|
| `account_player_states` persist inventory/location/vitals | DONE |
| Autosave + quit wipe of vanilla shell | DONE |
| Concurrent-login reject | DONE |
| GameProfile / display name → LarpSMP username | DONE (`AccountIdentityBinder` + `ServerCommandNameIndex`) |

---

## 4. `feature/teams` — DONE

**Merged into `main`.** Roster administration + team wealth display.

| Work item | Status |
|-----------|--------|
| Team CRUD, Blue/Red/Yellow seed | DONE |
| Membership assign/move/remove | DONE |
| Captains set/clear | DONE |
| Tiers 1–4 | DONE |
| Soft roster warnings | DONE |
| Dialog admin UI + Nether Star | DONE |
| `MoneyService.teamWealth` | DONE |
| Sidebar Team Money + Wallet | DONE |
| Competitor / admin / spectator roles | Not in this branch → see roles branch |
| `/larp visibility teamtotals` | Not in this branch → economy/display |
| Colored nametags / tab grouping | Not in this branch → display branch |

---

## 5. `feature/economy-settlement` — DONE (this branch)

**Owner:** Amira (economy).  
**Branch from:** `main`.  
**Goal:** Every confirmed money rule that does not need kill/capture world logic yet — plus the APIs those systems will call.

### Must ship on this branch

| # | Work item | Notes |
|---|-----------|-------|
| 1 | Economy rules/constants module | `$200`, death prices 40/30/20/15, rescue `$40` / `$100`/`$25`, capture `$50` |
| 2 | Competitor-only starting balance | Stop granting `$200` to every auth account; grant once to competitors |
| 3 | Expand transaction types | e.g. `DEATH_PAYMENT`, `TEAM_REWARD`, `CAPTURE_REWARD`, `RESCUE_PAYMENT`, `AUCTION_PAYMENT`, `REVERSAL` |
| 4 | `splitReward` + persisted remainder rotation | `$13/$13/$12/$12` pattern; offline members included |
| 5 | `processDeathPayment` | Victim pay → teammate cover → killer credit; friendly fire = no money; idempotent `deathId` |
| 6 | `processRescue` | Before unaffordable death drops team below `$40`; `$25`×4×3; no recursion; multi-rescue OK |
| 7 | Kill-money scopes | Global / event / player override + `/larp killmoney` + `/larp killreward set` |
| 8 | `/top teams`, `/top money` | Public standings |
| 9 | `/larp visibility teamtotals show\|hide` | Confirmed policy |
| 10 | Parent-rich death/rescue logging | Parent transaction + contributions |
| 11 | Tests | Split rotation, death shortfall, offline cover, rescue, duplicate IDs, restart |

### Explicitly out of this branch

- Detecting who caused a death (that is kill-attribution)
- Capture area/progress (that is capture-points)
- Auction UI / shulker delivery
- Ace Hunt / Gladiators match flow

**Done when:** another system can call economy with a confirmed death or capture ID and get a correct, logged, restart-safe money result — and players can see `/top` standings.

**Suggested first commit on the branch:** constants module + competitor starting-balance fix (unblocks clean standings).

---

## 6. `feature/kill-attribution` — DONE

**Owner:** Azeem.  
**Depends on:** `processDeathPayment` (landed with economy-settlement).

| # | Work item | Status |
|---|-----------|--------|
| 1 | Last-attacker window **60 seconds** | Done |
| 2 | Melee, projectiles, pets, TNT, lava/fire, falls after hit, traps | Done |
| 3 | Friendly fire: attribute if needed for logs, but **no money** (economy enforces) | Done |
| 4 | Unknown trap ownership → environmental, no money | Done |
| 5 | Emit stable `deathId` + evidence string into economy | Done |
| 6 | Respect kill-money scopes from economy | Done (economy gates; attribution always calls) |
| 7 | Event kills **do not** count on normal kill leaderboard (wire when `/top kills` exists) | Done (`combat_kills.event_kill` + `EventCombatScope`) |
| 8 | No automatic combat-log punishment | Done |

**Done when:** a real in-game kill produces exactly one correct money settlement (or none).

---

## 7. `feature/capture-points` — DONE

**Merged into `main`.** Capture engine + idempotent `$50` payout.

| # | Work item | Status |
|---|-----------|--------|
| 1 | 2–4 named points; one active at a time | Done (`quarry`/`tower`/`ruins`; day completions) |
| 2 | Advantage = lead − runner-up; ties freeze | Done (`CaptureProgressEngine`) |
| 3 | Base 10 min / 1 uncontested player; configurable faster multipliers | Done (`capture.durations-seconds`) |
| 4 | Neutralize then build; resume remaining progress if original team returns | Done |
| 5 | Empty decay (“pretty quick”; tune in playtest) | Done (default `decay-multiplier: 2.0`) |
| 6 | Height: platform + 3 blocks; boats/minecarts OK; no spectators/respawn screen/mid-teleport | Done |
| 7 | Platform unbreakable; temp blocks 30s; other players can break temp blocks; liquids **allowed** | Done |
| 8 | Boss bar + chat at 25/50/75/100% | Done |
| 9 | Admin start/pause/resume/stop | Done (`/larp capture …`) |
| 10 | On complete: call economy once with `captureId` for `$50` split | Done (`processCaptureReward`) |
| 11 | Persist active point / progress / temp blocks across restart | Done (`V010`) |

**Done when:** a contested capture pays exactly once and survives restart.

---

## 8. `feature/spawn-protection` — DONE (this branch)

**Smaller branch.** Confirmed rules documented in [spawn-and-world.md](../02-competition-rules/spawn-and-world.md).

| # | Work item | Status |
|---|-----------|--------|
| 1 | Protect **newly respawned** players only (not “anyone inside spawn”) | Done (`SpawnProtectionListener` grants only when respawn loc is in region) |
| 2 | Block player-caused damage only; env damage stays | Done (melee/projectile/pet/TNT cancelled; lava/fire/fall/hunger allowed) |
| 3 | Spawn blocks unbreakable | Done (`SpawnBlockListener`) |
| 4 | Clear boundary / duration of “newly respawned” protection | Done (AABB via `/larp spawn define`; default **10s**; early clear on attack) |

Can land anytime after teams; does not block economy settlement.

---

## 9. `feature/display-and-leaderboards` — DONE

**Merged into `main` after this branch.** Sidebar ownership stays with `BukkitBalanceSidebar`; nametags/tab write onto the same owned scoreboards.

| # | Work item | Status |
|---|-----------|--------|
| 1 | Keep single sidebar: Team Money + Wallet | DONE |
| 2 | Wire visibility hide into sidebar | DONE (`teamTotalsVisible`) |
| 3 | `/top kills` (exclude event + friendly-fire kills) | DONE |
| 4 | Colored nametags by team | DONE (`TeamPresenceDisplay`) |
| 5 | Player list grouped by team + tier | DONE (density: color + username; no balances/captain markers) |
| 6 | Optional `/team`, `/help` (capture tip in help; no player `/capture`) | DONE |

---

## 10. `feature/auctions` — DONE

Full automation shipped on this branch. Item delivery stays manual; mid-bid teammate `/pay` remains allowed in v1.

| #   | Work item                                        | Status |
| --- | ------------------------------------------------ | ------ |
| 1   | `/bid`, lot open/close, reject over-balance bids | DONE |
| 2   | Register fixed representatives per session       | DONE |
| 3   | Prove payment to `akiracreates` for exact amount | DONE (`/auction pay` + `AUCTION` settlement) |
| 4   | Previous-bidder fallback if winner cannot pay    | DONE (`/larp auction forfeit`) |
| 5   | Item delivery                                    | Manual v1 (`/larp auction delivered` tracks only) |
| 6   | Mid-bid teammate `/pay`                          | Allowed v1 (no restriction; revisable) |

---

## 11. `feature/ace-hunt` — DONE (Day 3)

**Merged into `main`.** Prep/hunt/surface/ban, scoring boss bar, lockup, borders, `/larp acehunt`.

---

## 12. `feature/gladiators` — NEXT (Day 4, admin-first)

Karim: **chat + admin + command blocks may be enough**. This branch ships light automation so ops do not need command blocks for the core loop.

| # | Work item | Status |
|---|-----------|--------|
| 1 | Arena TP + spectator barriers (map + admin) | Done (plugin geometry/TP/fill; coliseum map is ops) |
| 2 | Kits (team mini-kit + 5 banable 1v1 kits) | Done (admin save from inventory; item lists still Open) |
| 3 | Fight quota (max 4 fights / player) | Done |
| 4 | 3v3 schedule + 1v1 kit-ban flow | Done (double round-robin + admin-nominated duels) |
| 5 | Points board + placement payout via economy `splitReward` | Done (boss bar; `/larp gladiators payout`; default $125/$50/$0 still Open) |
| 6 | Kill money off for event | Done (`gladiators` event scope) |

Do **not** block the roadmap on full automation.

---

## 13. `feature/roles-and-authz` — QUEUED

| # | Work item |
|---|-----------|
| 1 | Competitor / admin / spectator flags on accounts |
| 2 | Exclude non-competitors from standings / capture advantage / starting money |
| 3 | Replace `DevelopmentConsoleOpAuthorizer` with production permissions |
| 4 | Who gets full server ops vs plugin admin (**Open**) |

Best after competitor-only starting balance is designed, or in the same PR series as that fix.

---

## 14. OPS / NON-CODE — before inviting players

Not branches, but blockers for “event ready”:

| Item | Status |
|------|--------|
| Final 12 roster, captains, tiers | Open — Karim |
| Five dates, times, timezone, wealth cutoff | Open — Karim |
| Map: spawn, captures, arena, ace territories | Build |
| Hosting (Europe preference, ~8 GB idea, ~6000 RUB budget) | Open |
| Backups (world + Postgres); who restores | Open |
| Playtest battery (poor death, rescue, capture tie, restart mid-reward) | After settlement + capture |
| Natural disasters | Unconfirmed — ignore until Karim says yes |

---

## Recommended working order (what you do next)

1. Economy settlement, kill attribution, capture, spawn protection, display/leaderboards, auctions, Ace Hunt — **Done**.  
2. **Current:** `feature/gladiators`.  
3. **Next after merge:** `feature/roles-and-authz`.  
4. **Throughout:** chase Karim on [open questions](open-questions.md).

---

## How to start the next branch

```bash
git checkout main
git pull origin main
git checkout -b feature/roles-and-authz
```

Read before coding:

- [Roadmap §13](#13-featureroles-and-authz--queued)
- [Known gaps](known-gaps-and-debt.md)

When the branch is merge-ready: PR → `main` → delete the feature branch → pick the next **QUEUED** item.
