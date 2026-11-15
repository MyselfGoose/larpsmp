# Gladiators

**Status:** Event **Confirmed**. Day 4 format from Karim is the current design source. Most automation **not required** for v1 (admin + chat + command blocks may suffice). Older “one big free-for-all” suggestions are **Superseded** where they conflict with Day 4.

## High-level

Team-versus-team-versus-team combat in a defined **coliseum-style arena** with spectator seats. Barrier blocks separate spectators from the arena; fighters are **teleported** in.

**Confirmed (Karim):** fighters **receive kits** (not personal survival gear).

## Day 4 format (Karim)

Two parts:

### 1) Team fights (3v3s)

- Each team chooses who fights and who sits out.
- Every player may fight at most **4** times total across the event (strategy constraint).
- Each team fights each other team **twice** (e.g. Blue vs Yellow, Blue vs Red, then the pairings repeat).
- Fighters receive a **mini kit** (kit contents TBD), restored to full hunger and saturation, then fight.
- Winning team gains **1 point**.

### 2) 1v1s

- **5 kits** total (specified later).
- Chosen fighters who still have fights left **ban kits one by one** until one kit remains.
- That remaining kit is used for the 1v1.
- Wins also award **1 point**.

### Placements

Based on points. Exact prize table still **Open**. Earlier suggested **$125 / $50 / $0** as **team totals** (not per player) — **not final** until confirmed. If used, split with the same rotating whole-dollar rules as other team rewards.

## What needs coding vs manual

Karim’s note: most of this can be done with **chat, commands, and an admin**; command blocks help. Automation is nice-to-have if time allows.

Nice-to-have automation ideas:

- Fight quota tracking (max 4 fights per player)
- Kit ban UI / commands
- Point board on screen
- Teleport / restore settings (kill money off, keep inventory policy as chosen)
- Idempotent prize payout via economy

## Older draft notes (partially superseded)

Earlier materials described a single elimination round with all twelve in one arena, last team standing, gear vs kits undecided, and unclear round count. Karim’s “What?” reply to “one round or multiple” was unclear; the **Day 4 write-up** is the clearer current format and should be preferred.

Kill money should be disableable for the event (**Confirmed** capability). Event kills **do not** count on the normal kill leaderboard (**Confirmed**).

## Arena requirements

- Coliseum with watching seats
- Barriers so spectators cannot walk into the arena
- Separate starting positions for teams when a fight starts
- Clear start / end / restore of inventories and locations if the chosen process stores them
