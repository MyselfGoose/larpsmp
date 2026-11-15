# Integration contracts

**Status:** Working agreement for Amira (economy) ↔ Azeem (complex systems). Partially realized in code (auth + teams + wallets exist; event hooks do not).

## 1. Account integration

Auth should give economy:

| Capability | Why |
|------------|-----|
| Permanent account ID | Wallet ownership key |
| Current Minecraft username | Commands / display |
| Whether fully authenticated | Block `/pay` before auth finishes |
| Lookup by username including offline | `/pay`, admin tools |

Economy must not:

- Allow `/pay` before authentication completes
- Grant a second starting balance after a username change
- Let a player access someone else’s wallet
- Treat a returning $0 competitor as a brand-new player

## 2. Team integration

Economy queries teams for:

- Current team of a player
- All members of a team (four, including offline)
- Player tier
- Whether someone is a competitor (**Deferred** flag)
- Whether someone is admin/spectator (**Deferred** flag)

Teams remains source of truth; economy does not copy the roster into a second membership table.

## 3. Event integration (stable IDs)

Agree identifiers for:

- Capture completions (`captureId`)
- Gladiator / Ace Hunt prizes
- Auctions / lots
- Rescue activations
- Deaths (`deathId`)

Stable IDs prevent double rewards on retry or restart.

### Kill → economy

Attribution system sends a confirmed payload (victim, killer, tier, teams, cause, evidence, `deathId`). Economy settles money or no-ops (friendly fire, kill money off, duplicate id).

### Capture → economy

Capture system sends `captureId`, point id, winning team, amount, time. Economy splits $50 once.

### Auction → economy

Auction system needs: available balance check, bid validation, proof that winner paid `akiracreates` the exact amount, mark payment used. No escrow unless deliberately added later. Item delivery stays outside economy until decided.

## 4. Shared file ownership

Coordinate edits to:

- `MoneyEventPlugin` (wiring)
- Command registration / `plugin.yml`
- Database initialization / migrations
- Configuration / permissions
- Sidebar / player-list controllers (scoreboards overwrite each other if two systems fight)

## 5. Code ownership reminder

| System | Primary |
|--------|---------|
| Wallets, `/pay`, admin money, logs, splits, death settlement, rescue, money UI | Economy owner (Amira) |
| Auth (done), kill attribution, capture detection, event state | Azeem |
| Teams admin CRUD | Landed on `feature/teams`; treat as shared platform |

Finished when both sides keep this note updated as APIs land.
