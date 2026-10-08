package com.larpsmp.moneyevent.wallet;

import java.util.Objects;
import java.util.UUID;

public final class Wallet {
    private final UUID ownerId;
    private long balance;
    private String lastKnownUsername;
    private boolean startingBalanceGranted;

    Wallet(UUID ownerId, long balance, String lastKnownUsername, boolean startingBalanceGranted) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        setBalance(balance);
        this.lastKnownUsername = requireUsername(lastKnownUsername);
        this.startingBalanceGranted = startingBalanceGranted;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public long balance() {
        return balance;
    }

    public String lastKnownUsername() {
        return lastKnownUsername;
    }

    public boolean startingBalanceGranted() {
        return startingBalanceGranted;
    }

    void setBalance(long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Wallet balance cannot be negative");
        }
        this.balance = balance;
    }

    void setLastKnownUsername(String lastKnownUsername) {
        this.lastKnownUsername = requireUsername(lastKnownUsername);
    }

    void grantStartingBalance(long amount) {
        if (amount < 0 || Long.MAX_VALUE - balance < amount) {
            throw new IllegalArgumentException("Invalid starting balance amount");
        }
        balance += amount;
        startingBalanceGranted = true;
    }

    void restore(long balance, String lastKnownUsername, boolean startingBalanceGranted) {
        setBalance(balance);
        this.lastKnownUsername = requireUsername(lastKnownUsername);
        this.startingBalanceGranted = startingBalanceGranted;
    }

    private static String requireUsername(String username) {
        Objects.requireNonNull(username, "lastKnownUsername");
        if (username.isBlank()) {
            throw new IllegalArgumentException("Last-known username cannot be blank");
        }
        return username;
    }
}
