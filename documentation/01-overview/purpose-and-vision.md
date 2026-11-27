# Purpose and vision

**Status:** Confirmed (product intent) · Plugin foundation **Implemented**

## What LarpSMP is

LarpSMP is a **five-day Minecraft Java competition for twelve players**. It is one large event with smaller organized activities on different days, on a shared survival world that stays open **24/7**.

Between scheduled events, players build bases, gather resources, fight, and prepare. At scheduled times everyone comes together for activities such as **capture points**, **gladiators**, **mystery shulker auctions**, and **Ace Hunt**.

The working comparison: like a Fortnite season with things happening throughout — except this is a private Minecraft world, private teams, and a private money system.

## Win condition

**Confirmed:** The winning team is the one with the **highest combined personal wallet balances** at the agreed cutoff.

There is **no separate team bank account**. Team wealth is only the sum of the four personal wallets on that team’s roster.

## Why money exists

Money gives players reasons to:

- Fight and defend each other
- Show up to events
- Think before spending (especially on mystery shulkers)
- Help broke teammates via `/pay` and death shortfall rules

Money is a **whole-dollar integer** stored by the plugin. There is no physical currency item in the current plan.

## Competition shape (basics)

| Thing | Plan | Status |
|-------|------|--------|
| Players | 12 competitors | Confirmed |
| Teams | 3 teams of 4 | Confirmed |
| Team balance | One tier 1, one tier 2, one tier 3, one tier 4 per team | Confirmed (roster design) |
| Starting money | $200 per competitor (once, on first team assignment) | Confirmed · Implemented |
| Wallets | Personal wallets only | Confirmed |
| Winner | Highest team wealth at cutoff | Confirmed |
| Length | 5 days; dates still to choose | Confirmed length; dates Open |
| Server | Minecraft Java 1.21.11; Paper plugin | Confirmed / Implemented |
| Availability | Open 24/7; events at scheduled times | Confirmed |
| Language | English | Confirmed |
| Main activities | Daily captures, gladiators, mystery auctions; Ace Hunt on Day 3 | See [events overview](../02-competition-rules/events/overview-and-calendar.md) |

## Who it is for

- **Competitors:** twelve players on the roster
- **Admins / hosts:** run the server, auctions, and event logistics (not competitors by default unless rostered)
- **Developers:** build the plugin; not extra competitors unless they hold a roster slot

## What this repository is

The repository is the **Paper plugin + local development stack** for that competition: authentication, wallets, teams administration, account-keyed player bodies, and event systems.

Plugin artifact name: **MoneyEvent**. Product / event name: **LarpSMP**.

## What success looks like for the plugin

Players can authenticate, hold persistent money, belong to balanced teams, see public balances and team wealth, settle kills and rescues correctly, run captures and structured events without duplicating rewards across restarts, and finish with an auditable ledger that explains every balance change.
