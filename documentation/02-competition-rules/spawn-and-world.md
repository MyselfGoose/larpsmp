# Spawn and world

**Status:** Spawn protection rules **Confirmed** and **Implemented** (`spawn/` package). Map build list **Planned**. World border / seed / difficulty mostly **Open**.

## Spawn protection

**Confirmed (Karim) — supersedes “fully safe spawn for everyone” draft:**

- Spawn is **not** permanently safe for every visitor.
- Protection applies to players who have **newly respawned** there.
- Players **cannot** escape an active fight merely by entering spawn.
- Protection prevents **player-caused damage** only.
- Environmental damage (mobs, fire, lava, falling, hunger, etc.) remains active.
- Spawn blocks **cannot be broken**.

### Implementation details (locked)

| Topic | Choice |
|-------|--------|
| Boundary | Axis-aligned box in `config.yml` (`spawn.region`), set with `/larp spawn define pos1\|pos2` |
| Duration | **10 seconds** after a qualifying respawn (`spawn.protection-seconds`, minimum 1) |
| Grant | On respawn only if the respawn location is **inside** the defined region |
| Scope | Immunity follows the player for the remaining duration (not re-checked against standing in spawn) |
| Early clear | Ends early if the protected player deals player-caused damage; also cleared on quit |
| Projectiles / pets / TNT | **Cancelled** when the damager resolves to another player (same resolver as combat) |
| Placed lava/fire | **Not cancelled** (treated as environmental for protection; kill attribution may still credit ownership) |
| Block place | Allowed; only **break** is cancelled inside the region |
| Region undefined | No grants and no block lock until corners are defined |

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
