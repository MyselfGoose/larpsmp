package com.larpsmp.moneyevent.wallet;

import java.util.UUID;

/**
 * One wallet's debit or credit within a multi-party settlement.
 */
public record SettlementContribution(
        UUID accountId,
        String username,
        long amount,
        long balanceBefore,
        long balanceAfter,
        boolean credit) {
}
