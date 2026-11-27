package com.larpsmp.moneyevent.wallet;

import com.larpsmp.moneyevent.auth.AccountIdentity;
import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamMember;
import com.larpsmp.moneyevent.team.TeamService;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Authoritative money operations against account-linked Postgres wallets.
 * Public identifiers are Minecraft profile UUIDs; persistence keys are account IDs.
 */
public final class MoneyService implements AutoCloseable {
    public static final long STARTING_BALANCE = EconomyRules.STARTING_BALANCE;
    public static final String STARTING_BALANCE_REASON = EconomyRules.STARTING_BALANCE_REASON;

    private final AccountRepository accounts;
    private final JdbcMoneyRepository money;
    private final Consumer<String> storageErrorLogger;
    private final List<BalanceChangeListener> balanceListeners = new CopyOnWriteArrayList<>();
    private final List<SettlementNoticeListener> settlementNoticeListeners = new CopyOnWriteArrayList<>();
    private final List<TransferSuccessListener> transferSuccessListeners = new CopyOnWriteArrayList<>();
    private volatile @Nullable TeamService teams;
    private boolean closed;

    public MoneyService(
            AccountRepository accounts,
            JdbcMoneyRepository money,
            Consumer<String> storageErrorLogger) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.money = Objects.requireNonNull(money, "money");
        this.storageErrorLogger = Objects.requireNonNull(storageErrorLogger, "storageErrorLogger");
    }

    public void setTeamService(TeamService teamService) {
        this.teams = Objects.requireNonNull(teamService, "teamService");
    }

    public void addBalanceChangeListener(BalanceChangeListener listener) {
        balanceListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void addSettlementNoticeListener(SettlementNoticeListener listener) {
        settlementNoticeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void addTransferSuccessListener(TransferSuccessListener listener) {
        transferSuccessListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public synchronized BalanceResult balance(UUID minecraftUuid) {
        try {
            Optional<WalletAccount> wallet = account(minecraftUuid);
            return wallet.map(value -> new BalanceResult(
                            TransactionStatus.SUCCESS, minecraftUuid, value.balance(), ""))
                    .orElseGet(() -> new BalanceResult(
                            TransactionStatus.WALLET_NOT_FOUND, minecraftUuid, null, "Wallet not found"));
        } catch (IOException exception) {
            logStorageFailure("read wallet " + minecraftUuid, exception);
            return new BalanceResult(TransactionStatus.STORAGE_FAILURE, minecraftUuid, null, exception.getMessage());
        }
    }

    public synchronized Optional<WalletAccount> account(UUID minecraftUuid) throws IOException {
        ensureOpen();
        try {
            return money.findWalletByMinecraftUuid(minecraftUuid).map(WalletAccount::from);
        } catch (SQLException exception) {
            throw storageException("load wallet for " + minecraftUuid, exception);
        }
    }

    public synchronized Optional<WalletAccount> accountByAccountId(UUID accountId) throws IOException {
        ensureOpen();
        try {
            return money.findWalletByAccountId(accountId).map(WalletAccount::from);
        } catch (SQLException exception) {
            throw storageException("load wallet for account " + accountId, exception);
        }
    }

    public synchronized List<WalletAccount> registeredAccounts() throws IOException {
        ensureOpen();
        try {
            return money.loadAllWallets().stream()
                    .filter(row -> row.minecraftUuid() != null)
                    .map(WalletAccount::from)
                    .toList();
        } catch (SQLException exception) {
            throw storageException("list wallets", exception);
        }
    }

    public synchronized List<WalletAccount> competitorStandings() throws IOException {
        ensureOpen();
        try {
            return money.loadCompetitorWalletsByBalanceDesc().stream()
                    .map(WalletAccount::from)
                    .toList();
        } catch (SQLException exception) {
            throw storageException("list competitor wallets", exception);
        }
    }

    /**
     * Combined personal balances of every roster member on {@code teamId} (offline included).
     * Empty rosters return 0.
     */
    public synchronized long teamWealth(UUID teamId) throws IOException {
        ensureOpen();
        try {
            return money.sumBalancesForTeam(Objects.requireNonNull(teamId, "teamId"));
        } catch (SQLException exception) {
            throw storageException("sum team wealth for " + teamId, exception);
        }
    }

    public synchronized boolean teamTotalsVisible() throws IOException {
        ensureOpen();
        try {
            return money.loadSettings().teamTotalsVisible();
        } catch (SQLException exception) {
            throw storageException("load team totals visibility", exception);
        }
    }

    public synchronized void setTeamTotalsVisible(
            boolean visible, String actorUsername, String senderType, String reason) throws IOException {
        ensureOpen();
        try {
            boolean previous = money.loadSettings().teamTotalsVisible();
            money.updateTeamTotalsVisible(visible);
            money.insertAdminAudit(new JdbcMoneyRepository.AdminAuditRow(
                    UUID.randomUUID(),
                    "TEAM_TOTALS_VISIBILITY",
                    "global",
                    Boolean.toString(previous),
                    Boolean.toString(visible),
                    reason == null ? "" : reason,
                    actorUsername == null ? "" : actorUsername,
                    senderType == null ? "" : senderType,
                    Instant.now()));
        } catch (SQLException exception) {
            throw storageException("set team totals visibility", exception);
        }
    }

    public synchronized boolean killMoneyGlobal() throws IOException {
        ensureOpen();
        try {
            return money.loadSettings().killMoneyGlobal();
        } catch (SQLException exception) {
            throw storageException("load kill money global", exception);
        }
    }

    public synchronized void setKillMoneyGlobal(
            boolean enabled, String actorUsername, String senderType, String reason) throws IOException {
        ensureOpen();
        try {
            boolean previous = money.loadSettings().killMoneyGlobal();
            money.updateKillMoneyGlobal(enabled);
            audit("KILL_MONEY_GLOBAL", "global", Boolean.toString(previous), Boolean.toString(enabled),
                    reason, actorUsername, senderType);
        } catch (SQLException exception) {
            throw storageException("set kill money global", exception);
        }
    }

    public synchronized void setKillMoneyEvent(
            String eventId, boolean enabled, String actorUsername, String senderType, String reason)
            throws IOException {
        ensureOpen();
        try {
            Optional<Boolean> previous = money.findEventKillMoney(eventId);
            money.setEventKillMoney(eventId, enabled);
            audit("KILL_MONEY_EVENT", eventId,
                    previous.map(Object::toString).orElse(""),
                    Boolean.toString(enabled), reason, actorUsername, senderType);
        } catch (SQLException exception) {
            throw storageException("set kill money event " + eventId, exception);
        }
    }

    public synchronized void addKillMoneyEventParticipant(String eventId, UUID accountId) throws IOException {
        ensureOpen();
        try {
            money.addEventParticipant(eventId, accountId);
        } catch (SQLException exception) {
            throw storageException("add event participant", exception);
        }
    }

    public synchronized void removeKillMoneyEventParticipant(String eventId, UUID accountId) throws IOException {
        ensureOpen();
        try {
            money.removeEventParticipant(eventId, accountId);
        } catch (SQLException exception) {
            throw storageException("remove event participant", exception);
        }
    }

    public synchronized void clearKillMoneyEventParticipants(String eventId) throws IOException {
        ensureOpen();
        try {
            money.clearEventParticipants(eventId);
        } catch (SQLException exception) {
            throw storageException("clear event participants", exception);
        }
    }

    public synchronized void setPlayerKillMoneyOverride(
            UUID accountId, boolean enabled, String actorUsername, String senderType, String reason)
            throws IOException {
        ensureOpen();
        try {
            Optional<Boolean> previous = money.findPlayerKillMoneyOverride(accountId);
            money.setPlayerKillMoneyOverride(accountId, enabled);
            audit("KILL_MONEY_PLAYER", accountId.toString(),
                    previous.map(Object::toString).orElse(""),
                    Boolean.toString(enabled), reason, actorUsername, senderType);
        } catch (SQLException exception) {
            throw storageException("set player kill money override", exception);
        }
    }

    public synchronized void clearPlayerKillMoneyOverride(
            UUID accountId, String actorUsername, String senderType, String reason) throws IOException {
        ensureOpen();
        try {
            Optional<Boolean> previous = money.findPlayerKillMoneyOverride(accountId);
            money.clearPlayerKillMoneyOverride(accountId);
            audit("KILL_MONEY_PLAYER_CLEAR", accountId.toString(),
                    previous.map(Object::toString).orElse(""),
                    "", reason, actorUsername, senderType);
        } catch (SQLException exception) {
            throw storageException("clear player kill money override", exception);
        }
    }

    public synchronized long deathPrice(PlayerTier tier) throws IOException {
        ensureOpen();
        try {
            Map<Integer, Long> prices = money.loadKillPrices();
            return prices.getOrDefault(tier.value(), EconomyRules.defaultDeathPrice(tier));
        } catch (SQLException exception) {
            throw storageException("load death price", exception);
        }
    }

    public synchronized Map<Integer, Long> killPrices() throws IOException {
        ensureOpen();
        try {
            return money.loadKillPrices();
        } catch (SQLException exception) {
            throw storageException("load kill prices", exception);
        }
    }

    public synchronized void setKillReward(
            int tier, long amount, String actorUsername, String senderType, String reason)
            throws IOException {
        ensureOpen();
        if (tier < 1 || tier > 4) {
            throw new IllegalArgumentException("tier must be 1–4");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        try {
            long previous = money.loadKillPrices().getOrDefault(tier, EconomyRules.defaultDeathPrice(tier));
            money.setKillPrice(tier, amount);
            audit("KILL_REWARD_SET", "tier-" + tier, Long.toString(previous), Long.toString(amount),
                    reason, actorUsername, senderType);
        } catch (SQLException exception) {
            throw storageException("set kill reward", exception);
        }
    }

    public synchronized KillMoneyStatus killMoneyStatus() throws IOException {
        ensureOpen();
        try {
            JdbcMoneyRepository.EconomySettingsRow settings = money.loadSettings();
            return new KillMoneyStatus(
                    settings.killMoneyGlobal(),
                    settings.teamTotalsVisible(),
                    settings.rewardRotationIndex(),
                    money.loadKillPrices());
        } catch (SQLException exception) {
            throw storageException("load kill money status", exception);
        }
    }

    /**
     * Grants the one-time competitor starting balance if not already granted.
     *
     * @return true when a grant was applied
     */
    public synchronized boolean grantCompetitorStartingBalanceIfNeeded(UUID accountId) throws IOException {
        ensureOpen();
        Objects.requireNonNull(accountId, "accountId");
        try {
            Optional<JdbcMoneyRepository.WalletRow> walletOpt = money.findWalletByAccountId(accountId);
            if (walletOpt.isEmpty()) {
                return false;
            }
            JdbcMoneyRepository.WalletRow wallet = walletOpt.get();
            if (wallet.startingBalanceGranted()) {
                return false;
            }
            Instant now = Instant.now();
            long after = Math.addExact(wallet.balance(), STARTING_BALANCE);
            UUID txId = UUID.randomUUID();
            TransactionRecord record = new TransactionRecord(
                    txId, now, TransactionType.STARTING_BALANCE, TransactionStatus.SUCCESS,
                    STARTING_BALANCE, STARTING_BALANCE_REASON, null, accountId, null,
                    wallet.balance(), after, null, null, "");
            Map<UUID, Long> balances = Map.of(accountId, after);
            money.commitSettlement(JdbcMoneyRepository.SettlementCommit.withStartingBalanceGrant(
                    balances, List.of(record), accountId));
            notifyBalanceChanged(wallet.minecraftUuid(), after);
            return true;
        } catch (SQLException exception) {
            throw storageException("grant starting balance for " + accountId, exception);
        }
    }

    /**
     * Updates the Minecraft identity last-seen/name when a bound player joins. Does not create wallets.
     */
    public synchronized Optional<WalletAccount> touchIdentityOnJoin(UUID minecraftUuid, String minecraftName)
            throws IOException {
        ensureOpen();
        try {
            Optional<AccountIdentity> identity = accounts.findIdentityByMinecraftUuid(minecraftUuid);
            if (identity.isEmpty()) {
                return Optional.empty();
            }
            accounts.updateIdentityLastSeen(identity.orElseThrow().id(), minecraftName);
            return money.findWalletByMinecraftUuid(minecraftUuid).map(WalletAccount::from);
        } catch (SQLException exception) {
            throw storageException("touch identity for " + minecraftUuid, exception);
        }
    }

    public synchronized Optional<TransactionRecord> transaction(UUID transactionId) throws IOException {
        ensureOpen();
        try {
            return money.loadTransaction(transactionId);
        } catch (SQLException exception) {
            throw storageException("load transaction " + transactionId, exception);
        }
    }

    public synchronized List<TransactionRecord> allTransactions() throws IOException {
        ensureOpen();
        try {
            return money.loadAllTransactions();
        } catch (SQLException exception) {
            throw storageException("list transactions", exception);
        }
    }

    public synchronized TransactionResult add(UUID minecraftUuid, long amount, String reason, UUID actorMinecraftUuid) {
        return add(minecraftUuid, amount, reason, actorMinecraftUuid, MoneyAuditContext.EMPTY);
    }

    public synchronized TransactionResult add(
            UUID minecraftUuid, long amount, String reason, UUID actorMinecraftUuid, MoneyAuditContext context) {
        return changeOne(TransactionType.ADD, minecraftUuid, amount, reason, actorMinecraftUuid, context);
    }

    public synchronized TransactionResult remove(
            UUID minecraftUuid, long amount, String reason, UUID actorMinecraftUuid) {
        return remove(minecraftUuid, amount, reason, actorMinecraftUuid, MoneyAuditContext.EMPTY);
    }

    public synchronized TransactionResult remove(
            UUID minecraftUuid, long amount, String reason, UUID actorMinecraftUuid, MoneyAuditContext context) {
        return changeOne(TransactionType.REMOVE, minecraftUuid, amount, reason, actorMinecraftUuid, context);
    }

    public synchronized TransactionResult set(
            UUID minecraftUuid, long balance, String reason, UUID actorMinecraftUuid) {
        return set(minecraftUuid, balance, reason, actorMinecraftUuid, MoneyAuditContext.EMPTY);
    }

    public synchronized TransactionResult set(
            UUID minecraftUuid, long balance, String reason, UUID actorMinecraftUuid, MoneyAuditContext context) {
        return changeOne(TransactionType.SET, minecraftUuid, balance, reason, actorMinecraftUuid, context);
    }

    public synchronized TransactionResult transfer(
            UUID sourceMinecraftUuid,
            UUID destinationMinecraftUuid,
            long amount,
            String reason,
            UUID actorMinecraftUuid) {
        return twoPartyTransfer(
                TransactionType.TRANSFER,
                sourceMinecraftUuid,
                destinationMinecraftUuid,
                amount,
                reason,
                actorMinecraftUuid,
                true);
    }

    /**
     * Auction lot settlement transfer ({@link TransactionType#AUCTION_PAYMENT}).
     * Does not fire {@code /pay} transfer-success listeners — auction settlement is owned by
     * {@code AuctionService.payOutstanding}, not by matching player {@code /pay} commands.
     */
    public synchronized TransactionResult auctionPayment(
            UUID sourceMinecraftUuid,
            UUID destinationMinecraftUuid,
            long amount,
            String reason,
            UUID actorMinecraftUuid) {
        return twoPartyTransfer(
                TransactionType.AUCTION_PAYMENT,
                sourceMinecraftUuid,
                destinationMinecraftUuid,
                amount,
                reason,
                actorMinecraftUuid,
                false,
                null);
    }

    /**
     * Auction lot payment that records the {@code auction} settlement in the same database
     * transaction as the wallet transfer, so a failed settlement insert cannot leave money
     * moved without an idempotency key.
     */
    public synchronized TransactionResult auctionPaymentWithSettlement(
            UUID sourceMinecraftUuid,
            UUID destinationMinecraftUuid,
            long amount,
            String reason,
            UUID actorMinecraftUuid,
            String lotId) {
        Objects.requireNonNull(lotId, "lotId");
        if (lotId.isBlank() || amount <= 0) {
            return failure(
                    UUID.randomUUID(),
                    Instant.now(),
                    TransactionType.AUCTION_PAYMENT,
                    amount,
                    reason,
                    null,
                    null,
                    null,
                    null,
                    null,
                    TransactionStatus.INVALID_AMOUNT,
                    "Invalid auction payment settlement request",
                    MoneyAuditContext.EMPTY);
        }
        return twoPartyTransfer(
                TransactionType.AUCTION_PAYMENT,
                sourceMinecraftUuid,
                destinationMinecraftUuid,
                amount,
                reason,
                actorMinecraftUuid,
                false,
                lotId);
    }

    private TransactionResult twoPartyTransfer(
            TransactionType type,
            UUID sourceMinecraftUuid,
            UUID destinationMinecraftUuid,
            long amount,
            String reason,
            UUID actorMinecraftUuid,
            boolean notifyPayListeners) {
        return twoPartyTransfer(
                type,
                sourceMinecraftUuid,
                destinationMinecraftUuid,
                amount,
                reason,
                actorMinecraftUuid,
                notifyPayListeners,
                null);
    }

    private TransactionResult twoPartyTransfer(
            TransactionType type,
            UUID sourceMinecraftUuid,
            UUID destinationMinecraftUuid,
            long amount,
            String reason,
            UUID actorMinecraftUuid,
            boolean notifyPayListeners,
            @Nullable String auctionLotId) {
        UUID transactionId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        if (sourceMinecraftUuid.equals(destinationMinecraftUuid)) {
            return failure(transactionId, timestamp, type, amount, reason, null,
                    null, null, null, null, TransactionStatus.SAME_WALLET,
                    "Source and destination wallets must be different", MoneyAuditContext.EMPTY);
        }
        TransactionResult validation = validate(transactionId, timestamp, type,
                amount, reason, null, null, false, MoneyAuditContext.EMPTY);
        if (validation != null) {
            return validation;
        }

        JdbcMoneyRepository.WalletRow source;
        JdbcMoneyRepository.WalletRow destination;
        UUID actorAccountId;
        try {
            source = money.findWalletByMinecraftUuid(sourceMinecraftUuid).orElse(null);
            destination = money.findWalletByMinecraftUuid(destinationMinecraftUuid).orElse(null);
            actorAccountId = resolveActorAccountId(actorMinecraftUuid);
        } catch (SQLException exception) {
            return storageFailure(transactionId, timestamp, type, amount, reason,
                    null, null, null, null, null, exception, MoneyAuditContext.EMPTY);
        }
        if (source == null || destination == null) {
            return failure(transactionId, timestamp, type, amount, reason, actorAccountId,
                    source == null ? null : source.accountId(),
                    destination == null ? null : destination.accountId(),
                    source == null ? null : source.balance(),
                    destination == null ? null : destination.balance(),
                    TransactionStatus.WALLET_NOT_FOUND,
                    source == null ? "Source wallet not found" : "Destination wallet not found",
                    MoneyAuditContext.EMPTY);
        }

        long sourceBefore = source.balance();
        long destinationBefore = destination.balance();
        if (sourceBefore < amount) {
            return failure(transactionId, timestamp, type, amount, reason, actorAccountId,
                    source.accountId(), destination.accountId(), sourceBefore, destinationBefore,
                    TransactionStatus.INSUFFICIENT_FUNDS, "Source wallet has insufficient funds",
                    MoneyAuditContext.EMPTY);
        }
        long destinationAfter;
        try {
            destinationAfter = Math.addExact(destinationBefore, amount);
        } catch (ArithmeticException exception) {
            return failure(transactionId, timestamp, type, amount, reason, actorAccountId,
                    source.accountId(), destination.accountId(), sourceBefore, destinationBefore,
                    TransactionStatus.INVALID_AMOUNT, "Amount would overflow destination balance",
                    MoneyAuditContext.EMPTY);
        }
        long sourceAfter = sourceBefore - amount;
        TransactionRecord success = record(transactionId, timestamp, type,
                TransactionStatus.SUCCESS, amount, reason, actorAccountId,
                source.accountId(), destination.accountId(),
                sourceBefore, sourceAfter, destinationBefore, destinationAfter, "", MoneyAuditContext.EMPTY);
        JdbcMoneyRepository.SettlementRow auctionSettlement = null;
        if (auctionLotId != null && !auctionLotId.isBlank()) {
            try {
                Optional<JdbcMoneyRepository.SettlementRow> existing =
                        money.findSettlement(EconomyRules.SETTLEMENT_KIND_AUCTION, auctionLotId);
                if (existing.isPresent()) {
                    return failure(
                            transactionId,
                            timestamp,
                            type,
                            amount,
                            reason,
                            actorAccountId,
                            source.accountId(),
                            destination.accountId(),
                            sourceBefore,
                            destinationBefore,
                            TransactionStatus.INVALID_AMOUNT,
                            "Auction lot already settled",
                            MoneyAuditContext.EMPTY);
                }
            } catch (SQLException exception) {
                return storageFailure(transactionId, timestamp, type, amount, reason,
                        actorAccountId, source.accountId(), destination.accountId(),
                        sourceBefore, destinationBefore, exception, MoneyAuditContext.EMPTY);
            }
            auctionSettlement = new JdbcMoneyRepository.SettlementRow(
                    EconomyRules.SETTLEMENT_KIND_AUCTION,
                    auctionLotId,
                    EconomyRules.OUTCOME_APPLIED,
                    transactionId,
                    amount,
                    "{\"paymentTransactionId\":\"" + transactionId + "\"}",
                    timestamp);
        }
        try {
            money.commitTransfer(
                    source.accountId(),
                    sourceAfter,
                    destination.accountId(),
                    destinationAfter,
                    success,
                    auctionSettlement);
            notifyBalanceChanged(source.minecraftUuid(), sourceAfter);
            notifyBalanceChanged(destination.minecraftUuid(), destinationAfter);
            if (notifyPayListeners) {
                notifyTransferSuccess(
                        source.minecraftUuid(),
                        destination.minecraftUuid(),
                        source.accountId(),
                        destination.accountId(),
                        amount,
                        transactionId);
            }
            return TransactionResult.from(success);
        } catch (SQLException exception) {
            return storageFailure(transactionId, timestamp, type, amount, reason,
                    actorAccountId, source.accountId(), destination.accountId(),
                    sourceBefore, destinationBefore, exception, MoneyAuditContext.EMPTY);
        }
    }

    public synchronized SplitRewardResult splitReward(
            UUID teamId,
            long amount,
            TransactionType type,
            String reason,
            String externalRefKind,
            String externalRefId) {
        ensureTeams();
        Instant now = Instant.now();
        if (amount <= 0) {
            return new SplitRewardResult(TransactionStatus.INVALID_AMOUNT, "", amount, null,
                    List.of(), 0, 0, "Amount must be positive");
        }
        if (reason == null || reason.isBlank()) {
            return new SplitRewardResult(TransactionStatus.INVALID_REASON, "", amount, null,
                    List.of(), 0, 0, "Reason cannot be blank");
        }
        if (type != TransactionType.TEAM_REWARD
                && type != TransactionType.CAPTURE_REWARD
                && type != TransactionType.EVENT_REWARD
                && type != TransactionType.RESCUE_PAYMENT) {
            return new SplitRewardResult(TransactionStatus.INVALID_REASON, "", amount, null,
                    List.of(), 0, 0, "Unsupported split reward type: " + type);
        }
        try {
            if (externalRefKind != null && !externalRefKind.isBlank()
                    && externalRefId != null && !externalRefId.isBlank()) {
                Optional<JdbcMoneyRepository.SettlementRow> existing =
                        money.findSettlement(externalRefKind, externalRefId);
                if (existing.isPresent()) {
                    JdbcMoneyRepository.SettlementRow row = existing.get();
                    List<SettlementContribution> shares = List.of();
                    if (row.parentTransactionId() != null) {
                        shares = contributionsFromChildren(row.parentTransactionId());
                    }
                    return new SplitRewardResult(
                            TransactionStatus.SUCCESS,
                            row.outcome(),
                            row.chargedTotal(),
                            row.parentTransactionId(),
                            shares,
                            0,
                            money.loadSettings().rewardRotationIndex(),
                            "");
                }
            }

            List<TeamMember> members = teams.competitorsOf(teamId);
            if (members.isEmpty()) {
                return new SplitRewardResult(TransactionStatus.WALLET_NOT_FOUND, "", amount, null,
                        List.of(), 0, 0, "Team has no competitors");
            }
            int rotationBefore = money.loadSettings().rewardRotationIndex();
            Map<UUID, Long> shares = computeRotatingShares(members, amount, rotationBefore);
            int n = members.size();
            int remainder = (int) (amount % n);
            int rotationAfter = n == 0 ? rotationBefore : (rotationBefore + remainder) % n;

            Map<UUID, JdbcMoneyRepository.WalletRow> wallets = new HashMap<>();
            for (UUID accountId : shares.keySet()) {
                JdbcMoneyRepository.WalletRow wallet = money.findWalletByAccountId(accountId).orElse(null);
                if (wallet == null) {
                    return new SplitRewardResult(TransactionStatus.WALLET_NOT_FOUND, "", amount, null,
                            List.of(), rotationBefore, rotationBefore, "Wallet missing for " + accountId);
                }
                wallets.put(accountId, wallet);
            }

            UUID parentId = UUID.randomUUID();
            String refKind = externalRefKind == null ? "" : externalRefKind;
            String refId = externalRefId == null ? "" : externalRefId;
            List<TransactionRecord> ledger = new ArrayList<>();
            Map<UUID, Long> balanceUpdates = new LinkedHashMap<>();
            List<SettlementContribution> contributionList = new ArrayList<>();

            TransactionRecord parent = new TransactionRecord(
                    parentId, now, type, TransactionStatus.SUCCESS, amount, reason, null,
                    null, null, null, null, null, null, "",
                    "", "", "", "", null, refKind, refId);
            ledger.add(parent);

            for (TeamMember member : members) {
                long share = shares.getOrDefault(member.accountId(), 0L);
                if (share <= 0) {
                    continue;
                }
                JdbcMoneyRepository.WalletRow wallet = wallets.get(member.accountId());
                long before = wallet.balance();
                long after = Math.addExact(before, share);
                balanceUpdates.put(member.accountId(), after);
                UUID childId = UUID.randomUUID();
                ledger.add(new TransactionRecord(
                        childId, now, type, TransactionStatus.SUCCESS, share, reason, null,
                        null, member.accountId(), null, null, before, after, "",
                        "", "", "", "", parentId, refKind, refId));
                contributionList.add(new SettlementContribution(
                        member.accountId(), member.username(), share, before, after, true));
            }

            JdbcMoneyRepository.SettlementRow settlement = null;
            if (!refKind.isBlank() && !refId.isBlank()) {
                settlement = new JdbcMoneyRepository.SettlementRow(
                        refKind, refId, EconomyRules.OUTCOME_APPLIED, parentId, amount, reason, now);
            }

            money.commitSettlement(JdbcMoneyRepository.SettlementCommit.withRotation(
                    balanceUpdates, ledger, settlement, rotationAfter));

            for (SettlementContribution contribution : contributionList) {
                JdbcMoneyRepository.WalletRow wallet = wallets.get(contribution.accountId());
                notifyBalanceChanged(wallet.minecraftUuid(), contribution.balanceAfter());
            }

            return new SplitRewardResult(
                    TransactionStatus.SUCCESS,
                    EconomyRules.OUTCOME_APPLIED,
                    amount,
                    parentId,
                    contributionList,
                    rotationBefore,
                    rotationAfter,
                    "");
        } catch (SQLException | ArithmeticException exception) {
            logStorageFailure("splitReward " + teamId, exception);
            return new SplitRewardResult(TransactionStatus.STORAGE_FAILURE, "", amount, null,
                    List.of(), 0, 0, exception.getMessage());
        }
    }

    public synchronized SplitRewardResult processCaptureReward(String captureId, UUID winningTeamId) {
        Objects.requireNonNull(captureId, "captureId");
        if (captureId.isBlank()) {
            return new SplitRewardResult(TransactionStatus.INVALID_REASON, "", 0, null,
                    List.of(), 0, 0, "captureId cannot be blank");
        }
        return splitReward(
                winningTeamId,
                EconomyRules.CAPTURE_REWARD,
                TransactionType.CAPTURE_REWARD,
                "Capture reward",
                EconomyRules.SETTLEMENT_KIND_CAPTURE,
                captureId);
    }

    /**
     * Idempotent Gladiators placement payout for one team.
     * External id is {@code sessionId:teamId}. {@code amount == 0} records a no-op settlement.
     */
    public synchronized SplitRewardResult processGladiatorPrize(String sessionId, UUID teamId, long amount) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(teamId, "teamId");
        if (sessionId.isBlank()) {
            return new SplitRewardResult(TransactionStatus.INVALID_REASON, "", 0, null,
                    List.of(), 0, 0, "sessionId cannot be blank");
        }
        if (amount < 0) {
            return new SplitRewardResult(TransactionStatus.INVALID_AMOUNT, "", amount, null,
                    List.of(), 0, 0, "Amount cannot be negative");
        }
        String externalId = sessionId + ":" + teamId;
        if (amount == 0) {
            ensureOpenUnchecked();
            try {
                Optional<JdbcMoneyRepository.SettlementRow> existing =
                        money.findSettlement(EconomyRules.SETTLEMENT_KIND_GLADIATOR, externalId);
                if (existing.isPresent()) {
                    return new SplitRewardResult(
                            TransactionStatus.SUCCESS,
                            existing.get().outcome(),
                            0,
                            existing.get().parentTransactionId(),
                            List.of(),
                            0,
                            money.loadSettings().rewardRotationIndex(),
                            "");
                }
                money.insertSettlementNoOp(
                        EconomyRules.SETTLEMENT_KIND_GLADIATOR,
                        externalId,
                        EconomyRules.OUTCOME_NO_OP,
                        "Gladiators $0 placement");
                return new SplitRewardResult(
                        TransactionStatus.SUCCESS,
                        EconomyRules.OUTCOME_NO_OP,
                        0,
                        null,
                        List.of(),
                        0,
                        money.loadSettings().rewardRotationIndex(),
                        "");
            } catch (SQLException exception) {
                logStorageFailure("processGladiatorPrize " + externalId, exception);
                return new SplitRewardResult(TransactionStatus.STORAGE_FAILURE, "", 0, null,
                        List.of(), 0, 0, exception.getMessage());
            }
        }
        return splitReward(
                teamId,
                amount,
                TransactionType.EVENT_REWARD,
                "Gladiators placement prize",
                EconomyRules.SETTLEMENT_KIND_GLADIATOR,
                externalId);
    }

    public synchronized DeathPaymentResult processDeathPayment(DeathPaymentRequest request) {
        ensureOpenUnchecked();
        ensureTeams();
        Objects.requireNonNull(request, "request");
        Instant now = Instant.now();
        try {
            Optional<JdbcMoneyRepository.SettlementRow> existing =
                    money.findSettlement(EconomyRules.SETTLEMENT_KIND_DEATH, request.deathId());
            if (existing.isPresent()) {
                return deathResultFromSettlement(existing.get(), request.deathId());
            }

            if (!isKillMoneyEnabledForVictim(request.victimAccountId())) {
                money.insertSettlementNoOp(
                        EconomyRules.SETTLEMENT_KIND_DEATH,
                        request.deathId(),
                        EconomyRules.OUTCOME_SKIPPED_KILL_MONEY_OFF,
                        "Kill money disabled");
                return new DeathPaymentResult(
                        request.deathId(),
                        EconomyRules.OUTCOME_SKIPPED_KILL_MONEY_OFF,
                        0, 0, false, null, null, List.of(), "Kill money disabled");
            }

            if (!teams.isCompetitor(request.victimAccountId())
                    || !teams.isCompetitor(request.killerAccountId())) {
                money.insertSettlementNoOp(
                        EconomyRules.SETTLEMENT_KIND_DEATH,
                        request.deathId(),
                        EconomyRules.OUTCOME_SKIPPED_NOT_COMPETITOR,
                        "Victim or killer is not a competitor");
                return new DeathPaymentResult(
                        request.deathId(),
                        EconomyRules.OUTCOME_SKIPPED_NOT_COMPETITOR,
                        0, 0, false, null, null, List.of(), "Victim or killer is not a competitor");
            }

            boolean friendly = request.friendlyFire();
            if (!friendly) {
                Optional<Team> victimTeam = teams.teamOf(request.victimAccountId());
                Optional<Team> killerTeam = teams.teamOf(request.killerAccountId());
                if (victimTeam.isPresent() && killerTeam.isPresent()
                        && victimTeam.get().id().equals(killerTeam.get().id())) {
                    friendly = true;
                }
            }
            if (friendly) {
                money.insertSettlementNoOp(
                        EconomyRules.SETTLEMENT_KIND_DEATH,
                        request.deathId(),
                        EconomyRules.OUTCOME_SKIPPED_FRIENDLY_FIRE,
                        "Friendly fire");
                return new DeathPaymentResult(
                        request.deathId(),
                        EconomyRules.OUTCOME_SKIPPED_FRIENDLY_FIRE,
                        0, 0, false, null, null, List.of(), "Friendly fire");
            }

            long owed = deathPrice(request.victimTier());
            Optional<Team> victimTeamOpt = teams.teamOf(request.victimAccountId());
            if (victimTeamOpt.isEmpty()) {
                money.insertSettlementNoOp(
                        EconomyRules.SETTLEMENT_KIND_DEATH,
                        request.deathId(),
                        EconomyRules.OUTCOME_NO_OP,
                        "Victim not on a team");
                return new DeathPaymentResult(
                        request.deathId(), EconomyRules.OUTCOME_NO_OP, owed, 0, false,
                        null, null, List.of(), "Victim not on a team");
            }
            Team victimTeam = victimTeamOpt.get();

            UUID rescueParentId = null;
            boolean rescueActivated = false;
            RescuePlan rescuePlan = RescuePlan.none();
            long wealth = money.sumBalancesForTeam(victimTeam.id());
            if (wealth - owed < EconomyRules.RESCUE_THRESHOLD) {
                rescuePlan = planRescue(request.deathId(), now);
                rescueActivated = rescuePlan.activated();
                rescueParentId = rescuePlan.parentTransactionId();
            }

            List<TeamMember> roster = teams.competitorsOf(victimTeam.id());
            Map<UUID, JdbcMoneyRepository.WalletRow> wallets = new HashMap<>();
            for (TeamMember member : roster) {
                JdbcMoneyRepository.WalletRow wallet =
                        money.findWalletByAccountId(member.accountId()).orElse(null);
                if (wallet == null) {
                    throw new SQLException("Missing wallet for teammate " + member.accountId());
                }
                wallets.put(member.accountId(), wallet);
            }
            JdbcMoneyRepository.WalletRow killerWallet =
                    money.findWalletByAccountId(request.killerAccountId()).orElse(null);
            if (killerWallet == null) {
                throw new SQLException("Missing killer wallet " + request.killerAccountId());
            }
            wallets.put(killerWallet.accountId(), killerWallet);

            // Include every wallet credited by a pending rescue so absolute balance updates
            // in the combined commit cover non-roster accounts (other teams).
            for (UUID rescuedAccountId : rescuePlan.balanceUpdates().keySet()) {
                if (wallets.containsKey(rescuedAccountId)) {
                    continue;
                }
                JdbcMoneyRepository.WalletRow wallet =
                        money.findWalletByAccountId(rescuedAccountId).orElse(null);
                if (wallet == null) {
                    throw new SQLException("Missing wallet for rescued account " + rescuedAccountId);
                }
                wallets.put(rescuedAccountId, wallet);
            }

            Map<UUID, Long> workingBalances = new HashMap<>();
            for (Map.Entry<UUID, JdbcMoneyRepository.WalletRow> entry : wallets.entrySet()) {
                workingBalances.put(entry.getKey(), entry.getValue().balance());
            }
            // Apply planned rescue credits in-memory so death math matches post-rescue balances
            // without a separate database commit.
            for (Map.Entry<UUID, Long> entry : rescuePlan.balanceUpdates().entrySet()) {
                workingBalances.put(entry.getKey(), entry.getValue());
            }

            long victimBalance = workingBalances.getOrDefault(request.victimAccountId(), 0L);
            long victimPays = Math.min(victimBalance, owed);
            workingBalances.put(request.victimAccountId(), victimBalance - victimPays);
            long shortfall = owed - victimPays;

            Map<UUID, Long> teammatePayments = new LinkedHashMap<>();
            if (shortfall > 0) {
                List<TeamMember> teammates = roster.stream()
                        .filter(member -> !member.accountId().equals(request.victimAccountId()))
                        .toList();
                teammatePayments = collectCascadingCover(teammates, workingBalances, shortfall);
            }

            long charged = victimPays;
            for (long paid : teammatePayments.values()) {
                charged += paid;
            }

            long killerBefore = workingBalances.get(request.killerAccountId());
            long killerAfter = Math.addExact(killerBefore, charged);
            workingBalances.put(request.killerAccountId(), killerAfter);

            UUID parentId = UUID.randomUUID();
            String reason = "Death payment " + request.deathId()
                    + (request.evidence().isBlank() ? "" : " (" + request.evidence() + ")");
            List<TransactionRecord> ledger = new ArrayList<>();
            List<SettlementContribution> contributions = new ArrayList<>();
            Map<UUID, Long> balanceUpdates = new LinkedHashMap<>();

            ledger.add(new TransactionRecord(
                    parentId, now, TransactionType.DEATH_PAYMENT, TransactionStatus.SUCCESS,
                    charged, reason, null, null, request.killerAccountId(),
                    null, null, killerBefore, killerAfter, "",
                    "", "", "", "", null,
                    EconomyRules.SETTLEMENT_KIND_DEATH, request.deathId()));

            if (victimPays > 0) {
                JdbcMoneyRepository.WalletRow victim = wallets.get(request.victimAccountId());
                // Before death phase = post-rescue working balance + victimPays.
                long after = workingBalances.get(request.victimAccountId());
                long victimBeforeDeath = after + victimPays;
                balanceUpdates.put(request.victimAccountId(), after);
                ledger.add(new TransactionRecord(
                        UUID.randomUUID(), now, TransactionType.DEATH_PAYMENT, TransactionStatus.SUCCESS,
                        victimPays, reason, null, request.victimAccountId(), request.killerAccountId(),
                        victimBeforeDeath, after, killerBefore, killerAfter, "",
                        "", "", "", "", parentId,
                        EconomyRules.SETTLEMENT_KIND_DEATH, request.deathId()));
                contributions.add(new SettlementContribution(
                        request.victimAccountId(), victim.displayName(), victimPays,
                        victimBeforeDeath, after, false));
            }

            for (Map.Entry<UUID, Long> entry : teammatePayments.entrySet()) {
                long paid = entry.getValue();
                if (paid <= 0) {
                    continue;
                }
                JdbcMoneyRepository.WalletRow payer = wallets.get(entry.getKey());
                long after = workingBalances.get(entry.getKey());
                long before = after + paid;
                balanceUpdates.put(entry.getKey(), after);
                ledger.add(new TransactionRecord(
                        UUID.randomUUID(), now, TransactionType.DEATH_PAYMENT, TransactionStatus.SUCCESS,
                        paid, reason, null, entry.getKey(), request.killerAccountId(),
                        before, after, killerBefore, killerAfter, "",
                        "", "", "", "", parentId,
                        EconomyRules.SETTLEMENT_KIND_DEATH, request.deathId()));
                contributions.add(new SettlementContribution(
                        entry.getKey(), payer.displayName(), paid, before, after, false));
            }

            balanceUpdates.put(request.killerAccountId(), killerAfter);
            JdbcMoneyRepository.WalletRow killer = wallets.get(request.killerAccountId());
            contributions.add(new SettlementContribution(
                    request.killerAccountId(), killer.displayName(), charged,
                    killerBefore, killerAfter, true));

            // Killer credit child (destination-only style already on parent; add explicit child for clarity)
            ledger.add(new TransactionRecord(
                    UUID.randomUUID(), now, TransactionType.DEATH_PAYMENT, TransactionStatus.SUCCESS,
                    charged, reason, null, null, request.killerAccountId(),
                    null, null, killerBefore, killerAfter, "",
                    "", "", "", "", parentId,
                    EconomyRules.SETTLEMENT_KIND_DEATH, request.deathId()));

            JdbcMoneyRepository.SettlementRow settlement = new JdbcMoneyRepository.SettlementRow(
                    EconomyRules.SETTLEMENT_KIND_DEATH,
                    request.deathId(),
                    EconomyRules.OUTCOME_APPLIED,
                    parentId,
                    charged,
                    "owed=" + owed + ";rescue=" + rescueActivated,
                    now);

            Map<UUID, Long> combinedBalances = new LinkedHashMap<>(rescuePlan.balanceUpdates());
            combinedBalances.putAll(balanceUpdates);
            List<TransactionRecord> combinedLedger = new ArrayList<>(rescuePlan.ledger());
            combinedLedger.addAll(ledger);
            List<JdbcMoneyRepository.SettlementRow> settlements = new ArrayList<>();
            if (rescuePlan.settlement() != null) {
                settlements.add(rescuePlan.settlement());
            }
            settlements.add(settlement);

            money.commitSettlement(JdbcMoneyRepository.SettlementCommit.ofSettlements(
                    combinedBalances, combinedLedger, settlements));

            for (Map.Entry<UUID, Long> entry : combinedBalances.entrySet()) {
                JdbcMoneyRepository.WalletRow row = wallets.get(entry.getKey());
                if (row != null) {
                    notifyBalanceChanged(row.minecraftUuid(), entry.getValue());
                }
            }
            for (SettlementContribution contribution : contributions) {
                if (!contribution.credit() && contribution.amount() > 0) {
                    notifySettlement(contribution.accountId(),
                            "You paid " + MoneyFormats.currency(contribution.amount())
                                    + " toward a death settlement.");
                }
            }

            return new DeathPaymentResult(
                    request.deathId(),
                    EconomyRules.OUTCOME_APPLIED,
                    owed,
                    charged,
                    rescueActivated,
                    parentId,
                    rescueParentId,
                    contributions,
                    "");
        } catch (SQLException | IOException | ArithmeticException exception) {
            logStorageFailure("processDeathPayment " + request.deathId(), exception);
            return new DeathPaymentResult(
                    request.deathId(),
                    EconomyRules.OUTCOME_NO_OP,
                    0, 0, false, null, null, List.of(),
                    exception.getMessage() == null ? "Storage failure" : exception.getMessage());
        }
    }

    /**
     * Plans a rescue settlement without committing. The caller merges this plan into the death
     * settlement transaction so rescue and death cannot diverge.
     */
    private RescuePlan planRescue(String deathId, Instant now) throws SQLException {
        Optional<JdbcMoneyRepository.SettlementRow> existing =
                money.findSettlement(EconomyRules.SETTLEMENT_KIND_RESCUE, deathId);
        if (existing.isPresent()) {
            return RescuePlan.alreadyApplied(existing.get().parentTransactionId());
        }
        ensureTeams();
        List<Team> allTeams = teams.listTeams();
        Map<UUID, Long> balanceUpdates = new LinkedHashMap<>();
        List<TransactionRecord> ledger = new ArrayList<>();
        UUID parentId = UUID.randomUUID();
        String reason = "Rescue for death " + deathId;
        long total = 0L;

        ledger.add(new TransactionRecord(
                parentId, now, TransactionType.RESCUE_PAYMENT, TransactionStatus.SUCCESS,
                0, reason, null, null, null, null, null, null, null, "",
                "", "", "", "", null,
                EconomyRules.SETTLEMENT_KIND_RESCUE, deathId));

        for (Team team : allTeams) {
            List<TeamMember> members = teams.competitorsOf(team.id());
            for (TeamMember member : members) {
                JdbcMoneyRepository.WalletRow wallet =
                        money.findWalletByAccountId(member.accountId()).orElse(null);
                if (wallet == null) {
                    continue;
                }
                long before = wallet.balance();
                if (balanceUpdates.containsKey(member.accountId())) {
                    before = balanceUpdates.get(member.accountId());
                }
                long after = Math.addExact(before, EconomyRules.RESCUE_PER_MEMBER);
                balanceUpdates.put(member.accountId(), after);
                total = Math.addExact(total, EconomyRules.RESCUE_PER_MEMBER);
                ledger.add(new TransactionRecord(
                        UUID.randomUUID(), now, TransactionType.RESCUE_PAYMENT, TransactionStatus.SUCCESS,
                        EconomyRules.RESCUE_PER_MEMBER, reason, null, null, member.accountId(),
                        null, null, before, after, "",
                        "", "", "", "", parentId,
                        EconomyRules.SETTLEMENT_KIND_RESCUE, deathId));
            }
        }

        ledger.set(0, new TransactionRecord(
                parentId, now, TransactionType.RESCUE_PAYMENT, TransactionStatus.SUCCESS,
                total, reason, null, null, null, null, null, null, null, "",
                "", "", "", "", null,
                EconomyRules.SETTLEMENT_KIND_RESCUE, deathId));

        JdbcMoneyRepository.SettlementRow settlement = new JdbcMoneyRepository.SettlementRow(
                EconomyRules.SETTLEMENT_KIND_RESCUE,
                deathId,
                EconomyRules.OUTCOME_APPLIED,
                parentId,
                total,
                "rescue",
                now);

        return new RescuePlan(true, parentId, balanceUpdates, ledger, settlement);
    }

    /**
     * Whether kill money applies for this victim (player override → event → global).
     */
    public synchronized boolean isKillMoneyEnabledForVictim(UUID victimAccountId) throws IOException {
        ensureOpen();
        try {
            Optional<Boolean> playerOverride = money.findPlayerKillMoneyOverride(victimAccountId);
            if (playerOverride.isPresent()) {
                return playerOverride.get();
            }
            Optional<String> eventId = money.findEventContainingParticipant(victimAccountId);
            if (eventId.isPresent()) {
                Optional<Boolean> eventEnabled = money.findEventKillMoney(eventId.get());
                if (eventEnabled.isPresent()) {
                    return eventEnabled.get();
                }
            }
            return money.loadSettings().killMoneyGlobal();
        } catch (SQLException exception) {
            throw storageException("resolve kill money for " + victimAccountId, exception);
        }
    }

    static Map<UUID, Long> computeRotatingShares(List<TeamMember> members, long amount, int rotationIndex) {
        int n = members.size();
        if (n == 0) {
            return Map.of();
        }
        long base = amount / n;
        int remainder = (int) (amount % n);
        int start = Math.floorMod(rotationIndex, n);
        Map<UUID, Long> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            TeamMember member = members.get(i);
            long share = base;
            int distance = Math.floorMod(i - start, n);
            if (distance < remainder) {
                share += 1;
            }
            shares.put(member.accountId(), share);
        }
        return shares;
    }

    private Map<UUID, Long> collectCascadingCover(
            List<TeamMember> teammates,
            Map<UUID, Long> workingBalances,
            long shortfall) {
        Map<UUID, Long> paid = new LinkedHashMap<>();
        for (TeamMember teammate : teammates) {
            paid.put(teammate.accountId(), 0L);
        }
        long remaining = shortfall;
        int rotation = 0;
        try {
            rotation = money.loadSettings().rewardRotationIndex();
        } catch (SQLException ignored) {
            // fall through with 0
        }

        // Initial rotating split of the shortfall
        Map<UUID, Long> planned = computeRotatingShares(teammates, remaining, rotation);
        remaining = applyCoverRound(teammates, workingBalances, paid, planned, remaining);

        // Cascade: keep redistributing unpaid remainder among those who still have money
        int guard = 0;
        while (remaining > 0 && guard++ < 64) {
            List<TeamMember> solvent = teammates.stream()
                    .filter(member -> workingBalances.getOrDefault(member.accountId(), 0L) > 0)
                    .toList();
            if (solvent.isEmpty()) {
                break;
            }
            Map<UUID, Long> round = computeRotatingShares(solvent, remaining, rotation + guard);
            long before = remaining;
            remaining = applyCoverRound(solvent, workingBalances, paid, round, remaining);
            if (remaining == before) {
                break;
            }
        }
        paid.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return paid;
    }

    private static long applyCoverRound(
            List<TeamMember> payers,
            Map<UUID, Long> workingBalances,
            Map<UUID, Long> paid,
            Map<UUID, Long> planned,
            long remaining) {
        long stillOwed = remaining;
        for (TeamMember payer : payers) {
            if (stillOwed <= 0) {
                break;
            }
            long plan = planned.getOrDefault(payer.accountId(), 0L);
            if (plan <= 0) {
                continue;
            }
            long available = workingBalances.getOrDefault(payer.accountId(), 0L);
            long take = Math.min(plan, Math.min(available, stillOwed));
            if (take <= 0) {
                continue;
            }
            workingBalances.put(payer.accountId(), available - take);
            paid.merge(payer.accountId(), take, Long::sum);
            stillOwed -= take;
        }
        return stillOwed;
    }

    private DeathPaymentResult deathResultFromSettlement(
            JdbcMoneyRepository.SettlementRow row, String deathId) throws SQLException {
        List<SettlementContribution> contributions = List.of();
        if (row.parentTransactionId() != null) {
            contributions = contributionsFromChildren(row.parentTransactionId());
        }
        Optional<JdbcMoneyRepository.SettlementRow> rescue =
                money.findSettlement(EconomyRules.SETTLEMENT_KIND_RESCUE, deathId);
        return new DeathPaymentResult(
                deathId,
                row.outcome(),
                0,
                row.chargedTotal(),
                rescue.isPresent() && EconomyRules.OUTCOME_APPLIED.equals(rescue.get().outcome()),
                row.parentTransactionId(),
                rescue.map(JdbcMoneyRepository.SettlementRow::parentTransactionId).orElse(null),
                contributions,
                "idempotent replay");
    }

    private List<SettlementContribution> contributionsFromChildren(UUID parentId) throws SQLException {
        List<SettlementContribution> list = new ArrayList<>();
        for (TransactionRecord child : money.loadChildTransactions(parentId)) {
            if (child.sourceWalletId() != null && child.amount() > 0) {
                String name = money.findWalletByAccountId(child.sourceWalletId())
                        .map(JdbcMoneyRepository.WalletRow::displayName)
                        .orElse("unknown");
                list.add(new SettlementContribution(
                        child.sourceWalletId(),
                        name,
                        child.amount(),
                        child.sourceBalanceBefore() == null ? 0 : child.sourceBalanceBefore(),
                        child.sourceBalanceAfter() == null ? 0 : child.sourceBalanceAfter(),
                        false));
            } else if (child.destinationWalletId() != null && child.amount() > 0
                    && child.sourceWalletId() == null) {
                String name = money.findWalletByAccountId(child.destinationWalletId())
                        .map(JdbcMoneyRepository.WalletRow::displayName)
                        .orElse("unknown");
                list.add(new SettlementContribution(
                        child.destinationWalletId(),
                        name,
                        child.amount(),
                        child.destinationBalanceBefore() == null ? 0 : child.destinationBalanceBefore(),
                        child.destinationBalanceAfter() == null ? 0 : child.destinationBalanceAfter(),
                        true));
            }
        }
        return list;
    }

    private void audit(
            String action,
            String scope,
            String oldValue,
            String newValue,
            String reason,
            String actorUsername,
            String senderType) throws SQLException {
        money.insertAdminAudit(new JdbcMoneyRepository.AdminAuditRow(
                UUID.randomUUID(),
                action,
                scope == null ? "" : scope,
                oldValue == null ? "" : oldValue,
                newValue == null ? "" : newValue,
                reason == null ? "" : reason,
                actorUsername == null ? "" : actorUsername,
                senderType == null ? "" : senderType,
                Instant.now()));
    }

    private TransactionResult changeOne(
            TransactionType type,
            UUID minecraftUuid,
            long amount,
            String reason,
            @Nullable UUID actorMinecraftUuid,
            MoneyAuditContext context) {
        UUID transactionId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        TransactionResult validation = validate(transactionId, timestamp, type, amount, reason,
                null, null, type == TransactionType.SET, context);
        if (validation != null) {
            return validation;
        }

        JdbcMoneyRepository.WalletRow wallet;
        UUID actorAccountId;
        try {
            wallet = money.findWalletByMinecraftUuid(minecraftUuid).orElse(null);
            actorAccountId = resolveActorAccountId(actorMinecraftUuid);
        } catch (SQLException exception) {
            return storageFailure(transactionId, timestamp, type, amount, reason,
                    null, null, null, null, null, exception, context);
        }
        if (wallet == null) {
            return failure(transactionId, timestamp, type, amount, reason, actorAccountId,
                    null, null, null, null, TransactionStatus.WALLET_NOT_FOUND, "Wallet not found", context);
        }

        long beforeBalance = wallet.balance();
        long afterBalance;
        if (type == TransactionType.REMOVE) {
            if (beforeBalance < amount) {
                return failure(transactionId, timestamp, type, amount, reason, actorAccountId,
                        wallet.accountId(), null, beforeBalance, null,
                        TransactionStatus.INSUFFICIENT_FUNDS, "Wallet has insufficient funds", context);
            }
            afterBalance = beforeBalance - amount;
        } else if (type == TransactionType.ADD) {
            try {
                afterBalance = Math.addExact(beforeBalance, amount);
            } catch (ArithmeticException exception) {
                return failure(transactionId, timestamp, type, amount, reason, actorAccountId,
                        wallet.accountId(), null, beforeBalance, null,
                        TransactionStatus.INVALID_AMOUNT, "Amount would overflow wallet balance", context);
            }
        } else {
            afterBalance = amount;
        }

        TransactionRecord success = record(transactionId, timestamp, type, TransactionStatus.SUCCESS,
                amount, reason, actorAccountId, wallet.accountId(), null, beforeBalance, afterBalance,
                null, null, "", context);
        try {
            money.commitSingleWalletChange(wallet.accountId(), afterBalance, success);
            notifyBalanceChanged(wallet.minecraftUuid(), afterBalance);
            return TransactionResult.from(success);
        } catch (SQLException exception) {
            return storageFailure(transactionId, timestamp, type, amount, reason,
                    actorAccountId, wallet.accountId(), null, beforeBalance, null, exception, context);
        }
    }

    private @Nullable UUID resolveActorAccountId(@Nullable UUID actorMinecraftUuid) throws SQLException {
        if (actorMinecraftUuid == null) {
            return null;
        }
        return accounts.findIdentityByMinecraftUuid(actorMinecraftUuid)
                .map(AccountIdentity::accountId)
                .orElse(null);
    }

    private TransactionResult validate(
            UUID transactionId,
            Instant timestamp,
            TransactionType type,
            long amount,
            String reason,
            @Nullable UUID sourceAccountId,
            @Nullable UUID destinationAccountId,
            boolean zeroAllowed,
            MoneyAuditContext context) {
        if (amount < 0 || (!zeroAllowed && amount == 0)) {
            return failure(transactionId, timestamp, type, amount, reason, null,
                    sourceAccountId, destinationAccountId, null, null, TransactionStatus.INVALID_AMOUNT,
                    zeroAllowed ? "Balance cannot be negative" : "Amount must be positive", context);
        }
        if (reason == null || reason.isBlank()) {
            return failure(transactionId, timestamp, type, amount, reason, null,
                    sourceAccountId, destinationAccountId, null, null, TransactionStatus.INVALID_REASON,
                    "Reason cannot be blank", context);
        }
        return null;
    }

    private TransactionResult failure(
            UUID transactionId,
            Instant timestamp,
            TransactionType type,
            long amount,
            String reason,
            @Nullable UUID actorAccountId,
            @Nullable UUID sourceAccountId,
            @Nullable UUID destinationAccountId,
            @Nullable Long sourceBefore,
            @Nullable Long destinationBefore,
            TransactionStatus status,
            String failureReason,
            MoneyAuditContext context) {
        TransactionRecord failure = record(transactionId, timestamp, type, status, amount, reason,
                actorAccountId, sourceAccountId, destinationAccountId, sourceBefore, sourceBefore,
                destinationBefore, destinationBefore, failureReason, context);
        try {
            money.recordFailure(failure);
            return TransactionResult.from(failure);
        } catch (SQLException exception) {
            logStorageFailure("record failed " + type + " transaction " + transactionId, exception);
            return TransactionResult.from(record(transactionId, timestamp, type,
                    TransactionStatus.STORAGE_FAILURE, amount, reason, actorAccountId, sourceAccountId,
                    destinationAccountId, sourceBefore, sourceBefore, destinationBefore, destinationBefore,
                    "Could not persist transaction record: " + exception.getMessage(), context));
        }
    }

    private TransactionResult storageFailure(
            UUID transactionId,
            Instant timestamp,
            TransactionType type,
            long amount,
            String reason,
            @Nullable UUID actorAccountId,
            @Nullable UUID sourceAccountId,
            @Nullable UUID destinationAccountId,
            @Nullable Long sourceBefore,
            @Nullable Long destinationBefore,
            SQLException exception,
            MoneyAuditContext context) {
        logStorageFailure(type + " transaction " + transactionId, exception);
        return failure(transactionId, timestamp, type, amount, reason, actorAccountId, sourceAccountId,
                destinationAccountId, sourceBefore, destinationBefore, TransactionStatus.STORAGE_FAILURE,
                exception.getMessage(), context);
    }

    private static TransactionRecord record(
            UUID transactionId,
            Instant timestamp,
            TransactionType type,
            TransactionStatus status,
            long amount,
            String reason,
            @Nullable UUID actorAccountId,
            @Nullable UUID sourceAccountId,
            @Nullable UUID destinationAccountId,
            @Nullable Long sourceBefore,
            @Nullable Long sourceAfter,
            @Nullable Long destinationBefore,
            @Nullable Long destinationAfter,
            String failureReason,
            MoneyAuditContext context) {
        return new TransactionRecord(transactionId, timestamp, type, status, amount, reason,
                actorAccountId, sourceAccountId, destinationAccountId, sourceBefore, sourceAfter,
                destinationBefore, destinationAfter, failureReason,
                context.authorizationAction(), context.command(), context.actorUsername(), context.senderType());
    }

    private void notifyBalanceChanged(@Nullable UUID minecraftUuid, long balance) {
        if (minecraftUuid == null) {
            return;
        }
        for (BalanceChangeListener listener : balanceListeners) {
            listener.balanceChanged(minecraftUuid, balance);
        }
    }

    private void notifyTransferSuccess(
            @Nullable UUID sourceMinecraftUuid,
            @Nullable UUID destinationMinecraftUuid,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amount,
            UUID transactionId) {
        TransferSuccessEvent event = new TransferSuccessEvent(
                sourceMinecraftUuid,
                destinationMinecraftUuid,
                sourceAccountId,
                destinationAccountId,
                amount,
                transactionId);
        for (TransferSuccessListener listener : transferSuccessListeners) {
            listener.onTransferSuccess(event);
        }
    }

    private void notifySettlement(UUID accountId, String message) {
        for (SettlementNoticeListener listener : settlementNoticeListeners) {
            listener.notifyAccount(accountId, message);
        }
    }

    /**
     * Idempotent auction payment proof: records that {@code lotId} was paid via an existing transfer.
     *
     * @return {@code true} when the settlement row exists after this call (inserted or already present)
     */
    public synchronized boolean ensureAuctionPaymentSettlement(
            String lotId,
            UUID paymentTransactionId,
            long amount) {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(paymentTransactionId, "paymentTransactionId");
        if (lotId.isBlank()) {
            return false;
        }
        if (amount <= 0) {
            return false;
        }
        try {
            Optional<JdbcMoneyRepository.SettlementRow> existing =
                    money.findSettlement(EconomyRules.SETTLEMENT_KIND_AUCTION, lotId);
            if (existing.isPresent()) {
                return true;
            }
            money.insertSettlementApplied(
                    EconomyRules.SETTLEMENT_KIND_AUCTION,
                    lotId,
                    paymentTransactionId,
                    amount,
                    "{\"paymentTransactionId\":\"" + paymentTransactionId + "\"}");
            return true;
        } catch (SQLException exception) {
            logStorageFailure("auction settlement " + lotId, exception);
            return false;
        }
    }

    public synchronized boolean hasAuctionPaymentSettlement(String lotId) {
        Objects.requireNonNull(lotId, "lotId");
        try {
            return money.findSettlement(EconomyRules.SETTLEMENT_KIND_AUCTION, lotId).isPresent();
        } catch (SQLException exception) {
            logStorageFailure("read auction settlement " + lotId, exception);
            return false;
        }
    }

    /**
     * @return the parent transfer id recorded on an existing auction settlement, if present
     */
    public synchronized Optional<UUID> findAuctionPaymentTransactionId(String lotId) {
        Objects.requireNonNull(lotId, "lotId");
        try {
            return money.findSettlement(EconomyRules.SETTLEMENT_KIND_AUCTION, lotId)
                    .map(JdbcMoneyRepository.SettlementRow::parentTransactionId);
        } catch (SQLException exception) {
            logStorageFailure("read auction settlement tx " + lotId, exception);
            return Optional.empty();
        }
    }

    /**
     * Records an admin configuration change in {@code economy_admin_audit}.
     */
    public synchronized void recordAdminAudit(
            String action,
            String scope,
            String oldValue,
            String newValue,
            String reason,
            String actorUsername,
            String senderType) {
        try {
            audit(action, scope, oldValue, newValue, reason, actorUsername, senderType);
        } catch (SQLException exception) {
            logStorageFailure("admin audit " + action, exception);
        }
    }

    private void logStorageFailure(String context, Exception exception) {
        storageErrorLogger.accept("Wallet storage failure during " + context + ": " + exception.getMessage());
    }

    private static WalletStorageException storageException(String context, SQLException exception) {
        return new WalletStorageException("Wallet storage failure during " + context + ": "
                + exception.getMessage(), exception);
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Money service is closed");
        }
    }

    private void ensureOpenUnchecked() {
        if (closed) {
            throw new IllegalStateException("Money service is closed");
        }
    }

    private void ensureTeams() {
        if (teams == null) {
            throw new IllegalStateException("TeamService has not been wired into MoneyService");
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    public record KillMoneyStatus(
            boolean killMoneyGlobal,
            boolean teamTotalsVisible,
            int rewardRotationIndex,
            Map<Integer, Long> killPrices) {
    }

    private record RescuePlan(
            boolean activated,
            @Nullable UUID parentTransactionId,
            Map<UUID, Long> balanceUpdates,
            List<TransactionRecord> ledger,
            @Nullable JdbcMoneyRepository.SettlementRow settlement) {

        private RescuePlan {
            balanceUpdates = balanceUpdates == null ? Map.of() : Map.copyOf(balanceUpdates);
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
        }

        static RescuePlan none() {
            return new RescuePlan(false, null, Map.of(), List.of(), null);
        }

        static RescuePlan alreadyApplied(@Nullable UUID parentTransactionId) {
            return new RescuePlan(false, parentTransactionId, Map.of(), List.of(), null);
        }
    }

    @FunctionalInterface
    public interface SettlementNoticeListener {
        void notifyAccount(UUID accountId, String message);
    }

    @FunctionalInterface
    public interface TransferSuccessListener {
        void onTransferSuccess(TransferSuccessEvent event);
    }

    public record TransferSuccessEvent(
            @Nullable UUID sourceMinecraftUuid,
            @Nullable UUID destinationMinecraftUuid,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amount,
            UUID transactionId) {
    }
}
