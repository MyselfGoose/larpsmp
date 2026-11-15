package com.larpsmp.moneyevent.wallet;

import com.larpsmp.moneyevent.team.PlayerTier;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Confirmed death payload from kill attribution (or tests). Economy settles or no-ops.
 */
public record DeathPaymentRequest(
        String deathId,
        UUID victimAccountId,
        UUID killerAccountId,
        PlayerTier victimTier,
        boolean friendlyFire,
        @Nullable String evidence) {

    public DeathPaymentRequest {
        Objects.requireNonNull(deathId, "deathId");
        if (deathId.isBlank()) {
            throw new IllegalArgumentException("deathId cannot be blank");
        }
        Objects.requireNonNull(victimAccountId, "victimAccountId");
        Objects.requireNonNull(killerAccountId, "killerAccountId");
        Objects.requireNonNull(victimTier, "victimTier");
        evidence = evidence == null ? "" : evidence;
    }
}
