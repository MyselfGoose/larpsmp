# People and roles

**Status:** Confirmed unless marked Open

## Decision authority

| Person | Role |
|--------|------|
| **Karim** | Host and competitor. Final authority on competition design choices. |
| **Amira** | Admin and auction host. Not a competitor by default. Minecraft username for payments: **`akiracreates`**. Contact email from draft: `amiraarts8@gmail.com` (organizer contact, not a command username). |
| **Azeem** | Plugin developer. Not a competitor by default. Handles more complex systems (auth already present; kill attribution, capture detection, event logic planned). |

Amira and Azeem work on the project together. **Do not count either developer as an extra competitor** unless they occupy one of the twelve roster slots.

## In-competition roles

| Role | Meaning |
|------|---------|
| **Competitor** | One of the twelve rostered players. Eligible for starting money, standings, capture advantage, death money, and event participation. |
| **Captain** | One per team. Nominates the auction representative; useful admin contact. Does **not** control teammates’ wallets or get admin money commands. |
| **Auction representative** | One per team for an auction session; cannot change between lots once set. Bids from their personal wallet. |
| **Admin** | Server / plugin privileges for money corrections, roster edits, event controls. |
| **Spectator** | Watches without counting on capture points or competing wealth (**Planned** role model; not fully implemented as a first-class account flag). |

## Team colors

**Confirmed (likely / seeded):** Blue, Red, Yellow.

Final display names beyond color labels, and the full twelve-player roster (usernames, captains, tiers), are **Open** — Karim has not confirmed them yet. Tiers are numbers **1–4** only; they do not have separate names.

## Development ownership (working split)

| Area | Owner (working agreement) |
|------|---------------------------|
| Economy: wallets, transfers, logs, reward split, death settlement, rescue, money UI | Amira (primary), with later integration work also on `feature/teams` |
| Complex systems: kill attribution, capture detection, event state machines | Azeem |
| Teams admin CRUD / tiers / captains UI | Landed on `feature/teams` (Azeem-led commits in git history) |
| Competition rules decisions | Karim |

Shared files (main plugin class, command registration, DB init, config, permissions, sidebar) need explicit coordination so both developers do not thrash the same wiring. See [Integration contracts](../03-architecture/integration-contracts.md) and [Branching and ownership](../04-development/branching-and-ownership.md).

## Hosting / budget notes

- Prefer hosting in **Europe** (Karim preference).
- Rough RAM idea: **~8 GB** (tentative, not a purchased plan).
- Available hosting and database budget mentioned in planning: **approximately 6,000 RUB**.
- Provider, backups ownership, and production access are still being researched (**Open**).
