package com.larpsmp.moneyevent.wallet;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class MoneyService implements AutoCloseable {
    public static final long STARTING_BALANCE = 200;
    public static final String STARTING_BALANCE_REASON = "Initial competitor balance";

    private final WalletService wallets;
    private final FileTransactionStore transactions;
    private final Consumer<String> storageErrorLogger;

    public MoneyService(
            WalletService wallets,
            FileTransactionStore transactions,
            Consumer<String> storageErrorLogger) {
        this.wallets = Objects.requireNonNull(wallets, "wallets");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.storageErrorLogger = Objects.requireNonNull(storageErrorLogger, "storageErrorLogger");
    }

    public synchronized BalanceResult balance(UUID walletId) {
        try {
            Wallet wallet = wallets.require(walletId);
            return wallet == null
                    ? new BalanceResult(TransactionStatus.WALLET_NOT_FOUND, walletId, null, "Wallet not found")
                    : new BalanceResult(TransactionStatus.SUCCESS, walletId, wallet.balance(), "");
        } catch (IOException exception) {
            logStorageFailure("read wallet " + walletId, exception);
            return new BalanceResult(TransactionStatus.STORAGE_FAILURE, walletId, null, exception.getMessage());
        }
    }

    public synchronized TransactionResult add(UUID walletId, long amount, String reason, UUID actorId) {
        return changeOne(TransactionType.ADD, walletId, amount, reason, actorId);
    }

    /**
     * Activates starting money only after an external roster/authentication system has established
     * that this UUID is an eligible competitor.
     */
    public synchronized TransactionResult initializeEligibleCompetitor(UUID playerId, String username) {
        UUID transactionId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        Wallet wallet;
        try {
            wallet = wallets.getOrCreate(playerId, username);
        } catch (IOException | IllegalArgumentException exception) {
            IOException storageException = exception instanceof IOException ioException
                    ? ioException
                    : new IOException(exception.getMessage(), exception);
            return storageFailure(transactionId, timestamp, TransactionType.STARTING_BALANCE,
                    STARTING_BALANCE, STARTING_BALANCE_REASON, null,
                    playerId, null, null, null, storageException);
        }

        long balanceBefore = wallet.balance();
        if (wallet.startingBalanceGranted()) {
            return failure(transactionId, timestamp, TransactionType.STARTING_BALANCE,
                    STARTING_BALANCE, STARTING_BALANCE_REASON, null,
                    playerId, null, balanceBefore, null,
                    TransactionStatus.ALREADY_INITIALIZED, "Starting balance was already granted");
        }

        try {
            Math.addExact(balanceBefore, STARTING_BALANCE);
        } catch (ArithmeticException exception) {
            return failure(transactionId, timestamp, TransactionType.STARTING_BALANCE,
                    STARTING_BALANCE, STARTING_BALANCE_REASON, null,
                    playerId, null, balanceBefore, null,
                    TransactionStatus.INVALID_AMOUNT, "Starting balance would overflow wallet balance");
        }

        WalletSnapshot before = WalletSnapshot.of(wallet);
        wallet.grantStartingBalance(STARTING_BALANCE);
        TransactionRecord success = record(transactionId, timestamp, TransactionType.STARTING_BALANCE,
                TransactionStatus.SUCCESS, STARTING_BALANCE, STARTING_BALANCE_REASON, null,
                playerId, null, balanceBefore, wallet.balance(), null, null, "");
        try {
            transactions.commit(success, List.of(before), List.of(wallet));
            return TransactionResult.from(success);
        } catch (IOException exception) {
            before.restore(wallet);
            return storageFailure(transactionId, timestamp, TransactionType.STARTING_BALANCE,
                    STARTING_BALANCE, STARTING_BALANCE_REASON, null,
                    playerId, null, balanceBefore, null, exception);
        }
    }

    public synchronized TransactionResult remove(UUID walletId, long amount, String reason, UUID actorId) {
        return changeOne(TransactionType.REMOVE, walletId, amount, reason, actorId);
    }

    public synchronized TransactionResult set(UUID walletId, long balance, String reason, UUID actorId) {
        return changeOne(TransactionType.SET, walletId, balance, reason, actorId);
    }

    public synchronized TransactionResult transfer(
            UUID sourceId, UUID destinationId, long amount, String reason, UUID actorId) {
        UUID transactionId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        if (sourceId.equals(destinationId)) {
            return failure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason, actorId,
                    sourceId, destinationId, null, null, TransactionStatus.SAME_WALLET,
                    "Source and destination wallets must be different");
        }
        TransactionResult validation = validate(transactionId, timestamp, TransactionType.TRANSFER,
                amount, reason, actorId, sourceId, destinationId, false);
        if (validation != null) {
            return validation;
        }

        Wallet source;
        Wallet destination;
        try {
            source = wallets.require(sourceId);
            destination = wallets.require(destinationId);
        } catch (IOException exception) {
            return storageFailure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason,
                    actorId, sourceId, destinationId, null, null, exception);
        }
        if (source == null || destination == null) {
            return failure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason, actorId,
                    sourceId, destinationId,
                    source == null ? null : source.balance(), destination == null ? null : destination.balance(),
                    TransactionStatus.WALLET_NOT_FOUND,
                    source == null ? "Source wallet not found" : "Destination wallet not found");
        }

        long sourceBefore = source.balance();
        long destinationBefore = destination.balance();
        if (sourceBefore < amount) {
            return failure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason, actorId,
                    sourceId, destinationId, sourceBefore, destinationBefore,
                    TransactionStatus.INSUFFICIENT_FUNDS, "Source wallet has insufficient funds");
        }
        long destinationAfter;
        try {
            destinationAfter = Math.addExact(destinationBefore, amount);
        } catch (ArithmeticException exception) {
            return failure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason, actorId,
                    sourceId, destinationId, sourceBefore, destinationBefore,
                    TransactionStatus.INVALID_AMOUNT, "Amount would overflow destination balance");
        }
        long sourceAfter = sourceBefore - amount;
        List<WalletSnapshot> before = List.of(WalletSnapshot.of(source), WalletSnapshot.of(destination));
        source.setBalance(sourceAfter);
        destination.setBalance(destinationAfter);
        TransactionRecord success = record(transactionId, timestamp, TransactionType.TRANSFER,
                TransactionStatus.SUCCESS, amount, reason, actorId, sourceId, destinationId,
                sourceBefore, sourceAfter, destinationBefore, destinationAfter, "");
        try {
            transactions.commit(success, before, List.of(source, destination));
            return TransactionResult.from(success);
        } catch (IOException exception) {
            before.get(0).restore(source);
            before.get(1).restore(destination);
            return storageFailure(transactionId, timestamp, TransactionType.TRANSFER, amount, reason,
                    actorId, sourceId, destinationId, sourceBefore, destinationBefore, exception);
        }
    }

    private TransactionResult changeOne(
            TransactionType type, UUID walletId, long amount, String reason, UUID actorId) {
        UUID transactionId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        TransactionResult validation = validate(transactionId, timestamp, type, amount, reason,
                actorId, walletId, null, type == TransactionType.SET);
        if (validation != null) {
            return validation;
        }

        Wallet wallet;
        try {
            wallet = wallets.require(walletId);
        } catch (IOException exception) {
            return storageFailure(transactionId, timestamp, type, amount, reason,
                    actorId, walletId, null, null, null, exception);
        }
        if (wallet == null) {
            return failure(transactionId, timestamp, type, amount, reason, actorId,
                    walletId, null, null, null, TransactionStatus.WALLET_NOT_FOUND, "Wallet not found");
        }

        long beforeBalance = wallet.balance();
        long afterBalance;
        if (type == TransactionType.REMOVE) {
            if (beforeBalance < amount) {
                return failure(transactionId, timestamp, type, amount, reason, actorId,
                        walletId, null, beforeBalance, null,
                        TransactionStatus.INSUFFICIENT_FUNDS, "Wallet has insufficient funds");
            }
            afterBalance = beforeBalance - amount;
        } else if (type == TransactionType.ADD) {
            try {
                afterBalance = Math.addExact(beforeBalance, amount);
            } catch (ArithmeticException exception) {
                return failure(transactionId, timestamp, type, amount, reason, actorId,
                        walletId, null, beforeBalance, null,
                        TransactionStatus.INVALID_AMOUNT, "Amount would overflow wallet balance");
            }
        } else {
            afterBalance = amount;
        }

        WalletSnapshot before = WalletSnapshot.of(wallet);
        wallet.setBalance(afterBalance);
        TransactionRecord success = record(transactionId, timestamp, type, TransactionStatus.SUCCESS,
                amount, reason, actorId, walletId, null, beforeBalance, afterBalance,
                null, null, "");
        try {
            transactions.commit(success, List.of(before), List.of(wallet));
            return TransactionResult.from(success);
        } catch (IOException exception) {
            before.restore(wallet);
            return storageFailure(transactionId, timestamp, type, amount, reason,
                    actorId, walletId, null, beforeBalance, null, exception);
        }
    }

    private TransactionResult validate(
            UUID transactionId, Instant timestamp, TransactionType type, long amount, String reason,
            UUID actorId, UUID sourceId, UUID destinationId, boolean zeroAllowed) {
        if (amount < 0 || (!zeroAllowed && amount == 0)) {
            return failure(transactionId, timestamp, type, amount, reason, actorId,
                    sourceId, destinationId, null, null, TransactionStatus.INVALID_AMOUNT,
                    zeroAllowed ? "Balance cannot be negative" : "Amount must be positive");
        }
        if (reason == null || reason.isBlank()) {
            return failure(transactionId, timestamp, type, amount, reason, actorId,
                    sourceId, destinationId, null, null, TransactionStatus.INVALID_REASON,
                    "Reason cannot be blank");
        }
        return null;
    }

    private TransactionResult failure(
            UUID transactionId, Instant timestamp, TransactionType type, long amount, String reason,
            UUID actorId, UUID sourceId, UUID destinationId, Long sourceBefore, Long destinationBefore,
            TransactionStatus status, String failureReason) {
        TransactionRecord failure = record(transactionId, timestamp, type, status, amount, reason,
                actorId, sourceId, destinationId, sourceBefore, sourceBefore,
                destinationBefore, destinationBefore, failureReason);
        try {
            transactions.record(failure);
            return TransactionResult.from(failure);
        } catch (IOException exception) {
            logStorageFailure("record failed " + type + " transaction " + transactionId, exception);
            TransactionRecord storageFailure = record(transactionId, timestamp, type,
                    TransactionStatus.STORAGE_FAILURE, amount, reason, actorId, sourceId, destinationId,
                    sourceBefore, sourceBefore, destinationBefore, destinationBefore,
                    "Could not persist transaction record: " + exception.getMessage());
            return TransactionResult.from(storageFailure);
        }
    }

    private TransactionResult storageFailure(
            UUID transactionId, Instant timestamp, TransactionType type, long amount, String reason,
            UUID actorId, UUID sourceId, UUID destinationId, Long sourceBefore, Long destinationBefore,
            IOException exception) {
        logStorageFailure(type + " transaction " + transactionId, exception);
        return failure(transactionId, timestamp, type, amount, reason, actorId, sourceId, destinationId,
                sourceBefore, destinationBefore, TransactionStatus.STORAGE_FAILURE, exception.getMessage());
    }

    private static TransactionRecord record(
            UUID transactionId, Instant timestamp, TransactionType type, TransactionStatus status,
            long amount, String reason, UUID actorId, UUID sourceId, UUID destinationId,
            Long sourceBefore, Long sourceAfter, Long destinationBefore, Long destinationAfter,
            String failureReason) {
        return new TransactionRecord(transactionId, timestamp, type, status, amount, reason,
                actorId, sourceId, destinationId, sourceBefore, sourceAfter,
                destinationBefore, destinationAfter, failureReason);
    }

    private void logStorageFailure(String context, IOException exception) {
        storageErrorLogger.accept("Wallet storage failure during " + context + ": " + exception.getMessage());
    }

    @Override
    public synchronized void close() throws IOException {
        transactions.close();
        wallets.close();
    }
}
