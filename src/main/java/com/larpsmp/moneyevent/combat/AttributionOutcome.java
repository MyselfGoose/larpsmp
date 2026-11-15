package com.larpsmp.moneyevent.combat;

import com.larpsmp.moneyevent.team.PlayerTier;
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
        @Nullable PlayerTier victimTier,
        @Nullable String victimUsername,
        @Nullable String killerUsername,
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
        return new AttributionOutcome(Kind.ENVIRONMENTAL, null, null, null, null, null, null, detail);
    }

    public static AttributionOutcome skipped(String detail) {
        return new AttributionOutcome(Kind.SKIPPED, null, null, null, null, null, null, detail);
    }

    public static AttributionOutcome attributed(
            String deathId,
            DeathPaymentResult settlement,
            CombatKillRecord killRecord,
            PlayerTier victimTier,
            String victimUsername,
            String killerUsername
    ) {
        return new AttributionOutcome(
                Kind.ATTRIBUTED,
                Objects.requireNonNull(deathId, "deathId"),
                Objects.requireNonNull(settlement, "settlement"),
                Objects.requireNonNull(killRecord, "killRecord"),
                Objects.requireNonNull(victimTier, "victimTier"),
                Objects.requireNonNull(victimUsername, "victimUsername"),
                Objects.requireNonNull(killerUsername, "killerUsername"),
                settlement.detail()
        );
    }

    public static AttributionOutcome duplicate(String deathId, CombatKillRecord existing) {
        return new AttributionOutcome(
                Kind.DUPLICATE,
                deathId,
                null,
                existing,
                null,
                null,
                null,
                "Kill already recorded"
        );
    }

    public boolean attributed() {
        return kind == Kind.ATTRIBUTED;
    }
}
