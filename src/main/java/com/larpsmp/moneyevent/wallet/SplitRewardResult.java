package com.larpsmp.moneyevent.wallet;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record SplitRewardResult(
        TransactionStatus status,
        String outcome,
        long amount,
        @Nullable UUID parentTransactionId,
        List<SettlementContribution> shares,
        int rotationIndexBefore,
        int rotationIndexAfter,
        String failureReason) {

    public SplitRewardResult {
        shares = shares == null ? List.of() : List.copyOf(shares);
        failureReason = failureReason == null ? "" : failureReason;
        outcome = outcome == null ? "" : outcome;
    }

    public boolean successful() {
        return status == TransactionStatus.SUCCESS;
    }
}
