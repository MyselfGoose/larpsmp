package com.larpsmp.moneyevent.wallet;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TransactionRecord(
        UUID transactionId,
        Instant timestamp,
        TransactionType type,
        TransactionStatus status,
        long amount,
        String reason,
        UUID actorId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        Long sourceBalanceBefore,
        Long sourceBalanceAfter,
        Long destinationBalanceBefore,
        Long destinationBalanceAfter,
        String failureReason) {

    public TransactionRecord {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        reason = reason == null ? "" : reason;
        failureReason = failureReason == null ? "" : failureReason;
    }

    public boolean successful() {
        return status == TransactionStatus.SUCCESS;
    }
}
