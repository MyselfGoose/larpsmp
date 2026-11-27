# Teams architecture

Merged from the former `docs/TEAMS.md` implementation sections.

**Status:** Admin teams/tiers/captains **Implemented**. Competitor/spectator participation **Implemented**.

## Package

`com.larpsmp.moneyevent.team`

Notable types:

- `Team`, `TeamMember`, `TeamColor`, `PlayerTier`
- `TeamService` — mutations + queries + soft warnings
- `JdbcTeamRepository` — Postgres
- `TeamCommandController` — `/larp team|captain|tier|teams`
- `TeamAdminDialogFactory` / `TeamAdminListener` / `TeamAdminTool` — dialog UI + Nether Star
- `RosterChangeListener` — display refresh hooks

Migrations: `V005__teams.sql`, `V006__competitor_tiers.sql`.

## Data model

| Table | Purpose |
|-------|---------|
| `teams` | id, slug, display_name, color, sort_order, captain_account_id |
| `team_memberships` | (team_id, account_id), sort_order; one team per account |
| `competitor_tiers` | account_id → tier 1–4; survives team moves |

Blue / Red / Yellow are seeded if missing.

## Contracts exposed to other systems

| Need | Purpose | Status |
|------|---------|--------|
| Player → team | Wealth, rewards, shortfall, displays | Implemented |
| Team → members (incl. offline) | Wealth sum, splits, rescue, cover | Implemented |
| Player → tier | Death cost; display | Implemented |
| Captain of team | Auction nomination (ops); admin registers via `/larp auction rep` | Partial (rep registration Implemented; captain self-serve command not required for v1) |
| Team list / colors | Nametags, list, chat | Implemented (colors + `TeamPresenceDisplay` nametags/tab grouping) |
| Competitor / spectator | Eligibility filters | Implemented (`account_participation`) |
| Plugin admin / server operator | Authz | Implemented (`account_plugin_admins` / `account_server_operators`) |

Economy must **not** maintain a second independent roster copy. `MoneyService.teamWealth` sums via SQL join to memberships.

## Admin UX history

Dialog console → brief chest GUI experiment → **dialog-only** again. Prefer dialogs going forward.

## Authz

Console, plugin admin, or server operator via `AccountPermissionAuthorizer`. `/larp op` grants plugin admin; `/larp serverop` grants Bukkit op (and implies plugin admin).

## Acceptance checklist (from TEAMS.md)

| # | Criterion | Progress |
|---|-----------|----------|
| 1 | Assign team, tier, captain with permissions | Done |
| 2 | Queries for members, tier, captain, roles | Done (`isCompetitor` / `isSpectator` / `competitorsOf`) |
| 3 | Account-ID keyed; survives restart | Done |
| 4 | Economy computes wealth from live roster | Done (`teamWealth`) |
| 5 | Incomplete edits warn admins | Soft warnings implemented |
| 6 | Public totals; admin hide | Done (HUD + `/larp visibility teamtotals`) |
| 7 | Non-competitors excluded from standings | Done |
| 8 | No substitute flow | Done (not built) |
| 9 | Colored identity for nametags/list/chat | Nametags + tab Done; chat coloring not required |
| 10 | Integration note for consumers | See [Integration contracts](integration-contracts.md) |

## Sidebar (Implemented)

Single Minecraft sidebar panel (right side only — client limit); personal balances on top, rankings below:

```text
Team Money ← objective title (heading)
$2,601
··········
Wallet
$250
··········
TOP TEAMS
1. Blue $2,601
2. Red $2,100
3. Yellow $1,800
··········
TOP PLAYERS
1. alice $850
```

Hierarchy: white bold headings → gray names → gold amounts → dark-gray ranks/dividers (no per-team name colors). Ranking amounts sit in the scoreboard suffix. Empty player ranks omitted. Unused scoreboard rows are reset so the board does not pad with blank space. When team totals are hidden, Team Money + TOP TEAMS omit; Wallet + TOP PLAYERS remain.
