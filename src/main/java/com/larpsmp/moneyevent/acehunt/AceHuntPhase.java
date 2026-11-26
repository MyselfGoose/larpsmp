package com.larpsmp.moneyevent.acehunt;

/**
 * Lifecycle of one Ace Hunt session.
 */
public enum AceHuntPhase {
    IDLE,
    PREP,
    HUNT,
    SURFACE,
    BAN,
    ENDED;

    public boolean isLive() {
        return this == PREP || this == HUNT || this == SURFACE || this == BAN;
    }

    public boolean isScoring() {
        return this == HUNT || this == SURFACE || this == BAN;
    }

    public boolean allowsKeepInventory() {
        return isLive();
    }
}
