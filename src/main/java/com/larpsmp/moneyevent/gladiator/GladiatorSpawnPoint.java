package com.larpsmp.moneyevent.gladiator;

import org.jetbrains.annotations.Nullable;

/**
 * Named spawn pad for arena fighters or seats.
 */
public record GladiatorSpawnPoint(
        @Nullable String world,
        @Nullable Double x,
        @Nullable Double y,
        @Nullable Double z,
        float yaw,
        float pitch
) {
    public GladiatorSpawnPoint {
        if (world != null && world.isBlank()) {
            world = null;
        }
    }

    public static GladiatorSpawnPoint undefined() {
        return new GladiatorSpawnPoint(null, null, null, null, 0.0f, 0.0f);
    }

    public boolean isDefined() {
        return world != null && x != null && y != null && z != null;
    }

    public GladiatorSpawnPoint at(String worldName, double x, double y, double z, float yaw, float pitch) {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
        return new GladiatorSpawnPoint(worldName.trim(), x, y, z, yaw, pitch);
    }
}
