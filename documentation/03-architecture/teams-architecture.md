# Teams architecture

Merged from the former `docs/TEAMS.md` implementation sections.

**Status:** Admin teams/tiers/captains **Implemented** on `feature/teams`. Competitor/admin/spectator role flags **Deferred**.

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
| Captain of team | Auction nomination; markers | Implemented |
| Team list / colors | Nametags, list, chat | Partial (colors in model; nametags/list grouping not finished) |
| Competitor / admin / spectator | Eligibility filters | Deferred |

Economy must **not** maintain a second independent roster copy. `MoneyService.teamWealth` sums via SQL join to memberships.

## Admin UX history

Dialog console → brief chest GUI experiment → **dialog-only** again. Prefer dialogs going forward.

## Authz

Temporary: console or op via `DevelopmentConsoleOpAuthorizer` (same as money). Production adapter still needed.

## Acceptance checklist (from TEAMS.md)

| # | Criterion | Progress |
|---|-----------|----------|
| 1 | Assign team, tier, captain with permissions | Done for team/tier/captain; roles deferred |
| 2 | Queries for members, tier, captain, roles | Members/tier/captain done; roles deferred |
| 3 | Account-ID keyed; survives restart | Done |
| 4 | Economy computes wealth from live roster | Done (`teamWealth`) |
| 5 | Incomplete edits warn admins | Soft warnings implemented |
| 6 | Public totals; admin hide | Public HUD done; hide deferred |
| 7 | Non-competitors excluded from standings | Partial — no first-class role flag yet |
| 8 | No substitute flow | Done (not built) |
| 9 | Colored identity for nametags/list/chat | Not finished |
| 10 | Integration note for consumers | See [Integration contracts](integration-contracts.md) |

## Sidebar (Implemented)

Single Minecraft sidebar panel; two stacked sections:

```text
Team Money
$2,601
··········
Wallet
$250
```

No team → `—` under Team Money. Amounts use grouped digits. Aqua = team; gold = personal.
