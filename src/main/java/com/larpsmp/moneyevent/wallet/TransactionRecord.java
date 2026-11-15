package com.larpsmp.moneyevent.wallet;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

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
        String senderType,
        @Nullable UUID parentTransactionId,
        String externalRefKind,
        String externalRefId) {

    public TransactionRecord(
            UUID transactionId, Instant timestamp, TransactionType type, TransactionStatus status,
            long amount, String reason, UUID actorId, UUID sourceWalletId, UUID destinationWalletId,
            Long sourceBalanceBefore, Long sourceBalanceAfter, Long destinationBalanceBefore,
            Long destinationBalanceAfter, String failureReason) {
        this(transactionId, timestamp, type, status, amount, reason, actorId, sourceWalletId,
                destinationWalletId, sourceBalanceBefore, sourceBalanceAfter, destinationBalanceBefore,
                destinationBalanceAfter, failureReason, "", "", "", "", null, "", "");
    }

    public TransactionRecord(
            UUID transactionId, Instant timestamp, TransactionType type, TransactionStatus status,
            long amount, String reason, UUID actorId, UUID sourceWalletId, UUID destinationWalletId,
            Long sourceBalanceBefore, Long sourceBalanceAfter, Long destinationBalanceBefore,
            Long destinationBalanceAfter, String failureReason,
            String authorizationAction, String command, String actorUsername, String senderType) {
        this(transactionId, timestamp, type, status, amount, reason, actorId, sourceWalletId,
                destinationWalletId, sourceBalanceBefore, sourceBalanceAfter, destinationBalanceBefore,
                destinationBalanceAfter, failureReason, authorizationAction, command, actorUsername,
                senderType, null, "", "");
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
        externalRefKind = externalRefKind == null ? "" : externalRefKind;
        externalRefId = externalRefId == null ? "" : externalRefId;
    }

    public boolean successful() {
        return status == TransactionStatus.SUCCESS;
    }

    public TransactionRecord withParentAndRef(
            @Nullable UUID parentId, String refKind, String refId) {
        return new TransactionRecord(
                transactionId, timestamp, type, status, amount, reason, actorId, sourceWalletId,
                destinationWalletId, sourceBalanceBefore, sourceBalanceAfter, destinationBalanceBefore,
                destinationBalanceAfter, failureReason, authorizationAction, command, actorUsername,
                senderType, parentId, refKind, refId);
    }
}
