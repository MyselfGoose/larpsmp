package com.larpsmp.moneyevent.wallet;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record DeathPaymentResult(
        String deathId,
        String outcome,
        long owedAmount,
        long chargedTotal,
        boolean rescueActivated,
        @Nullable UUID parentTransactionId,
        @Nullable UUID rescueParentTransactionId,
        List<SettlementContribution> contributions,
        String detail) {

    public DeathPaymentResult {
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
        detail = detail == null ? "" : detail;
    }

    public boolean applied() {
        return EconomyRules.OUTCOME_APPLIED.equals(outcome);
    }

    public boolean noOp() {
        return !applied();
    }
}
