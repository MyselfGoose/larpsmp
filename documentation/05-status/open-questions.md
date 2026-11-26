# Open questions

Only items that are **still unresolved**. Settled Karim answers are recorded in the competition-rules docs instead.

## Economy-adjacent

| Question | Notes |
|----------|-------|
| May auction teammates `/pay` the representative **during** live bidding? | **v1: allowed** (no restriction). Funding before bidding is Confirmed; revisit if Karim wants a block |
| Auction item delivery: manual or plugin-controlled? | **v1: manual** (`/larp auction delivered` tracks handoff only) |
| Individual kill-money override: applies to victim, killer, or both? | Suggestion: victim authoritative |
| Final sidebar / player-list density (balances, captain markers, tiers) | Sidebar + team/tier tab grouping shipped; balances/captain markers in list still open |
| Exact Ace Hunt point values (hunt placements + survival placements) | **Resolved for v1:** `2 / 1 / 0` (config.yml); matches 2+2=4 example |
| Does Ace Hunt use normal death money, or kill money off + points only? | **Resolved for v1:** kill money off for participants (`ace-hunt` event scope); points only |

## Capture

| Question | Notes |
|----------|-------|
| Exact enemy neutralization speed | Awaiting Karim / playtest |
| Exact empty-point decay speed | Karim: pretty quick; decide with testing |
| Maximum contested duration (if any) | Suggestion: none initially; admin pause |
| Final number of capture locations (2–4) | |
| Capture point names | Non-blocking for code |

## Gladiators

| Question | Notes |
|----------|-------|
| Exact prize amounts / whether $125/$50/$0 is final | Team totals if used; shipped default in `config.yml` until Karim confirms |
| Full kit contents (team fight mini kit + five 1v1 kits) | “Send when needed”; admins save kits in-game |
| How much automation vs admin/command blocks | Karim: manual is enough; light automation shipped on `feature/gladiators` |

## Ace Hunt / map

| Question | Notes |
|----------|-------|
| How ace territories exist with world border | **Resolved for v1:** per-player world borders + cuboid confinement (islands may sit outside main border) |
| Hunt duration (15–20 minutes suggested) | **Resolved for v1:** default 18 minutes (`ace-hunt.hunt-seconds`) |
| Exact “underground banned” enforcement | **Resolved for v1:** after surface warning, cancel break/place below `surface-y` + periodic damage |

## Roster / schedule / ops

| Question | Notes |
|----------|-------|
| Final twelve competitors (names/tags) | Unknown yet |
| Team assignments, captains, tiers | Colors Blue/Red/Yellow confirmed likely |
| Five dates, times, timezone, wealth cutoff | |
| Who receives full server / ops access vs plugin admin permissions | |
| Behavior when server is offline at a scheduled event start | |
| Which active event states must resume after restart (full list) | |
| Hosting provider selection | Budget ~6000 RUB mentioned |
| Tiebreak activity if teams finish equal | Possible; not required for sure |

## Explicitly non-blocking for plugin core

Map seed, final world size, who builds each location, detailed prohibited-item list (if admins enforce manually), detailed shulker item balancing.
