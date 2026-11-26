package com.larpsmp.moneyevent.gladiator;

import java.util.Objects;

/**
 * Prior block data for a barrier fill undo.
 */
public record GladiatorBarrierBlock(
        String worldName,
        int x,
        int y,
        int z,
        String priorBlockData
) {
    public GladiatorBarrierBlock {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
        Objects.requireNonNull(priorBlockData, "priorBlockData");
    }
}
