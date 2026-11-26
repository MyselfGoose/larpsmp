package com.larpsmp.moneyevent.acehunt;

import org.jetbrains.annotations.Nullable;

/**
 * Named spawn inside a territory or lockup.
 */
public record AceHuntSpawnPoint(
        @Nullable String world,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        float yaw,
        float pitch
) {
    public AceHuntSpawnPoint {
        if (world != null && world.isBlank()) {
            world = null;
        }
    }

    public static AceHuntSpawnPoint undefined() {
        return new AceHuntSpawnPoint(null, null, null, null, 0.0f, 0.0f);
    }

    public boolean isDefined() {
        return world != null && x != null && y != null && z != null;
    }

    public AceHuntSpawnPoint at(String worldName, double x, double y, double z, float yaw, float pitch) {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
        return new AceHuntSpawnPoint(worldName.trim(), x, y, z, yaw, pitch);
    }
}
