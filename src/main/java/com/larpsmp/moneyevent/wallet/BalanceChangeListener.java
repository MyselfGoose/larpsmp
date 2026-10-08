package com.larpsmp.moneyevent.wallet;

import java.util.UUID;

@FunctionalInterface
public interface BalanceChangeListener {
    void balanceChanged(UUID walletId, long newBalance);
}
