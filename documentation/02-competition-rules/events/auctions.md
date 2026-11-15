# Mystery shulker auctions

**Status:** Event **Confirmed**. Economy payment rules **Confirmed**. Auction automation **not Implemented** (manual admin + `/pay` may suffice initially).

## Idea

Amira hosts. Three representatives (one per team), nominated by captains, bid on **mystery shulkers**. Contents stay hidden; bidders get a short **theme or category** hint.

Organizer contact email from draft: `amiraarts8@gmail.com`. In-game payment recipient: **`akiracreates`**.

## Money rules

| Rule | Status |
|------|--------|
| Bid from personal wallet only | Confirmed |
| Teammates may `/pay` the representative to fund | Confirmed |
| Whether teammates may `/pay` **during** live bidding | **Open** |
| Bids above current wallet **rejected** | Confirmed |
| Payment required **immediately** after win | Confirmed |
| If winner cannot pay → offer to **previous bidder** | Confirmed |
| Payment to Amira; leaves competing wealth | Confirmed |
| Representatives **cannot** change between lots | Confirmed |
| No escrow while bidding (winner pays afterward) | Confirmed original request |
| New bids **not** accepted during final countdown | Confirmed |
| Item delivery manual vs plugin-controlled | **Open** |

## Lot workflow (practical)

1. Amira chooses shulker and public theme/name.
2. Representatives brought to spawn; cannot hit one another.
3. Amira opens lot: starting price and bid rules.
4. Representatives bid with `/bid <amount>` (**Confirmed** command name request; not Implemented).
5. Amira counts to three slowly and closes bidding manually.
6. Winner pays final amount to Amira.
7. After payment check, winner receives the shulker.

## Plugin help (when automated)

**Suggestion / Planned:** start named lot, register reps, show highest valid bid, close bidding, mark payment/delivery. Public chat shows high bid without revealing contents.

Example messages:

```text
[Auction] Lot 2: "Something Explosive". Starting bid: $20.
[Auction] Blue bids $35.
[Auction] Sold to Red for $60. Waiting for payment.
```

Themes and item pools are design work, not promised catalogs.

## Still to settle before first auction

- Minimum bid increase
- Payment deadline details (immediate pay is confirmed; grace behavior **Open**)
- Exact shulker contents / risk communication
- Outsider access to auction area
- Teammate advice / communication rules
- Return after teleport

If auctions run fully manually, the plugin only needs wallets and `/pay` until automation is worth building.
