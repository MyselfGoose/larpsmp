package com.larpsmp.moneyevent.wallet;

import java.util.UUID;

public record WalletAccount(UUID playerId, String lastKnownUsername, long balance, boolean initialized) {
    static WalletAccount from(Wallet wallet) {
        return new WalletAccount(wallet.ownerId(), wallet.lastKnownUsername(),
                wallet.balance(), wallet.startingBalanceGranted());
    }
}
