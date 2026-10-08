package com.larpsmp.moneyevent.wallet;

import java.util.UUID;

public record TransactionResult(
        UUID transactionId,
        TransactionType type,
        TransactionStatus status,
        long amount,
        Long sourceBalanceBefore,
        Long sourceBalanceAfter,
        Long destinationBalanceBefore,
        Long destinationBalanceAfter,
        String failureReason) {

    static TransactionResult from(TransactionRecord record) {
        return new TransactionResult(
                record.transactionId(), record.type(), record.status(), record.amount(),
                record.sourceBalanceBefore(), record.sourceBalanceAfter(),
                record.destinationBalanceBefore(), record.destinationBalanceAfter(),
                record.failureReason());
    }

    public boolean successful() {
        return status == TransactionStatus.SUCCESS;
    }
}
