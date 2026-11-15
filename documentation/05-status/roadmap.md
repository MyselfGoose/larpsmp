# Roadmap

Practical continuation order based on the economy phase plan and confirmed events. Adjust ownership with Amira/Azeem as needed.

## Near-term foundation (economy)

1. Integration contracts stay current ([Integration contracts](../03-architecture/integration-contracts.md)).
2. Authoritative rules/constants module (starting balance, death prices, rescue threshold/amounts, capture reward).
3. Centralize competitor-only starting balance (fix signup-for-all gap).
4. Expand ledger types + parent/child death transactions.
5. `splitReward` with persisted rotation.
6. `processDeathPayment` + `processRescue` with idempotent IDs.
7. Kill-money scope commands.
8. `/top teams` / `/top money` (+ kills when attribution exists).
9. Team-totals visibility toggle.
10. Restart/idempotency tests for every payout path.

**First usable economy milestone historically:** wallets + `/pay` + admin money + logs — **largely done**. Next milestone: death + rescue + split.

## Parallel complex systems (Azeem)

1. Kill attribution (60s window, projectiles, pets, TNT, lava/fire, traps).
2. Capture detection (areas, advantage, boss bar, temp blocks, liquids allowed).
3. Call economy with stable IDs only after attribution/capture completion is authoritative.
4. Event setting apply/restore helpers (`killmoney`, keep inventory).

## Events (by calendar priority)

| Priority | Event | Automation urgency |
|----------|-------|--------------------|
| High | Daily captures | Needs real plugin for fair contested play |
| Medium | Auctions | Can start manual with `/pay`; automate bid validation later |
| Medium | Ace Hunt | Needs points, respawn boxes, spectator rules, territory design |
| Medium | Gladiators Day 4 | Karim: admin + command blocks may suffice; automate if time |
| Low | Natural disasters | Unconfirmed |

## Display / UX

- Coordinate any new scoreboard with existing sidebar.
- Colored nametags and team-grouped player list.
- Optional `/team`, `/help`, `/capture` info commands if not redundant.

## Ops before invite

- Final roster from Karim.
- Dates / timezone / cutoff.
- Host + backups + who has access.
- Production authz replacing `DevelopmentConsoleOpAuthorizer`.
- Playtest: poor victim death, rescue, capture ties, restart mid-reward, auction unpaid winner.

## Suggested suggested capture speed starting point

Configurable playtest defaults:

| Advantage | Time |
|-----------|------|
| 1 | 10m |
| 2 | 7m |
| 3 | 5m |
| 4 | 3m 30s |

Empty decay: start at ~2× one-player capture speed; tune to “pretty quick.”
