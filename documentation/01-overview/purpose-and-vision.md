# Purpose and vision

**Status:** Confirmed (product intent) · Plugin foundation **Implemented** on `feature/teams`

## What LarpSMP is

LarpSMP is a **five-day Minecraft Java competition for twelve friends**. It is one large event with smaller organized activities on different days, on a shared survival world that stays open **24/7**.

Between scheduled events, players build bases, gather resources, fight, and prepare. At scheduled times everyone comes together for activities such as **capture points**, **gladiators**, **mystery shulker auctions**, and (planned) **Ace Hunt**.

The comparison used in the working notes: like a Fortnite season with things happening throughout — except this is a private Minecraft world, private teams, and a private money system.

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
| Players | 12 competitors (including Karim) | Confirmed |
| Teams | 3 teams of 4 | Confirmed |
| Team balance | One tier 1, one tier 2, one tier 3, one tier 4 per team | Confirmed (roster design) |
| Starting money | $200 per competitor (once) | Confirmed; see [known gaps](../05-status/known-gaps-and-debt.md) for current signup behavior |
| Wallets | Personal wallets only | Confirmed |
| Winner | Highest team wealth at cutoff | Confirmed |
| Length | 5 days; dates still to choose | Confirmed length; dates Open |
| Server | Minecraft Java 1.21.11; Paper plugin | Confirmed / Implemented |
| Availability | Open 24/7; events at scheduled times | Confirmed |
| Language | English | Confirmed |
| Main activities | Daily captures, gladiators, mystery auctions; Ace Hunt on Day 3 (design) | See [events overview](../02-competition-rules/events/overview-and-calendar.md) |

## Who it is for

- **Competitors:** twelve friends on the roster
- **Admins / hosts:** run the server, auctions, and event logistics (Amira is admin and auction host; not a competitor by default)
- **Developers:** Amira and Azeem build the plugin; neither is an extra competitor unless they hold a roster slot

## What this repository is

The repository is the **Paper plugin + local development stack** for that competition: authentication, wallets, teams administration, account-keyed player bodies, and (planned) event systems.

Plugin artifact name: **MoneyEvent**. Product / event name: **LarpSMP**.

## What success looks like for the plugin

Players can authenticate, hold persistent money, belong to balanced teams, see public balances and team wealth, settle kills and rescues correctly, run captures and structured events without duplicating rewards across restarts, and finish with an auditable ledger that explains every balance change.
