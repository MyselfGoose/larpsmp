package com.larpsmp.moneyevent.gladiator;

import java.util.Objects;
import java.util.UUID;

/**
 * One fighter slot in an active or completed fight.
 */
public final class GladiatorFighter {

    private final UUID accountId;
    private final UUID teamId;
    private final int slotIndex;
    private boolean alive;

    public GladiatorFighter(UUID accountId, UUID teamId, int slotIndex, boolean alive) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.slotIndex = slotIndex;
        this.alive = alive;
    }

    public UUID accountId() {
        return accountId;
    }

    public UUID teamId() {
        return teamId;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public boolean alive() {
        return alive;
    }

    void markDead() {
        this.alive = false;
    }

    void setAlive(boolean alive) {
        this.alive = alive;
    }
}
