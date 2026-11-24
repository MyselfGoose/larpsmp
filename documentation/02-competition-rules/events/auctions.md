# Mystery shulker auctions

**Status:** Event **Confirmed**. Economy payment rules **Confirmed**. Auction automation **Implemented** on `feature/auctions` (`/bid`, fixed reps, lot lifecycle, `/pay` proof to host, previous-bidder fallback). Item delivery remains **manual** for v1.

## Idea

Amira hosts. Three representatives (one per team), nominated by captains, bid on **mystery shulkers**. Contents stay hidden; bidders get a short **theme or category** hint.

Organizer contact email from draft: `amiraarts8@gmail.com`. In-game payment recipient defaults to **`akiracreates`**. Change at runtime with `/larp auction payee <player>` (persisted in Postgres; mirrored to `auction.payment-username` in `config.yml`).

## Money rules

| Rule | Status |
|------|--------|
| Bid from personal wallet only | Confirmed · Implemented |
| Teammates may `/pay` the representative to fund | Confirmed |
| Whether teammates may `/pay` **during** live bidding | **v1: allowed** (no plugin restriction; revisable if Karim decides otherwise) |
| Bids above current wallet **rejected** | Confirmed · Implemented |
| Payment required **immediately** after win | Confirmed · Implemented (awaiting-payment state; admin forfeit) |
| If winner cannot pay → offer to **previous bidder** | Confirmed · Implemented (`/larp auction forfeit`) |
| Payment to Amira; leaves competing wealth | Confirmed · Implemented via `/pay` match |
| Representatives **cannot** change between lots | Confirmed · Implemented (locked after first lot opens) |
| No escrow while bidding (winner pays afterward) | Confirmed · Implemented |
| New bids **not** accepted during final countdown | Confirmed · Implemented (`/larp auction countdown`) |
| Item delivery manual vs plugin-controlled | **v1: manual** (`/larp auction delivered` tracks handoff only) |

## Lot workflow (practical)

1. Amira chooses shulker and public theme/name.
2. Representatives brought to spawn; cannot hit one another.
3. Admin: `/larp auction session start`, register reps, `/larp auction open <theme> <startingBid>`.
4. Representatives bid with `/bid <amount>`.
5. Admin: `/larp auction countdown` then `/larp auction close` (or close directly).
6. Winner pays exact amount to the current payee (`/larp auction payee` / default `akiracreates`) with `/pay <payee> <amount>` — plugin marks paid.
7. After payment, Amira delivers the shulker manually; optional `/larp auction delivered`.

## Plugin commands (Implemented)

| Command | Role |
|---------|------|
| `/larp auction session start` | Start session |
| `/larp auction rep <player>` | Register fixed team representative |
| `/larp auction payee [player]` | Show or set payment recipient (must be a registered account with a wallet) |
| `/larp auction open <theme…> <startingBid>` | Open named lot |
| `/larp auction countdown` | Lock new bids |
| `/larp auction close` | Close lot; await payment or unsold |
| `/larp auction forfeit` | Winner cannot pay → previous bidder |
| `/larp auction delivered` | Mark paid lot delivered (tracking only) |
| `/larp auction status` / `end` | Inspect / end session |
| `/bid <amount>` | Representative bid |

Public chat uses the same aqua/gold standings style as `/top`, e.g.:

```text
[Auction] Lot 2 — "Something Explosive" · start $20
[Auction] Blue bids $35
[Auction] Sold to Red for $60 — pay akiracreates
```

Admin feedback (`/larp auction status`, `rep`, `payee`, …) uses titled sections (`— Auction status —`) instead of raw UUID dumps.

Minimum bid increase for v1: whole dollars, strictly greater than current high bid (or `>=` starting bid if none).

Themes and item pools are design work, not promised catalogs.

## Still to settle before first auction

- Exact shulker contents / risk communication
- Outsider access to auction area
- Teammate advice / communication rules
- Return after teleport
- Whether Karim wants mid-bid `/pay` blocked later
