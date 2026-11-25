package com.larpsmp.moneyevent.acehunt;

import java.time.Duration;

/**
 * Confirmed Ace Hunt constants and defaults.
 */
public final class AceHuntRules {

    public static final String EVENT_ID = "ace-hunt";

    public static final int POINTS_FIRST = 2;
    public static final int POINTS_SECOND = 1;
    public static final int POINTS_THIRD = 0;

    public static final Duration DEFAULT_HUNT_DURATION = Duration.ofMinutes(18);
    public static final Duration DEFAULT_SURFACE_WARNING = Duration.ofSeconds(60);
    public static final Duration DEFAULT_RESPAWN_LOCK = Duration.ofMinutes(3);

    /** Half-hearts of damage each second while underground after the ban. */
    public static final double DEFAULT_UNDERGROUND_DAMAGE = 4.0;

    private AceHuntRules() {
    }

    /**
     * Points for placement index 0 (1st), 1 (2nd), 2 (3rd). Out of range → 0.
     */
    public static int pointsForPlacement(int zeroBasedPlace) {
        return switch (zeroBasedPlace) {
            case 0 -> POINTS_FIRST;
            case 1 -> POINTS_SECOND;
            case 2 -> POINTS_THIRD;
            default -> 0;
        };
    }
}
