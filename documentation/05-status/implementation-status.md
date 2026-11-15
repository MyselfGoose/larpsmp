# Implementation status

Branch reference: **`feature/teams`**. Labels: **Done** / **Partial** / **Not started**.

## Platform

| Feature | Status | Evidence |
|---------|--------|----------|
| Paper 1.21.11 plugin skeleton | Done | `build.gradle.kts`, `plugin.yml` |
| Local Paper + Docker Postgres bootstrap | Done | `scripts/dev-server.sh`, `docker/` |
| Pre-join auth (signup/login/verify/recovery) | Done | `auth` package |
| Account-keyed player bodies | Done | `playerstate` package, `V004` |
| Portable UUID rebind | Done | AuthService login/signup |
| Production admin authz | Partial | `DevelopmentConsoleOpAuthorizer` only |
| Remote object storage | Not started | `LARPSMP_STORAGE_*` placeholders |

## Economy

| Feature | Status | Evidence |
|---------|--------|----------|
| Wallets + $200 on signup | Done | `V003`, AuthService / MoneyService |
| `/balance`, `/pay` | Done | `MoneyCommandController` |
| Admin give/take/set | Done | `/larp money` |
| Ledger for current types | Done | `wallet_transactions` |
| Offline pay notifications | Done | `PaymentNotificationStore` |
| Team wealth calculation | Done | `MoneyService.teamWealth` |
| Sidebar Team Money + Wallet | Done | `BukkitBalanceSidebar` |
| `/top` leaderboards | Not started | — |
| Rotating reward split | Not started | — |
| Death payment settlement | Not started | — |
| Rescue system | Not started | — |
| Kill-money scopes / killreward commands | Not started | — |
| Visibility hide/show team totals | Not started | Deferred in TEAMS |
| Competitor-only starting balance | Not started | Gap vs rules |

## Teams

| Feature | Status | Evidence |
|---------|--------|----------|
| Team CRUD + seed colors | Done | `team` package, `V005` |
| Captains | Done | `/larp captain` |
| Tiers 1–4 | Done | `V006`, `/larp tier` |
| Soft roster warnings | Done | `TeamService` |
| Dialog admin UI + Nether Star | Done | `TeamAdmin*` |
| Competitor/admin/spectator roles | Not started | Deferred |
| Colored nametags / tab grouping | Not started | — |
| Captain auction abilities | Not started | — |

## Events / world

| Feature | Status |
|---------|--------|
| Spawn protection (respawn-only) | Not started |
| Capture points engine | Not started |
| Capture temporary blocks | Not started |
| Kill attribution | Not started |
| Auctions automation | Not started |
| Ace Hunt systems | Not started |
| Gladiators automation | Not started |
| Natural disasters | Not confirmed / not started |

## Economy phase plan progress (Amira track)

| Phases | Theme | Status |
|--------|-------|--------|
| 1–2 | Integration note + branch inspect | Partial (this docs set + existing code) |
| 3–7 | Rules constants, wallets, service, logs, `/balance` `/pay` | Mostly Done for basics |
| 8–9 | Admin money + team wealth/leaderboards | Partial (`/top` missing) |
| 10–13 | Split, death, rescue, kill-money | Not started |
| 14–16 | Kill/capture/auction connections | Not started |
| 17–18 | Money UI polish + restart protection extras | Partial (sidebar exists) |
| 19–20 | Full automated + multiplayer battery | Partial (unit/integration for current money only) |
