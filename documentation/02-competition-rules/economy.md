# Economy rules

**Status:** Core rules **Confirmed**. Partial **Implemented** (wallets, `/pay`, admin money, ledger). Death settlement, rescue, reward split, and kill-money scopes are **Confirmed** but **not Implemented** yet.

## Personal wallets

| Rule | Status |
|------|--------|
| Every competitor starts with **$200** once | Confirmed |
| Money cannot go negative | Confirmed · Implemented (`balance >= 0`) |
| Whole dollars only (no cents) | Confirmed · Implemented (`long` amounts) |
| Money is a plugin number, not a physical item | Confirmed |
| Persist against permanent account ID, not display name | Confirmed · Implemented |
| Rejoining must never grant a second starting payment | Confirmed · **Gap:** every auth signup currently gets $200 — see [known gaps](../05-status/known-gaps-and-debt.md) |
| No team wallet | Confirmed |

Team wealth example: wallets $250 + $180 + $300 + $70 → team total **$800**. An internal `/pay` does not change that total.

## Sources of money

| Source | Effect | Kind | Status |
|--------|--------|------|--------|
| Starting balance | $200 once per competitor | Creates money | Confirmed |
| Killing an opponent | Moves money victim/team → killer | Transfers existing | Confirmed (settlement not coded) |
| Capturing a point | $50 to winning team, split | Creates money | Confirmed (payout not coded) |
| Event prizes | Admin-chosen amount | Creates money | Confirmed (via admin give today) |
| `/pay` | Player → player (or Amira) | Transfers existing | Confirmed · Implemented |
| Tasks | Admin-checked rewards | Creates money | Planned / Suggestion |
| Rescue | $100 per team ($25 each) | Creates money | Confirmed (not coded) |

Kills and `/pay` move money that already exists. Starting balances, capture rewards, admin prizes, and rescues **add** money to the competition.

## Paying and spending

### `/pay`

- **Confirmed / Implemented:** `/pay <player> <amount>`
- Recipients may be offline (**Confirmed** plugin decision · Implemented via wallet lookup + offline notification store)
- Not blocked during combat (**Confirmed**)
- Anyone may be a recipient in code; paying a competing enemy team is banned by **rules**, not by code (**Confirmed** design)
- Reject zero, negative, fractional, oversized, and self-payments
- No transfer fee (**Suggestion** adopted in plans)

Suggested feedback (Implemented in spirit via command controller messages):

- Sender and recipient both informed of the new balances
- Clear “need $X more” style errors

Auction winners pay **Amira** (`akiracreates`) through `/pay`. That money leaves competing team wealth because Amira is not on a competing roster.

### Admin money

**Implemented:** `/larp money give|take|set` with reasons/audit context.

**Planned:** `/larp money balance`, `/larp money history` as richer investigation tools.

### Withdrawing

**Superseded / clarified:** With personal wallets only, “withdraw” is not a separate player action. Do not add `/withdraw` that creates free money. Admin grants and event payouts are explicit operations.

## Whole-dollar splitting

**Confirmed:** Uneven rewards use **rotating remainder** distribution.

Example for **$50** capture reward across four members: **$13, $13, $12, $12**. Extra dollars rotate across future rewards. Rotation position must persist across restarts (**Confirmed**; **not Implemented**).

Same approach for shared death shortfalls and future team prize splits (e.g. Gladiator totals) so totals are preserved and money is neither created nor lost by rounding.

## Death payments (economy side)

Victim tier sets the amount:

| Victim tier | Cost / killer gain |
|-------------|--------------------|
| 1 | $40 |
| 2 | $30 |
| 3 | $20 |
| 4 | $15 |

Settlement rules (**Confirmed**, not Implemented):

1. If kill money disabled for the relevant scope → no charge, no payout, no rescue check.
2. Friendly fire → no money transfer.
3. If the payment would take the victim’s team below **$40**, run **rescue first**.
4. Victim pays as much as possible.
5. Remainder split among teammates (offline included).
6. If one teammate cannot afford their share, others who still have money cover the difference.
7. Killer receives exactly the charged total; nobody goes negative.
8. Each contributor is told exactly how much they paid.
9. Idempotent on a stable `deathId` (no double pay).

Full combat attribution rules: [Combat and kills](combat-and-kills.md).

## Rescue system

**Superseded:** Old rule that a team below $40 is **disqualified** — removed.

**Confirmed replacement:**

- Trigger: death payment would leave victim team wealth **below $40**
- Timing: checked during death settlement, **before** completing that payment; after every death-related balance calculation (not on a timer)
- Payout: every team gets **$100 total** → **$25 per member** (offline included) → **$300** added server-wide per rescue round
- Only death-related calculations can trigger rescue — not `/pay`, auctions, or purchases
- Multiple rescues allowed across different deaths
- One death must not activate the same rescue twice
- One poor team triggers **one** rescue round (not one per poor player)
- Rescue payments must not recursively trigger another rescue

## Visibility

| Item | Visibility | Status |
|------|------------|--------|
| Personal balances | Public (`/balance <player>` allowed) | Confirmed · Implemented |
| Team totals | Public by default | Confirmed · HUD Implemented; hide/show **Deferred** |
| Admin hide/show team totals | `/larp visibility teamtotals <show\|hide>` | Confirmed policy · command Deferred |

## Leaderboards (planned commands)

| Command | Purpose | Status |
|---------|---------|--------|
| `/top teams` | Team wealth standings | Implemented |
| `/top money` | Personal wealth standings | Implemented |
| `/top kills` | Kill counts (excludes event + friendly-fire) | Implemented |
| `/balance` | Own balance | Implemented |
| `/balance <player>` | Public personal balance | Implemented |

**Confirmed (Karim):** Event kills **do not** count toward the normal kill leaderboard.

## Logging requirements

**Confirmed / Planned** (ledger partially Implemented for current transaction types):

Every money change should record time, type, amount, balances before/after, payer/recipient, team membership when relevant, reason, admin/command, event IDs, death/rescue contributions, and success/failure/reversal.

Keep full logs for the entire five-day event; archive afterward rather than deleting immediately.

Current Implemented types: `STARTING_BALANCE`, `ADD`, `REMOVE`, `SET`, `TRANSFER`. Planned types include `DEATH_PAYMENT`, `TEAM_REWARD`, `CAPTURE_REWARD`, `EVENT_REWARD`, `RESCUE_PAYMENT`, `AUCTION_PAYMENT`, `REVERSAL`, etc.

## Tasks (optional economy use)

**Planned / Suggestion:** Admins announce a task, verify completion, and pay via admin money commands. Not a full quest engine for v1. Each task needs name, condition, optional deadline, reward, first-team-only vs every team, and whether reward is individual or split.
