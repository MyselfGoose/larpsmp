# Development roadmap (by branch)

You are here: **`feature/capture-points`** implements the capture engine on top of `main` (settlement + kill attribution already merged).  
**After this lands:** [`feature/spawn-protection`](#8-featurespawn-protection--queued-can-parallel-early) or [`feature/display-and-leaderboards`](#9-featuredisplay-and-leaderboards--queued).

Status key on this page:

| Marker | Meaning |
|--------|---------|
| **DONE** | Merged into `main`; do not rebuild |
| **NEXT** | Start this branch now |
| **QUEUED** | After the previous branch lands |
| **PARALLEL** | Can run alongside another branch if ownership is clear |
| **OPS / NON-CODE** | Needed for launch; not a feature branch |

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
NEXT     feature/capture-points
QUEUED   feature/spawn-protection
QUEUED   feature/display-and-leaderboards
QUEUED   feature/auctions
QUEUED   feature/ace-hunt
QUEUED   feature/gladiators          (light automation; admin-first OK)
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

## 7. `feature/capture-points` — NEXT (on branch)

**Owner:** Azeem (detection) + economy already has `splitReward`.  
**Depends on:** economy settlement (reward payout).

| # | Work item |
|---|-----------|
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

## 8. `feature/spawn-protection` — QUEUED (can PARALLEL early)

**Smaller branch.** Confirmed rules already documented.

| # | Work item |
|---|-----------|
| 1 | Protect **newly respawned** players only (not “anyone inside spawn”) |
| 2 | Block player-caused damage only; env damage stays |
| 3 | Spawn blocks unbreakable |
| 4 | Clear boundary / duration of “newly respawned” protection |

Can land anytime after teams; does not block economy settlement.

---

## 9. `feature/display-and-leaderboards` — QUEUED

**Coordinate with whoever owns the sidebar** (scoreboards overwrite each other).

| # | Work item |
|---|-----------|
| 1 | Keep single sidebar: Team Money + Wallet (already DONE) |
| 2 | Wire visibility hide into sidebar (needs economy visibility flag) |
| 3 | `/top kills` once attribution exists (exclude event kills) |
| 4 | Colored nametags by team |
| 5 | Player list grouped by team + tier (density playtest) |
| 6 | Optional `/team`, `/help`, `/capture` info if still useful |

Prefer after economy `/top` + visibility, and after teams colors are stable.

---

## 10. `feature/auctions` — QUEUED

**Can stay thin:** wallets + `/pay` already work for a manual auction.

| # | Work item | Priority |
|---|-----------|----------|
| 1 | `/bid`, lot open/close, reject over-balance bids | Nice |
| 2 | Register fixed representatives per session | Nice |
| 3 | Prove payment to `akiracreates` for exact amount | Important if automated |
| 4 | Previous-bidder fallback if winner cannot pay | Important if automated |
| 5 | Item delivery | **Open** — manual OK for v1 |
| 6 | Mid-bid teammate `/pay` | **Open** — ask Karim |

**Launch path:** run first auction manually; automate only if pain shows up.

---

## 11. `feature/ace-hunt` — QUEUED (Day 3)

**Depends on:** event apply/restore helpers (keep inventory, spectator, maybe kill-money off), map territories.

| # | Work item |
|---|-----------|
| 1 | Ace nomination / teleport to prep territories (2–3 h prep window is ops/schedule) |
| 2 | Points: hunt placement + ace survival placement; on-screen counter |
| 3 | Non-ace 3-minute bedrock-box respawn; keep inventory on |
| 4 | Dead ace → spectator; successful hunters → spectator |
| 5 | No skybase rule (enforce as designed) |
| 6 | Surface minute / underground ban near end |
| 7 | Territory vs world border solution (**Open** design) |
| 8 | Exact point table (**Open** — ask Karim) |

---

## 12. `feature/gladiators` — QUEUED (Day 4, admin-first)

Karim: **chat + admin + command blocks may be enough**.

| # | Work item | Priority |
|---|-----------|----------|
| 1 | Arena TP + spectator barriers (map + admin) | Ops / map |
| 2 | Kits (team mini-kit + 5 banable 1v1 kits) | Content from Karim |
| 3 | Fight quota (max 4 fights / player) | Automate if time |
| 4 | 3v3 schedule + 1v1 kit-ban flow | Automate if time |
| 5 | Points board + placement payout via economy `splitReward` | When prizes confirmed |
| 6 | Kill money off for event | Uses kill-money scopes |

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

1. **Now:** create `feature/economy-settlement` from `main` → constants + competitor starting balance → split → death/rescue → kill-money scopes → `/top` + visibility → tests.  
2. **Then / parallel:** `feature/kill-attribution` — Done.  
3. **Then:** `feature/capture-points`.  
4. **Whenever small:** `feature/spawn-protection`.  
5. **Then:** display polish, auctions (manual first), Ace Hunt, Gladiators as needed.  
6. **Throughout:** chase Karim on [open questions](open-questions.md).

---

## How to start the next branch

```bash
git checkout main
git pull origin main
git checkout -b feature/economy-settlement
```

Read before coding:

- [Economy rules](../02-competition-rules/economy.md)
- [Economy architecture](../03-architecture/economy-architecture.md)
- [Integration contracts](../03-architecture/integration-contracts.md)
- [Known gaps](known-gaps-and-debt.md)

When the branch is merge-ready: PR → `main` → delete the feature branch → pick the next **QUEUED** item.
