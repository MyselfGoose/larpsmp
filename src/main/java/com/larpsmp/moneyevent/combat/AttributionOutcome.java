package com.larpsmp.moneyevent.combat;

import com.larpsmp.moneyevent.wallet.DeathPaymentResult;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Result of attempting kill attribution for one death.
 */
public record AttributionOutcome(
        Kind kind,
        @Nullable String deathId,
        @Nullable DeathPaymentResult settlement,
        @Nullable CombatKillRecord killRecord,
        String detail
) {

    public enum Kind {
        /** Confirmed kill settled (or no-op'd) through economy. */
        ATTRIBUTED,
        /** No qualifying last attacker within the window. */
        ENVIRONMENTAL,
        /** Attacker present but accounts/tier missing — no money call. */
        SKIPPED,
        /** Already processed this death id. */
        DUPLICATE
    }

    public AttributionOutcome {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
    }

    public static AttributionOutcome environmental(String detail) {
        return new AttributionOutcome(Kind.ENVIRONMENTAL, null, null, null, detail);
    }

    public static AttributionOutcome skipped(String detail) {
        return new AttributionOutcome(Kind.SKIPPED, null, null, null, detail);
    }

    public static AttributionOutcome attributed(
            String deathId,
            DeathPaymentResult settlement,
            CombatKillRecord killRecord
    ) {
        return new AttributionOutcome(
                Kind.ATTRIBUTED,
                Objects.requireNonNull(deathId, "deathId"),
                Objects.requireNonNull(settlement, "settlement"),
                Objects.requireNonNull(killRecord, "killRecord"),
                settlement.detail()
        );
    }

    public boolean attributed() {
        return kind == Kind.ATTRIBUTED;
    }
}
