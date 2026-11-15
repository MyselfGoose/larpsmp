# LarpSMP Documentation

This is the **canonical documentation** for LarpSMP: a five-day Minecraft competition for twelve friends, and the Paper plugin (`MoneyEvent`) that runs its economy, auth, teams, and future events.

Older paths under [`docs/`](../docs/) redirect here. Prefer these pages when reading or linking.

## How to read this set

| If you need… | Start here |
|--------------|------------|
| What the event is and who is involved | [Purpose and vision](01-overview/purpose-and-vision.md), [People and roles](01-overview/people-and-roles.md) |
| Confirmed gameplay rules | [Competition rules](02-competition-rules/economy.md) |
| How the plugin is built | [System overview](03-architecture/system-overview.md) |
| Running a local server | [Getting started](04-development/getting-started.md) |
| Refresh / test what already works (2 clients) | [Manual testing refresher](04-development/manual-testing-refresher.md) |
| What is coded vs still planned | [Implementation status](05-status/implementation-status.md) |
| What to build next (by branch) | [Roadmap](05-status/roadmap.md) |
| Unresolved decisions | [Open questions](05-status/open-questions.md) |

## Status legend

Every substantive claim in these docs should be readable with one of these labels:

| Label | Meaning |
|-------|---------|
| **Implemented** | Verified in source on `main` (or as otherwise noted) |
| **Confirmed** | Decided with Karim or settled in the plugin plan; may not be coded yet |
| **Planned** | Agreed direction; naming or exact UX may change |
| **Suggestion** | Working recommendation until playtest or Karim decides |
| **Open** | Still needs a decision before that part is finished |
| **Superseded** | Older draft text replaced by a later decision |

Do not treat **Confirmed** as **Implemented**. Game rules and code often diverge until a feature ships.

## Documentation map

### 01 — Overview

- [Purpose and vision](01-overview/purpose-and-vision.md)
- [People and roles](01-overview/people-and-roles.md)
- [Glossary](01-overview/glossary.md)

### 02 — Competition rules

- [Economy](02-competition-rules/economy.md)
- [Teams and tiers](02-competition-rules/teams-and-tiers.md)
- [Combat and kills](02-competition-rules/combat-and-kills.md)
- [Spawn and world](02-competition-rules/spawn-and-world.md)
- [Capture points](02-competition-rules/capture-points.md)
- Events
  - [Overview and calendar](02-competition-rules/events/overview-and-calendar.md)
  - [Mystery shulker auctions](02-competition-rules/events/auctions.md)
  - [Gladiators](02-competition-rules/events/gladiators.md)
  - [Ace Hunt](02-competition-rules/events/ace-hunt.md)
- [Ending and schedule](02-competition-rules/ending-and-schedule.md)

### 03 — Architecture

- [System overview](03-architecture/system-overview.md)
- [Auth and identity](03-architecture/auth-and-identity.md)
- [Economy architecture](03-architecture/economy-architecture.md)
- [Teams architecture](03-architecture/teams-architecture.md)
- [Player state](03-architecture/player-state.md)
- [Persistence and schema](03-architecture/persistence-and-schema.md)
- [Integration contracts](03-architecture/integration-contracts.md)

### 04 — Development

- [Getting started](04-development/getting-started.md)
- [Manual testing refresher](04-development/manual-testing-refresher.md)
- [Project layout](04-development/project-layout.md)
- [Configuration and secrets](04-development/configuration-and-secrets.md)
- [Testing](04-development/testing.md)
- [Branching and ownership](04-development/branching-and-ownership.md)

### 05 — Status

- [Implementation status](05-status/implementation-status.md)
- [Open questions](05-status/open-questions.md)
- [Known gaps and debt](05-status/known-gaps-and-debt.md)
- [Roadmap](05-status/roadmap.md)

### Assets

- [Ace Hunt timeline](assets/ace-hunt-timeline.png)

## Repository facts (snapshot)

| Item | Value |
|------|--------|
| Plugin name | `MoneyEvent` |
| Artifact | `money-event` `0.1.0` |
| Package | `com.larpsmp.moneyevent` |
| Minecraft / Paper | 1.21.11 |
| Java | 21+ |
| Database | PostgreSQL 16+ |
| Current feature branch | `feature/teams` |

Last knowledge recovery pass: November 2026. Re-verify **Implemented** claims against `src/` before relying on them for release work.
