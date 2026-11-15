# Spawn and world

**Status:** Spawn protection rules **Confirmed**. Map build list **Planned**. World border / seed / difficulty mostly **Open**. Spawn protection **not Implemented** as a dedicated plugin feature yet.

## Spawn protection

**Confirmed (Karim) — supersedes “fully safe spawn for everyone” draft:**

- Spawn is **not** permanently safe for every visitor.
- Protection applies to players who have **newly respawned** there.
- Players **cannot** escape an active fight merely by entering spawn.
- Protection prevents **player-caused damage** only.
- Environmental damage (mobs, fire, lava, falling, hunger, etc.) remains active.
- Spawn blocks **cannot be broken**.

**Still to define in implementation** when coding this feature: exact duration of “newly respawned” protection, boundary geometry, and whether projectiles into the protected player are cancelled.

## Map requirements (build checklist)

Needed for the event (design / build; not all plugin work):

- Spawn and gathering area for twelve competitors + admins
- Auction space at spawn (representatives can stand and see lot info)
- Gladiator arena for three teams (coliseum with spectator seats separated by barriers — see [Gladiators](events/gladiators.md))
- Two to four capture locations with clear platforms and visible boundaries
- Survival space for three team bases and resource gathering
- Ace Hunt separated territories / islands (see [Ace Hunt](events/ace-hunt.md))

**Suggestion:** Name capture points (e.g. Quarry, Tower, Ruins) so chat messages are readable.

## Unchosen world settings

**Open:** map size, world border, seed, difficulty, base rules, item restrictions, event travel distances.

**Suggestion (travel):** normal survival travel for capture points; admin teleportation for Gladiators and auctions.

## Capture visual boundaries

**Suggestion:** differently coloured platform + corner markers (pillars/banners) + occasional particles. Physical markings matter even if particles are disabled.

## Hosting context

Europe preference, ~8 GB RAM idea, ~6,000 RUB budget mentioned in planning — see [People and roles](../01-overview/people-and-roles.md). Provider not chosen.
