# Capture points

**Status:** Gameplay rules largely **Confirmed**. Plugin capture system **not Implemented**. Exact neutralization / decay multipliers still **Open** / playtest.

## Session shape

- **2–4** capture locations (**Open:** final count).
- Every location activates **once per day**.
- Only **one** point active at a time.
- After capture: winning team gets **$50**, next point chosen randomly, opens **immediately**.
- Admins can pause / delay / stop if something goes wrong.
- Daily session continues until scheduled points are done.

## Who makes progress

- The team with the **most** players on the point progresses.
- Advantage = leading team count minus **second-largest** team count (**Confirmed** runner-up model).
- Smaller advantage → slower progress; larger advantage → faster.
- If two teams are tied for the lead → **progress freezes**.
- Spectators and admins do **not** count.
- Players on the respawn screen do **not** count.
- Players mid-teleport do **not** count until teleport finishes.
- Boats / minecarts count if location is inside the area.
- Height: platform surface up to **three blocks above** (**Confirmed**).

### Example advantage table

| Blue | Red | Yellow | Behavior |
|------|-----|--------|----------|
| 1 | 0 | 0 | Blue advantage 1 |
| 2 | 1 | 0 | Blue advantage 1 |
| 3 | 1 | 0 | Blue advantage 2 |
| 4 | 0 | 0 | Blue max advantage |
| 3 | 2 | 2 | Blue advantage 1 |
| 2 | 2 | 1 | Tie for lead → freeze |

## Progress ownership

- If Blue has 60% and Red takes the lead, Red must **neutralize** Blue’s progress to 0 before building Red’s own bar. Red does **not** inherit 60%.
- If the original team retakes the lead before progress hits zero, it **resumes from remaining progress** (**Confirmed**).
- Empty point: progress **decays**. Karim: decide with testing; should be **pretty quick**. Exact speed **Open** (Suggestion: decay at twice the one-player capture speed).
- Enemy neutralization speed: still awaiting exact answer (**Open**).
- Contested duration hard timeout: initially **none**; admins pause/reset (**Suggestion**).

## Capture time baseline

**Confirmed:** Base capture time is **10 minutes for one uncontested player**. More teammates make it faster.

**Suggestion** (configurable, playtest):

| Advantage | Full capture time |
|-----------|-------------------|
| 1 | 10 minutes |
| 2 | 7 minutes |
| 3 | 5 minutes |
| 4 | 3 minutes 30 seconds |

## Blocks and liquids

- Original capture platform **cannot** be broken.
- Players may place **temporary** blocks; they disappear after **30 seconds**.
- Other players **may destroy** temporary blocks.
- Plugin must remember placed blocks and restore previous state; survive restart cleanup.
- **Liquids (Karim):** water and lava **are allowed** and “aren’t an issue” — not blocked by the capture rules. They are simply not used by the capture progress system. (**Confirmed**; supersedes “won’t do anything / maybe block” ambiguity.)

Explosions, pistons, fire, and temporary cover interactions still need careful protection design when implementing (**Planned**).

## Rewards

- **$50 total** to the winning team, split across **all four** roster members including offline.
- Rotating remainder: $13 / $13 / $12 / $12 (**Confirmed**; economy not Implemented).
- Standing on the point at completion is **not** required to receive a share.
- Duplicate `captureId` must not pay twice (including across restarts).

## Player feedback

- Progress primarily via **boss bar** (**Confirmed** plugin decision).
- Chat announcements at **25%, 50%, 75%, 100%** (not every percent tick).
- Threshold announcements reset when a different team takes ownership (**Suggestion**).
- Floating text at the point (name, capturing team %, headcounts) — **Planned**.
- Example open message: `[LarpSMP] Ruins is now open! Capture reward: $50 for your team.`

## Persistence

Remember active point, progress, completed points, and temporary blocks across restarts. **Suggestion:** pause progress during downtime and resume from saved state. Missed scheduled sessions should not all auto-open at once (**Open** exact policy).
