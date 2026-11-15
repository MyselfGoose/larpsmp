# Branching and ownership

## Branches (as of knowledge recovery)

| Branch | Role |
|--------|------|
| `main` | Early Paper init only (far behind features) |
| `feature/authentication` | Auth line |
| `feat/money` | Parallel money line |
| `dev` | Auth + wallets + account bodies integration trunk before teams |
| **`feature/teams`** | Current HEAD — teams, tiers, team wealth HUD |

Almost all recent work lives on feature / `dev` branches, not `main`.

## Git authorship (approximate)

- **Azeem** — majority of commits (auth, DB, bootstrap, teams, much money integration)
- **Amira (`akiracreates`)** — wallet foundation, payments, admin money commits on the money line

Karim is the competition design authority and is not a primary git committer.

## Working ownership

| Area | Owner |
|------|-------|
| Economy rules + money code | Amira primary |
| Kill attribution, captures, event engines | Azeem |
| Teams admin platform | Landed via Azeem on `feature/teams`; shared going forward |
| Competition rule decisions | Karim |

Coordinate shared wiring files — see [Integration contracts](../03-architecture/integration-contracts.md).

## Suggested economy branch name (from plan)

`feature/economy` for death settlement / split / rescue work — create when starting that slice; do not rebuild auth or teams inside it.
