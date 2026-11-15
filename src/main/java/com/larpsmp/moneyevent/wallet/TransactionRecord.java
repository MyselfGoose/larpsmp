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
        String failureReason,
        String authorizationAction,
        String command,
        String actorUsername,
        String senderType) {

    public TransactionRecord(
            UUID transactionId, Instant timestamp, TransactionType type, TransactionStatus status,
            long amount, String reason, UUID actorId, UUID sourceWalletId, UUID destinationWalletId,
            Long sourceBalanceBefore, Long sourceBalanceAfter, Long destinationBalanceBefore,
            Long destinationBalanceAfter, String failureReason) {
        this(transactionId, timestamp, type, status, amount, reason, actorId, sourceWalletId,
                destinationWalletId, sourceBalanceBefore, sourceBalanceAfter, destinationBalanceBefore,
                destinationBalanceAfter, failureReason, "", "", "", "");
    }

    public TransactionRecord {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        reason = reason == null ? "" : reason;
        failureReason = failureReason == null ? "" : failureReason;
        authorizationAction = authorizationAction == null ? "" : authorizationAction;
        command = command == null ? "" : command;
        actorUsername = actorUsername == null ? "" : actorUsername;
        senderType = senderType == null ? "" : senderType;
    }

    public boolean successful() {
        return status == TransactionStatus.SUCCESS;
    }
}
