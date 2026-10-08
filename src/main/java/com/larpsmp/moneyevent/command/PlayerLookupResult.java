package com.larpsmp.moneyevent.command;

public record PlayerLookupResult(Status status, OnlinePlayerIdentity player) {
    public enum Status {
        FOUND,
        NOT_FOUND,
        AMBIGUOUS,
        STORAGE_FAILURE
    }

    static PlayerLookupResult found(OnlinePlayerIdentity player) {
        return new PlayerLookupResult(Status.FOUND, player);
    }

    static PlayerLookupResult status(Status status) {
        return new PlayerLookupResult(status, null);
    }
}
