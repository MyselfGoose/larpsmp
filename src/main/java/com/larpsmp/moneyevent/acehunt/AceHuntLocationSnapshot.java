package com.larpsmp.moneyevent.acehunt;

import java.util.Objects;
import java.util.UUID;

/**
 * Pre-event location restored when the Ace Hunt session ends.
 */
public record AceHuntLocationSnapshot(
        UUID accountId,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        ParticipantRole role
) {
    public AceHuntLocationSnapshot {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(role, "role");
    }

    public enum ParticipantRole {
        ACE,
        HUNTER
    }
}
