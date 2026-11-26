package com.larpsmp.moneyevent.gladiator;

/**
 * Confirmed Gladiators constants and defaults.
 */
public final class GladiatorRules {

    public static final String EVENT_ID = "gladiators";

    public static final int MAX_FIGHTS = 4;
    public static final int TEAM_FIGHT_SIZE = 3;
    public static final int DUEL_KIT_COUNT = 5;

    public static final String TEAM_KIT_ID = "team";

    public static final long DEFAULT_PRIZE_FIRST = 125L;
    public static final long DEFAULT_PRIZE_SECOND = 50L;
    public static final long DEFAULT_PRIZE_THIRD = 0L;

    /** Max blocks a barrier fill may cover (prevents accidental arena-volume fills). */
    public static final int MAX_BARRIER_BLOCKS = 16_384;

    private GladiatorRules() {
    }

    public static String duelKitId(int oneBased) {
        if (oneBased < 1 || oneBased > DUEL_KIT_COUNT) {
            throw new IllegalArgumentException("Duel kit must be 1–" + DUEL_KIT_COUNT + ", got " + oneBased);
        }
        return String.valueOf(oneBased);
    }

    public static boolean isDuelKitId(String kitId) {
        if (kitId == null || kitId.isBlank()) {
            return false;
        }
        try {
            int value = Integer.parseInt(kitId.trim());
            return value >= 1 && value <= DUEL_KIT_COUNT;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
