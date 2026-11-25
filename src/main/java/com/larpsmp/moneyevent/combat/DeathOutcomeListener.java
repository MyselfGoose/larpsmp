package com.larpsmp.moneyevent.combat;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Notified after kill attribution runs for a player death (including environmental).
 */
@FunctionalInterface
public interface DeathOutcomeListener {

    void onDeathOutcome(
            UUID victimMinecraftUuid,
            AttributionOutcome outcome
    );
}
