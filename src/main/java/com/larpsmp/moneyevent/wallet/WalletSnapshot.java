package com.larpsmp.moneyevent.wallet;

import java.util.UUID;

record WalletSnapshot(UUID ownerId, long balance, String lastKnownUsername, boolean startingBalanceGranted) {
    static WalletSnapshot of(Wallet wallet) {
        return new WalletSnapshot(
                wallet.ownerId(), wallet.balance(), wallet.lastKnownUsername(), wallet.startingBalanceGranted());
    }

    Wallet toWallet() {
        return new Wallet(ownerId, balance, lastKnownUsername, startingBalanceGranted);
    }

    void restore(Wallet wallet) {
        wallet.restore(balance, lastKnownUsername, startingBalanceGranted);
    }
}
