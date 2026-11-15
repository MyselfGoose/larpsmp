package com.larpsmp.moneyevent.combat;

import java.time.Duration;

/**
 * Confirmed combat attribution constants.
 */
public final class CombatRules {

    /** Last attacker receives credit if the victim dies within this window. */
    public static final Duration ATTRIBUTION_WINDOW = Duration.ofSeconds(60);

    private CombatRules() {
    }
}
