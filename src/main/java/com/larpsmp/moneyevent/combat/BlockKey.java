package com.larpsmp.moneyevent.combat;

import java.util.Objects;

/**
 * World-block coordinate key for ownership tracking (Paper-free).
 */
public record BlockKey(String worldName, int x, int y, int z) {

    public BlockKey {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
    }

    public static BlockKey of(String worldName, int x, int y, int z) {
        return new BlockKey(worldName, x, y, z);
    }
}
