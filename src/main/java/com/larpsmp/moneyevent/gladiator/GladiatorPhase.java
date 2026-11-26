package com.larpsmp.moneyevent.gladiator;

/**
 * Lifecycle of one Gladiators session.
 */
public enum GladiatorPhase {
    IDLE,
    OPEN,
    TEAM_FIGHT,
    KIT_BAN,
    DUEL,
    ENDED;

    public boolean isLive() {
        return this == OPEN || this == TEAM_FIGHT || this == KIT_BAN || this == DUEL;
    }

    public boolean isFightActive() {
        return this == TEAM_FIGHT || this == DUEL;
    }

    public boolean allowsBetweenBouts() {
        return this == OPEN;
    }
}
