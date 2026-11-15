# Combat and kills

**Status:** Rules **Confirmed**. Kill attribution and death-money settlement **not Implemented** (economy endpoint planned; attribution owned by Azeem’s systems).

## Money on kills

- The **victim’s tier** sets the transfer amount ($40 / $30 / $20 / $15).
- The killer’s tier does **not** change the payout.
- **Friendly fire is allowed**, but **teammate kills transfer no money**.
- Ordinary environmental deaths with no qualifying player attribution transfer **no money**.

## Attribution window

**Confirmed:** The last player who attacked the victim receives credit if the victim dies **within 60 seconds** of that attack. This covers indirect deaths (knock into lava, fall after a hit, etc.).

**Confirmed:** Karim’s rule that the last hit gets the money, with the 60-second window for remembering the last attacker.

## What should count as player-caused

**Confirmed** plugin decisions — credit when ownership can be determined:

- Direct melee
- Projectiles (credit shooter)
- Pets (credit owner)
- TNT / explosions (credit igniter/placer)
- Placed lava / fire (credit placer)
- Fall damage after a hit within 60 seconds
- Player-created traps
- Redstone / dispenser traps (credit who last placed/configured the important component)

**Suggestion:** Track trap ownership until the important block is broken or replaced. If ownership cannot be determined reliably, treat as environmental — **no money**.

**Suggestion:** Keep a short death log entry such as “last attacked by PlayerName 8 seconds ago” for admin corrections.

Active combat memory only needs ~60 seconds. Completed death transactions stay in logs for the whole event.

## Kill-money controls

**Confirmed:** Support multiple scopes:

| Scope | Intent |
|-------|--------|
| Global | Whole server |
| Event | Registered event participants |
| Player override | Debugging / unusual cases |

**Suggested priority:** individual override → event → global.

**Confirmed behavior when disabled:** victim loses nothing, teammates lose nothing, killer receives nothing, rescue is **not** checked.

**Open:** Whether the individual override is authoritative for victim, killer, or both. Recommendation: victim’s setting is authoritative because the victim’s wallet is charged.

Every toggle change should log admin, scope, old/new value, optional reason, and time.

## Leaderboard interaction

**Confirmed (Karim):** Event kills **do not** count on the normal `/top kills` leaderboard.

## Rules handled by admins (not automatic plugin shields)

| Rule | Handling |
|------|----------|
| No killing the same person again within 15 minutes without a reason (attacking, stealing, griefing) | Admins judge exceptions — **no automatic repeat-kill shield** planned from this rule |
| Combat logging | Against the rules; **manual** admin handling. Plugin will **not** auto-kill, fine, or punish disconnects |
| Keep inventory | Only for events that need it; restore previous setting afterward |

## Economy settlement contract

Attribution produces a confirmed result (`deathId`, victim, killer, tier, teams, cause, evidence). Economy decides whether/how money moves. See [Economy](economy.md) and [Integration contracts](../03-architecture/integration-contracts.md).
