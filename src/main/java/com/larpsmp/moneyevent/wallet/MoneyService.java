package com.larpsmp.moneyevent.wallet;

import com.larpsmp.moneyevent.auth.AccountIdentity;
import com.larpsmp.moneyevent.auth.AccountRepository;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
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
    public static final long STARTING_BALANCE = 200;
    public static final String STARTING_BALANCE_REASON = "Initial competitor balance";

    private final AccountRepository accounts;
    private final JdbcMoneyRepository money;
    private final Consumer<String> storageErrorLogger;
    private final List<BalanceChangeListener> balanceListeners = new CopyOnWriteArrayList<>();
    private boolean closed;

    public MoneyService(
            AccountRepository accounts,
            JdbcMoneyRepository money,
            Consumer<String> storageErrorLogger) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.money = Objects.requireNonNull(money, "money");
        this.storageErrorLogger = Objects.requireNonNull(storageErrorLogger, "storageErrorLogger");
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

    public void addBalanceChangeListener(BalanceChangeListener listener) {
        balanceListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public synchronized TransactionResult transfer(
            UUID sourceMinecraftUuid,
            UUID destinationMinecraftUuid,
            long amount,
            String reason,
            UUID actorMinecraftUuid) {
        UUID transactionId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        if (sourceMinecraftUuid.equals(destinationMinecraftUuid)) {
            return failure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason, null,
                    null, null, null, null, TransactionStatus.SAME_WALLET,
                    "Source and destination wallets must be different", MoneyAuditContext.EMPTY);
        }
        TransactionResult validation = validate(transactionId, timestamp, TransactionType.TRANSFER,
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
            return storageFailure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason,
                    null, null, null, null, null, exception, MoneyAuditContext.EMPTY);
        }
        if (source == null || destination == null) {
            return failure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason, actorAccountId,
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
            return failure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason, actorAccountId,
                    source.accountId(), destination.accountId(), sourceBefore, destinationBefore,
                    TransactionStatus.INSUFFICIENT_FUNDS, "Source wallet has insufficient funds",
                    MoneyAuditContext.EMPTY);
        }
        long destinationAfter;
        try {
            destinationAfter = Math.addExact(destinationBefore, amount);
        } catch (ArithmeticException exception) {
            return failure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason, actorAccountId,
                    source.accountId(), destination.accountId(), sourceBefore, destinationBefore,
                    TransactionStatus.INVALID_AMOUNT, "Amount would overflow destination balance",
                    MoneyAuditContext.EMPTY);
        }
        long sourceAfter = sourceBefore - amount;
        TransactionRecord success = record(transactionId, timestamp, TransactionType.TRANSFER,
                TransactionStatus.SUCCESS, amount, reason, actorAccountId,
                source.accountId(), destination.accountId(),
                sourceBefore, sourceAfter, destinationBefore, destinationAfter, "", MoneyAuditContext.EMPTY);
        try {
            money.commitTransfer(source.accountId(), sourceAfter, destination.accountId(), destinationAfter, success);
            notifyBalanceChanged(source.minecraftUuid(), sourceAfter);
            notifyBalanceChanged(destination.minecraftUuid(), destinationAfter);
            return TransactionResult.from(success);
        } catch (SQLException exception) {
            return storageFailure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason,
                    actorAccountId, source.accountId(), destination.accountId(),
                    sourceBefore, destinationBefore, exception, MoneyAuditContext.EMPTY);
        }
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

    @Override
    public synchronized void close() {
        closed = true;
    }
}
