package com.larpsmp.moneyevent.wallet;

import java.util.UUID;

public record BalanceResult(TransactionStatus status, UUID walletId, Long balance, String failureReason) {
    public boolean successful() {
        return status == TransactionStatus.SUCCESS;
    }
}
