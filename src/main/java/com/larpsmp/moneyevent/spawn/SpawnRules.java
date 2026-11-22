package com.larpsmp.moneyevent.spawn;

import java.time.Duration;

/**
 * Confirmed spawn-protection defaults.
 */
public final class SpawnRules {

    /** Default length of newly-respawned player-damage immunity. */
    public static final Duration DEFAULT_PROTECTION = Duration.ofSeconds(10);

    private SpawnRules() {
    }
}
