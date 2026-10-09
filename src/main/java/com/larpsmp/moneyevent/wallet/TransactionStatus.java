package com.larpsmp.moneyevent.wallet;

public enum TransactionStatus {
    SUCCESS,
    WALLET_NOT_FOUND,
    INVALID_AMOUNT,
    INVALID_REASON,
    INSUFFICIENT_FUNDS,
    SAME_WALLET,
    STORAGE_FAILURE
}
