# Implementation status

Branch reference: **`main`** (includes merged auth, money, bodies, teams, documentation).  
For ordered “what to build next,” see [roadmap.md](roadmap.md). Labels: **Done** / **Partial** / **Not started**.

## Platform

| Feature | Status | Evidence |
|---------|--------|----------|
| Paper 1.21.11 plugin skeleton | Done | `build.gradle.kts`, `plugin.yml` |
| Local Paper + Docker Postgres bootstrap | Done | `scripts/dev-server.sh`, `docker/` |
| Pre-join auth (signup/login/verify/recovery) | Done | `auth` package |
| Account-keyed player bodies | Done | `playerstate` package, `V004` |
| Portable UUID rebind | Done | AuthService login/signup |
| Universal account identity (display + vanilla commands) | Done | `AccountIdentityBinder` + `ServerCommandNameIndex` (`playersByName` rebind) |
| Production admin authz | Partial | `DevelopmentConsoleOpAuthorizer` only |
| Remote object storage | Not started | `LARPSMP_STORAGE_*` placeholders |

## Economy

| Feature | Status | Evidence |
|---------|--------|----------|
| Wallets (empty on signup) | Done | `V003` + `V007`; signup creates $0 wallet |
| Competitor-only starting balance | Done | Grant `$200` once on first team assignment (`starting_balance_granted`) |
| `/balance`, `/pay` | Done | `MoneyCommandController` |
| Admin give/take/set | Done | `/larp money` |
| Ledger for current + settlement types | Done | `wallet_transactions` (+ parent/child refs in `V007`) |
| Offline pay notifications | Done | `PaymentNotificationStore` |
| Team wealth calculation | Done | `MoneyService.teamWealth` |
| Sidebar Team Money + Wallet | Done | `BukkitBalanceSidebar` stacked Top Teams / Top Players / personal (honors team-totals visibility) |
| `/top teams`, `/top money` | Done | `MoneyCommandController.top` |
| Rotating reward split | Done | `MoneyService.splitReward` + persisted rotation index |
| Death payment settlement | Done | `MoneyService.processDeathPayment` (wired from kill attribution) |
| Rescue system | Done | Linked from death settlement; `$25`×members×teams |
| Kill-money scopes / killreward commands | Done | `/larp killmoney`, `/larp killreward set` |
| Visibility hide/show team totals | Done | `/larp visibility teamtotals show\|hide` |

## Teams

| Feature | Status | Evidence |
|---------|--------|----------|
| Team CRUD + seed colors | Done | `team` package, `V005` |
| Captains | Done | `/larp captain` |
| Tiers 1–4 | Done | `V006`, `/larp tier` |
| Soft roster warnings | Done | `TeamService` |
| Dialog admin UI + Nether Star | Done | `TeamAdmin*` |
| Competitor/admin/spectator roles | Not started | Deferred |
| Colored nametags / tab grouping | Done | `TeamPresenceDisplay` on owned scoreboards |
| `/top kills` | Done | `MoneyCommandController.top` + `normalKillLeaderboard` |
| Player `/team`, `/help` | Done | `TeamInfoCommandController`, `PlayerHelpCommandController` |
| Captain auction abilities | Partial | Admin registers reps via `/larp auction rep` (captain nomination is ops/process) |

## Events / world

| Feature | Status |
|---------|--------|
| Spawn protection (respawn-only) | Done (`spawn/` → 10s after respawn in region; unbreakable blocks; `/larp spawn`) |
| Capture points engine | Done (`capture/` → `processCaptureReward`; boss bar; `/larp capture`) |
| Capture temporary blocks | Done (`capture_temp_blocks` + 30s restore across restart) |
| Kill attribution | Done (`combat/` → `processDeathPayment`; `combat_kills` ledger) |
| Auctions automation | Done (`auction/` → `/bid`, `/larp auction`, `/pay` proof, forfeit fallback; manual delivery) |
| Ace Hunt systems | Not started |
| Gladiators automation | Not started |
| Natural disasters | Not confirmed / not started |

## Economy phase plan progress (Amira track)

| Phases | Theme | Status |
|--------|-------|--------|
| 1–2 | Integration note + branch inspect | Partial (this docs set + existing code) |
| 3–7 | Rules constants, wallets, service, logs, `/balance` `/pay` | Mostly Done for basics |
| 8–9 | Admin money + team wealth/leaderboards | Done (`/top teams\|money`) |
| 10–13 | Split, death, rescue, kill-money | Done on `feature/economy-settlement` |
| 14–16 | Kill/capture/auction connections | Done (kill + capture + auction payment settlements) |
| 17–18 | Money UI polish + restart protection extras | Partial (sidebar + visibility done) |
| 19–20 | Full automated + multiplayer battery | Partial (settlement unit/integration tests exist) |
