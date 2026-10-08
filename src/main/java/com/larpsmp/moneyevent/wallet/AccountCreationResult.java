package com.larpsmp.moneyevent.wallet;

public record AccountCreationResult(Status status, WalletAccount account) {
    public enum Status {
        CREATED,
        ALREADY_EXISTS,
        STORAGE_FAILURE
    }
}
