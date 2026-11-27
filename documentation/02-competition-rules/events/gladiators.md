# Gladiators

**Status:** Event **Confirmed**. Day 4 format is the current design source. Plugin systems **Implemented** (admin-first). Map build (coliseum / seats / barrier shell) remains ops. Kit item lists and exact prizes stay admin-configured (**Open**).

## High-level

Team-versus-team-versus-team combat in a defined **coliseum-style arena** with spectator seats. Barrier blocks separate spectators from the arena; fighters are **teleported** in.

**Confirmed:** fighters **receive kits** (not personal survival gear).

## Day 4 format

Two parts:

### 1) Team fights (3v3s)

- Each team chooses who fights and who sits out (`/larp gladiators lineup`).
- Every player may fight at most **4** times total across the event (strategy constraint; tracked automatically).
- Each team fights each other team **twice** (generated double round-robin; e.g. six bouts for three teams).
- Fighters receive a **mini kit** (saved by admin into Postgres; contents still Open), restored to full hunger and saturation, then fight.
- Winning team gains **1 point**.

### 2) 1v1s

- **5 kits** total (saved by admin; contents still Open).
- Admins nominate two fighters after the 3v3 schedule is finished (`/larp gladiators duel`).
- Chosen fighters who still have fights left **ban kits one by one** (`/gladiator ban` or `/larp gladiators ban`) until one kit remains.
- That remaining kit is used for the 1v1.
- Wins also award **1 point**.

### Placements

Based on points. Exact prize table still **Open**. Shipped default in `config.yml` is the documented suggestion **$125 / $50 / $0** as **team totals** (not per player). Ties use worse-place ranking (same rule as Ace Hunt). Payout is explicit via `/larp gladiators payout` (idempotent `splitReward` / `EVENT_REWARD`, settlement kind `GLADIATOR`). Stop does **not** auto-pay.

## Implemented admin flow

`/larp gladiators …` — `arena`, `spawn`, `barriers`, `kit`, `open`, `stop`, `schedule`, `lineup`, `fight`, `winner`, `duel`, `ban`, `quota`, `points`, `payout`, `status`, `reload`.

Player: `/gladiator` (quota + points); `/gladiator ban <kit>` during kit-ban turns.

Sitting out uses adventure mode behind the seat region (spectator gamemode would noclip through barriers). Personal bodies are frozen for the session so kits are not autosaved into `account_player_states`.

## Economy / kill money

Kill money is **off** for Gladiators participants via event scope `gladiators` for the live session. Event deaths are marked `event_kill` and stay off `/top kills`. Opening Gladiators is refused while Ace Hunt is live (and the reverse).

## Arena requirements (ops + plugin)

- Coliseum with watching seats (map build)
- Barriers so spectators cannot walk into the arena (`/larp gladiators barriers fill|clear` for a defined shell, max 16384 blocks)
- Separate starting positions for teams when a fight starts (`/larp gladiators spawn`)
- Clear start / end / restore of inventories and locations (frozen body snapshots; restore on `stop`)

## Older draft notes (partially superseded)

Earlier materials described a single elimination round with all twelve in one arena, last team standing, gear vs kits undecided, and unclear round count. The **Day 4 write-up** above is the current format and should be preferred.
