# Teams

Working specification for LarpSMP teams on `feature/teams`.  
This is the single place for team roster, tiers, captains, team wealth, admin controls, visibility, persistence, and how teams connect to money / kills / captures / auctions.

Status key:

- **Confirmed** — decided with Karim or settled in plugin plans; implement as written.
- **Likely / planned** — agreed direction, not final naming or exact UX.
- **Suggestion** — working recommendation until playtest or Karim decides.
- **Open** — still needs a choice before that part is finished.

---

## 1. Competition shape

| Thing | Plan | Status |
|-------|------|--------|
| Competitors | 12 players (including Karim) | Confirmed |
| Teams | 3 teams of 4 (soft target). Admins have full team CRUD; Blue/Red/Yellow are seeded defaults. | Confirmed (competition shape); CRUD implemented |
| Likely colors / IDs | Blue, Red, Yellow | Likely (names still open); seeded + editable |
| Captains | One captain per team | Confirmed; assign/clear implemented (abilities later) |
| Tiers | Tier 1 (strongest) through Tier 4 | Implemented (admin dialog + `/larp tier`) |
| Balance rule | Exactly one player of each tier per team | Confirmed (roster design) |
| Substitutes | Not used | Confirmed |
| Developers | Amira = admin / auction host (not a competitor). Azeem = developer (not a competitor). Do not count either as an extra competitor unless they have a roster slot. | Confirmed |
| Winner | Team with the highest combined personal balances at cutoff | Confirmed |

Still to fill in before invite: final usernames, team assignments, captains, tiers, exact team display names, map/dates/event times.

---

## 2. What a team is (and is not)

### Confirmed model

- A team is a roster of up to **four competing accounts** (soft limit with admin warnings).
- Each competitor belongs to **at most one team**.
- Each competitor on a team has a **tier** (1–4) — set by admins via Teams Admin **Tiers** dialog or `/larp tier set`.
- Each team has **one captain** (must be a member of that team).
- There is **no team wallet**. Money is only in personal wallets.
- **Team wealth** = sum of the four personal wallets on that team’s roster.
- Internal `/pay` between teammates does **not** change team wealth.
- Paying another competing team (or Amira / `akiracreates`) **does** change competing scores.
- Amira and Azeem do **not** appear in competing team wealth.
- Offline roster members **still count** toward team wealth, death shortfalls, and team rewards.

### Captains

Captains:

- Nominate the team’s auction representative (representatives cannot change between lots once set).
- Are a useful admin contact for organizing events.

Captains do **not**:

- Control teammates’ wallets.
- Get admin money commands.
- Get arbitrary admin permissions from being captain alone.

### Tiers

Tiers are for:

1. **Roster balancing** — one of each tier per team so no team gets all strong fighters.
2. **Death costs** — the **victim’s** tier sets how much money moves on a kill.

Tiers do **not** (unless separately designed later):

- Automatically change health, damage, gear, or abilities.

Players must **not** be able to change their own tier (would make deaths cheaper).

Death prices (victim tier → amount):

| Victim tier | Cost / killer gain |
|-------------|--------------------|
| 1 | $40 |
| 2 | $30 |
| 3 | $20 |
| 4 | $15 |

Editable in-game via admin kill-reward commands; new values apply to **future** deaths only.

### Substitutes

**Confirmed: substitutes will not be used.**  
If someone cannot participate, their roster slot is not temporarily transferred. No extra wallet, no temporary replacement member in standings.

---

## 3. Team wealth

### Definition

Example: Blue members have $250, $180, $300, $70 → Blue total = **$800**.  
There is no separate $800 account behind those wallets.

### Why it matters

- Final ranking uses team wealth.
- Leaderboards show team standings (`/top teams`).
- Rescue checks team wealth against the $40 threshold (economy owns the calculation; teams provide roster membership).
- Capture / event rewards credit **all four** roster members (including offline), not only players standing on the point.
- Auction spend comes from the representative’s personal wallet; team total falls when they pay Amira.

### Visibility (confirmed)

- Personal balances are **public**.
- Team totals are **public** by default.
- Admins can temporarily **hide or reveal** team totals during events.
- Suggested admin command: `/larp visibility teamtotals <show|hide>`.

Incomplete or unbalanced rosters should produce a **visible admin warning**, not silently invent totals.

---

## 4. Roster rules for admins

### Implemented admin surfaces

- **In-game UI:** Native Paper Dialog console opened by the Teams Admin Nether Star (preferred hotbar slot 8) or `/larp teams` (ops/console authorizer). All admin screens (hub, team detail, create/edit, assign, captain, tiers, remove, delete, warnings) are dialogs.
- **Chat/console:** `/larp team …`, `/larp captain …`, and `/larp tier …`.

Admins can:

| Action | Example command / UI | Notes |
|--------|----------------------|-------|
| Create / rename / recolor / delete teams | `/larp team create\|rename\|color\|delete` or dialog | Full CRUD; Blue/Red/Yellow seeded |
| Assign / move player to a team | `/larp team set <player> <team>` or dialog | Uses authenticated account identity |
| Remove player from team | `/larp team unset <player>` or dialog | Clears captain if that player was captain |
| Set / clear captain | `/larp captain set\|clear` or dialog | Player must be on that team to be captain |
| Set / clear / list tiers | `/larp tier set\|clear\|list` or dialog Tiers board | Account-scoped; survives team moves; warn on duplicates/missing |

Exact wording can change; permissions must block ordinary players.

### Validation style (suggestion → implement as warn-first)

During setup, admins may make several edits in a row. Prefer:

- **Warn** if a team would have two players of the same tier.
- **Warn** if a team would have more than four players.
- **Warn** if roster is unfinished (missing members, missing tier, missing captain).

Do **not** make every intermediate edit impossible unless we later choose hard locks for live competition.

### Persistence identity

- Roster membership, tier, and captain must be stored against the player’s **permanent authenticated account ID**.
- Username is for commands and display only.
- Username change must not create a second team membership or a second wallet.
- Survive disconnects and full server restarts.

---

## 5. What teams expose to other systems

Economy must **not** maintain a second independent copy of the roster. Teams is the source of truth; economy queries it.

### Contract the teams system should provide

| Need | Purpose |
|------|---------|
| Player → current team | Wealth, rewards, death shortfall, displays |
| Team → all four members (including offline) | Wealth sum, split rewards, rescue $25 each, death cover |
| Player → tier | Death cost lookup (economy); display |
| Whether someone is a competitor | Starting money / standings eligibility |
| Whether someone is admin / spectator | Exclude from capture advantage, standings, etc. |
| Captain of a team | Auction nomination, contact, markers |
| Team list / colors | Nametags, player list, chat coloring |

### What economy owns (teams only supplies roster facts)

- Wallets and balances
- `/pay`, admin money, logs
- Team wealth **calculation** from the four wallets
- Reward splitting with rotating whole-dollar remainders
- Death payment + teammate cover + rescue
- Kill-money scopes (global / event / player override)

### What kills / captures / auctions own

- Kill attribution → then calls economy with victim/killer IDs and teams/tiers resolved.
- Capture progress / boss bar → then calls economy with `winningTeam` + `captureId`.
- Auction representatives / bidding UX → economy validates balance and records payment to `akiracreates`.

---

## 6. Team-related player commands

Suggested unless noted as already requested:

| Command | Behavior | Status |
|---------|----------|--------|
| `/team` | Show your roster and team total | Optional / suggested |
| `/team balance` | Team wealth for your team (or as designed) | Suggested in economy plan |
| `/top teams` | Team wealth standings | Planned |
| `/top money` | Personal wealth standings | Planned (not team-only, but standings UI) |
| `/balance` | Own money | Exists / economy |
| `/balance <player>` | Public personal balance | Confirmed visibility |

No private team chat in this plugin.

---

## 7. Team-related admin commands

| Command | Purpose | Status |
|---------|---------|--------|
| `/larp teams` | Open Teams Admin dialog (players) | Implemented |
| `/larp team list` | Dump rosters + warnings | Implemented |
| `/larp team create <name> [color]` | Create team | Implemented |
| `/larp team rename <team> <name>` | Rename team | Implemented |
| `/larp team color <team> <color>` | Recolor team | Implemented |
| `/larp team delete <team>` | Delete team + memberships | Implemented |
| `/larp team set <player> <team>` | Assign or move a competitor | Implemented |
| `/larp team unset <player>` | Remove from team | Implemented |
| `/larp captain set <team> <player>` | Choose captain | Implemented |
| `/larp captain clear <team>` | Clear captain | Implemented |
| `/larp tier set <player> <1–4>` | Give or change tier | Implemented |
| `/larp tier clear <player>` | Clear tier | Implemented |
| `/larp tier list` | List rostered tiers | Implemented |
| `/larp visibility teamtotals <show\|hide>` | Hide/show public team totals | Deferred |
| `/larp money …` | Economy corrections (not team ownership, but affects team wealth) | Exists |
| `/larp killreward set <tier> <amount>` | Edit death prices by tier | Deferred |

All need permissions. Captains and auction representatives get **event abilities only**, not admin money or arbitrary roster edits (unless also admins).

Good feedback examples:

- Warn: “Blue now has two Tier 2 players.”
- Warn: “Yellow has 5 members; competition roster is 4.”
- Success: “Set Mira as captain of Blue.”

---

## 8. Displays involving teams

### Confirmed / requested direction

- Team names **colored** (Blue / Red / Yellow).
- **Nametags**: colored usernames.
- Leaderboards: team wealth, personal wealth, kills.
- Personal balances and team totals are public (with admin hide for team totals).

### Sidebar (implemented)

Compact Wallet sidebar (scoreboard). Team line above personal Wallet; aqua vs gold:

```text
Wallet
Team $817
Wallet $235
```

Players with no team see `Team —`. Team totals visibility hide/show remains deferred.

### Suggested player list

- Group competitors by team.
- Team colors.
- Show tier.
- Optional: personal balances, team totals, captain markers — final density still open.

Exact sidebar / player-list layout: **open** (choose during development; avoid two systems fighting over the same scoreboard).

---

## 9. How teams interact with gameplay rules

### Deaths and shortfalls

- Victim tier sets the price.
- Friendly fire allowed; **teammate kills transfer no money**.
- If the victim cannot pay the full amount, **teammates cover the remainder** (including offline wallets).
- If one teammate cannot afford their share, others who still have money cover the difference.
- Nobody goes negative.
- Contributors each get a message of exactly how much they paid.

### Rescue (team wealth threshold)

- Old “team below $40 is disqualified” rule is **removed**.
- When a **death payment** would take the victim’s team below $40, rescue runs **before** that payment completes.
- Every team receives **$100 total** → **$25 per member** (offline included).
- Only death-related calculations can trigger rescue — not `/pay`, auctions, or other purchases.
- Multiple rescues allowed across different deaths; one death must not double-pay the same rescue.

### Capture rewards

- Winning team gets **$50 total**, split across all four roster members (offline included).
- Whole-dollar rotation: $13 / $13 / $12 / $12 with persisted rotation index.
- Spectators / admins do not count on the point and are not on competing rosters for payout.

### Auctions

- One representative per team, nominated by the captain.
- Representative cannot change between lots.
- Bids from **personal** wallet; teammates may `/pay` them to fund (whether during live bidding is still **open**).
- Payment goes to Amira (`akiracreates`); that money leaves competing team wealth.

### Gladiators

- Team vs team vs team (three teams of four).
- Format details still being clarified; prizes if used are **team totals** to split, not $125×4.

### Ending

- At cutoff, sum each team’s four wallets; highest wins.
- Suggestion: one saved snapshot so late payments cannot change an announced result.
- Ties: possible tiebreak event; exact activity **open**.

---

## 10. Data the teams system must remember

Survive restarts:

| Data | Notes |
|------|-------|
| Team definitions (id, display name, color) | Blue / Red / Yellow as starting set |
| Roster membership (account ID → team) | Max 4 competitors per team for the live event |
| Tier per competitor | 1–4 |
| Captain per team | Account ID |
| Whether account is competitor / admin / spectator | Needed by economy and events |
| Anything required for displays | Color codes, sort order |

Related but owned by economy / other modules (teams must not duplicate as source of truth):

- Wallets, starting-balance granted flag
- Reward remainder rotation position
- Transaction / death / rescue / capture payout IDs
- Kill-money switches and kill prices
- Team-total visibility flag (may live in config or a small shared settings store — agree ownership)

---

## 11. Implementation notes for this branch

### Ownership

- **Teams feature branch (`feature/teams`)**: roster, captains, tiers, team CRUD, team queries, team admin commands + Paper Dialog UI, Wallet sidebar team-wealth line.
- **Economy (existing / Amira’s side)**: wallets, transfers, team wealth **math** (`MoneyService.teamWealth`), reward split, death settlement, rescue, money logs, money commands.
- **Azeem**: more complex systems (auth already present; kill attribution, capture detection, event logic) — calls teams for membership and economy for money.

Do **not** build a second authentication or wallet system inside teams. Use existing permanent account IDs from auth.

### Package / module placement

Implemented under `com.larpsmp.moneyevent.team`:

- Domain + `JdbcTeamRepository` + `TeamService` (Postgres migration `V005__teams.sql`).
- `TeamAdminDialogFactory` / `TeamAdminListener` (dialog-only admin UI + hotbar shortcut, including Tiers board).
- `competitor_tiers` via migration `V006__competitor_tiers.sql`; `PlayerTier` + `/larp tier`.
- `TeamCommandController` wired through `/larp`.
- Temporary authz: console or op via `DevelopmentConsoleOpAuthorizer` (same as money).

### Admin warning examples (unfinished roster)

- Team has fewer than 4 competitors.
- Team has more than 4 competitors.
- Duplicate tier on the same team.
- Missing tier on a competitor.
- Captain not set, or captain not on the team.
- Non-competitor assigned to a competing team.

### Permissions (must exist)

- Ordinary players: read own team / public standings only.
- Admins: set team, tier, captain, visibility.
- Captains: nomination / event abilities only (exact commands when auction work lands).

---

## 12. Confirmed vs open (teams-focused)

### Confirmed

- 12 players, 3×4 teams.
- Likely colors Blue / Red / Yellow.
- One captain per team; captain nominates auction rep.
- One of each tier per team for fair roster design.
- No substitutes.
- No team wallet; wealth = sum of personal wallets.
- Personal + team totals public; admins can hide team totals.
- Offline members count for wealth, death cover, and rewards.
- Friendly fire: no money on teammate kills.
- Rescue uses team wealth; disqualification-below-$40 removed.
- Developers/admins are not competitors unless rostered.

### Open (do not block basic roster CRUD)

- Final team display names (if not literally Blue/Red/Yellow).
- Final roster, captains, tiers from Karim.
- Exact `/team` command surface vs information already on sidebar / player list.
- Exact player-list density (balances, captain markers).
- Hard-lock vs warn-only when live event has started and someone tries to break the 4 / unique-tier rules.
- Who receives full server / ops access vs plugin admin permissions.
- Tiebreak activity if teams finish equal.

### Explicitly out of teams scope

- Map seed, world size, who builds bases.
- Capture neutralization / decay speeds (capture module).
- Auction delivery automation (auction module).
- Hosting provider choice.

---

## 13. Acceptance checklist for the teams feature

Finished when:

1. Admins can assign account → team, set tier 1–4, set captain, with permissions enforced. *(team/tier/captain: done; competitor/admin/spectator role: deferred)*
2. Queries exist for team members, tier, captain, competitor/admin/spectator role. *(members/tier/captain: done; roles: deferred)*
3. Data is keyed by permanent account ID and survives restart.
4. Economy can compute team wealth from the live roster without a duplicated roster table of its own.
5. Incomplete / unbalanced edits warn admins rather than corrupting the competition silently.
6. Public team totals and personal balances can be shown; team totals can be hidden by admin.
7. Non-competitors (Amira, Azeem, spectators) are excluded from competing standings unless given a roster slot.
8. No substitute flow is implemented (confirmed unused).
9. Colored team identity is available for nametags / list / chat as the display work lands.
10. Integration note exists for auth IDs + economy methods teams consumers need.

---

## 14. Quick reference table (roster template)

| Team | Tier 1 | Tier 2 | Tier 3 | Tier 4 | Captain |
|------|--------|--------|--------|--------|---------|
| Blue | To choose | To choose | To choose | To choose | To choose |
| Red | To choose | To choose | To choose | To choose | To choose |
| Yellow | To choose | To choose | To choose | To choose | To choose |

Fill this when Karim confirms the twelve competitors.
