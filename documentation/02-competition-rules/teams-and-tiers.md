# Teams and tiers

**Status:** Competition model **Confirmed**. Admin CRUD, captains, tiers, team-wealth HUD, and competitor/spectator participation **Implemented**.

## Competition shape

| Thing | Plan | Status |
|-------|------|--------|
| Competitors | 12 (including Karim) | Confirmed |
| Teams | 3 × 4 | Confirmed |
| Colors | Blue, Red, Yellow | Confirmed likely; seeded + editable in plugin |
| Captains | One per team | Confirmed · assign/clear Implemented |
| Tiers | 1 (strongest) … 4 | Confirmed · Implemented |
| Balance rule | Exactly one of each tier per team | Confirmed (roster design) |
| Substitutes | **Not used** | Confirmed |
| Winner | Highest team wealth at cutoff | Confirmed |

Final usernames, assignments, captains, tiers, and exact display names beyond colors: **Open**.

## What a team is

- Roster of up to **four** competing accounts (soft limit with admin warnings).
- Each competitor belongs to **at most one** team.
- Each rostered competitor has a **tier** (1–4).
- Each team has **one captain** who must be a member.
- **No team wallet.** Team wealth = sum of personal wallets.
- Offline members still count for wealth, death shortfalls, and team rewards.
- Amira and Azeem do **not** appear in competing wealth unless rostered.

## Captains

Captains **do**:

- Nominate the team’s auction representative (reps cannot change between lots once set)
- Act as a useful admin contact

Captains **do not**:

- Control teammates’ wallets
- Receive admin money commands from captain status alone

## Tiers

Used for:

1. Roster balancing
2. Death costs (victim’s tier)

**Not** (unless separately designed later): automatic health, damage, gear, or ability changes.

Players must not be able to change their own tier.

Death prices:

| Victim tier | Amount |
|-------------|--------|
| 1 | $40 |
| 2 | $30 |
| 3 | $20 |
| 4 | $15 |

Editable later via admin kill-reward commands (**Deferred**). New values apply to **future** deaths only.

## Substitutes

**Confirmed:** No substitutes. If someone cannot play, their slot is not temporarily transferred. No extra wallet and no temporary standings member.

## Admin roster controls

**Implemented** surfaces:

- Paper Dialog console: `/larp teams` or Teams Admin Nether Star
- Chat: `/larp team …`, `/larp captain …`, `/larp tier …`

| Action | Example | Status |
|--------|---------|--------|
| Create / rename / recolor / delete | `/larp team create\|rename\|color\|delete` | Implemented |
| Assign / move | `/larp team set <player> <team>` | Implemented |
| Remove | `/larp team unset <player>` | Implemented |
| Captain set/clear | `/larp captain set\|clear` | Implemented |
| Tier set/clear/list | `/larp tier set\|clear\|list` | Implemented |
| Hide/show team totals | `/larp visibility teamtotals …` | Implemented |

### Validation style

**Suggestion (adopted in teams work):** warn-first during setup:

- Two players of the same tier on one team
- More than four players
- Unfinished roster (missing members, tiers, or captain)

Do not hard-block every intermediate edit unless a later live-lock mode is chosen (**Open**).

## Player-facing team commands

| Command | Behavior | Status |
|---------|----------|--------|
| `/team` | Show roster and total | Implemented |
| `/team balance` | Team wealth | Implemented |
| `/top teams` | Standings | Implemented |
| Sidebar Team Money | Live team wealth | Implemented |

No private team chat in this plugin.

## Roster template

| Team | Tier 1 | Tier 2 | Tier 3 | Tier 4 | Captain |
|------|--------|--------|--------|--------|---------|
| Blue | To choose | To choose | To choose | To choose | To choose |
| Red | To choose | To choose | To choose | To choose | To choose |
| Yellow | To choose | To choose | To choose | To choose | To choose |

Fill when Karim confirms the twelve competitors.

## Implementation details

See [Teams architecture](../03-architecture/teams-architecture.md).
