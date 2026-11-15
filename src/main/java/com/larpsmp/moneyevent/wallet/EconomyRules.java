package com.larpsmp.moneyevent.wallet;

import com.larpsmp.moneyevent.team.PlayerTier;

/**
 * Confirmed competition money constants. Kill prices may be overridden in persistence;
 * these values are the defaults and rule-of-record fallbacks.
 */
public final class EconomyRules {
    public static final long STARTING_BALANCE = 200L;
    public static final String STARTING_BALANCE_REASON = "Initial competitor balance";

    public static final long DEATH_PRICE_TIER_1 = 40L;
    public static final long DEATH_PRICE_TIER_2 = 30L;
    public static final long DEATH_PRICE_TIER_3 = 20L;
    public static final long DEATH_PRICE_TIER_4 = 15L;

    public static final long RESCUE_THRESHOLD = 40L;
    public static final long RESCUE_PER_MEMBER = 25L;
    public static final long RESCUE_PER_TEAM = 100L;

    public static final long CAPTURE_REWARD = 50L;

    public static final String SETTLEMENT_KIND_DEATH = "DEATH";
    public static final String SETTLEMENT_KIND_RESCUE = "RESCUE";
    public static final String SETTLEMENT_KIND_CAPTURE = "CAPTURE";

    public static final String OUTCOME_APPLIED = "APPLIED";
    public static final String OUTCOME_NO_OP = "NO_OP";
    public static final String OUTCOME_SKIPPED_KILL_MONEY_OFF = "SKIPPED_KILL_MONEY_OFF";
    public static final String OUTCOME_SKIPPED_FRIENDLY_FIRE = "SKIPPED_FRIENDLY_FIRE";

    private EconomyRules() {
    }

    public static long defaultDeathPrice(PlayerTier tier) {
        return switch (tier) {
            case TIER_1 -> DEATH_PRICE_TIER_1;
            case TIER_2 -> DEATH_PRICE_TIER_2;
            case TIER_3 -> DEATH_PRICE_TIER_3;
            case TIER_4 -> DEATH_PRICE_TIER_4;
        };
    }

    public static long defaultDeathPrice(int tier) {
        return switch (tier) {
            case 1 -> DEATH_PRICE_TIER_1;
            case 2 -> DEATH_PRICE_TIER_2;
            case 3 -> DEATH_PRICE_TIER_3;
            case 4 -> DEATH_PRICE_TIER_4;
            default -> throw new IllegalArgumentException("Tier must be 1–4, got " + tier);
        };
    }
}
