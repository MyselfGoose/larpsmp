# Economy architecture

**Status:** Core wallet service **Implemented**. Settlement/split/rescue APIs **Planned**.

## Authoritative entry point

[`MoneyService`](../../src/main/java/com/larpsmp/moneyevent/wallet/MoneyService.java) is the only approved path for balance changes today.

### Implemented operations

| Method | Purpose |
|--------|---------|
| `balance` / `account` / `accountByAccountId` | Read |
| `registeredAccounts` | List wallets with Minecraft identity |
| `teamWealth(teamId)` | Sum roster balances (offline included) |
| `add` / `remove` / `set` | Admin-style single-wallet mutations |
| `transfer` | Player `/pay` and similar |
| `transaction` / `allTransactions` | Ledger reads |
| `touchIdentityOnJoin` | Update last-seen name; does not create wallets |
| Balance change listeners | Drive sidebar updates |

Constants: `STARTING_BALANCE = 200`.

Public identifiers in the command layer are Minecraft UUIDs; persistence keys are **account IDs**.

### Implemented transaction types

[`TransactionType`](../../src/main/java/com/larpsmp/moneyevent/wallet/TransactionType.java):

- `STARTING_BALANCE`
- `ADD`
- `REMOVE`
- `SET`
- `TRANSFER`

Ledger columns (migration `V003`) include type, status, amount, reason, actor/source/destination account IDs, before/after balances, failure reason, and audit fields (`authorization_action`, `command`, `actor_username`, `sender_type`).

### Commands

| Command | Status |
|---------|--------|
| `/balance`, `/balance <player>` | Implemented |
| `/pay <player> <amount>` | Implemented (offline OK; combat OK) |
| `/larp money give\|take\|set` | Implemented |
| `/top money`, `/top teams` | Planned |
| `/larp money history` | Planned |
| `/larp visibility teamtotals` | Deferred |
| `/larp killmoney …`, `/larp killreward …` | Deferred / Planned |

Offline payment notifications: [`PaymentNotificationStore`](../../src/main/java/com/larpsmp/moneyevent/notification/PaymentNotificationStore.java) under the plugin data folder.

Sidebar: [`BukkitBalanceSidebar`](../../src/main/java/com/larpsmp/moneyevent/display/BukkitBalanceSidebar.java) — stacked Team Money + Wallet.

## Planned economy service surface

Future callers (kills, captures, auctions, events) must **not** write wallet fields directly. Planned operations from the economy phase plan:

| Operation | Intent |
|-----------|--------|
| `splitReward` | Rotating whole-dollar team split; persist rotation index |
| `processDeathPayment` | Victim + teammate cover + killer payout; idempotent on `deathId` |
| `processRescue` | $25 × 4 × 3 teams; linked to death; no recursion |
| Stronger audit types | `DEATH_PAYMENT`, `CAPTURE_REWARD`, `RESCUE_PAYMENT`, `AUCTION_PAYMENT`, … |

### Invariants for future mutations

1. Validate the complete operation.
2. Compute every balance change.
3. Ensure nobody goes negative.
4. Apply the complete operation together (no half-applied `/pay`).
5. Persist.
6. Write the transaction log (parent/child for deaths).
7. Return a clear result; fail closed on storage errors.

## Integration expectations

- **Auth** supplies permanent account ID, username, auth completion, offline lookup by username.
- **Teams** supplies team membership, roster of four, tier, (eventually) competitor vs admin/spectator.
- **Kills / captures / auctions** supply stable IDs so payouts are idempotent across retries and restarts.

See [Integration contracts](integration-contracts.md).

## What economy does not own

- Kill attribution evidence
- Capture progress / boss bars / temporary blocks
- Auction lot UX / item delivery (unless later chosen)
- Ace Hunt / Gladiator matchmaking state machines
