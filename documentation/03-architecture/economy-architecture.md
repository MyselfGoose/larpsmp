# Economy architecture

**Status:** Core wallet service **Implemented**. Settlement/split/rescue APIs **Implemented** on `feature/economy-settlement` (callers for kills/captures still queued).

## Authoritative entry point

[`MoneyService`](../../src/main/java/com/larpsmp/moneyevent/wallet/MoneyService.java) is the only approved path for balance changes today.

### Implemented operations

| Method | Purpose |
|--------|---------|
| `balance` / `account` / `accountByAccountId` | Read |
| `registeredAccounts` / `competitorStandings` | List wallets |
| `teamWealth(teamId)` | Sum roster balances (offline included) |
| `add` / `remove` / `set` | Admin-style single-wallet mutations |
| `transfer` | Player `/pay` and similar |
| `grantCompetitorStartingBalanceIfNeeded` | One-time `$200` on first team assignment |
| `splitReward` / `processCaptureReward` | Rotating whole-dollar team credits; idempotent external ids |
| `processDeathPayment` | Victim + teammate cover + killer payout; rescue; idempotent `deathId` |
| Kill-money / killreward / visibility setters | Admin scopes and death prices |
| `transaction` / `allTransactions` | Ledger reads |
| `touchIdentityOnJoin` | Update last-seen name; does not create wallets |
| Balance / settlement notice listeners | Drive sidebar + contributor messages |

Constants: [`EconomyRules`](../../src/main/java/com/larpsmp/moneyevent/wallet/EconomyRules.java) (`STARTING_BALANCE = 200`, death/rescue/capture amounts).

Public identifiers in the command layer are Minecraft UUIDs; persistence keys are **account IDs**.

### Implemented transaction types

[`TransactionType`](../../src/main/java/com/larpsmp/moneyevent/wallet/TransactionType.java):

- `STARTING_BALANCE`, `ADD`, `REMOVE`, `SET`, `TRANSFER`
- `DEATH_PAYMENT`, `TEAM_REWARD`, `CAPTURE_REWARD`, `EVENT_REWARD`, `RESCUE_PAYMENT`, `AUCTION_PAYMENT`, `REVERSAL`

Ledger columns (migrations `V003` + `V007`) include type, status, amount, reason, actor/source/destination account IDs, before/after balances, failure reason, audit fields, `parent_transaction_id`, and external refs.

### Commands

| Command | Status |
|---------|--------|
| `/balance`, `/balance <player>` | Implemented |
| `/pay <player> <amount>` | Implemented (offline OK; combat OK) |
| `/larp money give\|take\|set` | Implemented |
| `/top money`, `/top teams` | Implemented |
| `/larp visibility teamtotals show\|hide` | Implemented |
| `/larp killmoney …`, `/larp killreward set` | Implemented |
| `/larp money history` | Planned |

Offline payment notifications: [`PaymentNotificationStore`](../../src/main/java/com/larpsmp/moneyevent/notification/PaymentNotificationStore.java) under the plugin data folder.

Sidebar: [`BukkitBalanceSidebar`](../../src/main/java/com/larpsmp/moneyevent/display/BukkitBalanceSidebar.java) — stacked Team Money + Wallet (Team Money hidden when visibility is off).

### Mutation invariants

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
