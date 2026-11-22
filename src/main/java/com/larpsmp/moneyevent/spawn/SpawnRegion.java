package com.larpsmp.moneyevent.spawn;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Axis-aligned spawn box from config. Incomplete until both corners are defined.
 */
public record SpawnRegion(
        @Nullable String world,
        @Nullable Integer minX,
        @Nullable Integer minY,
        @Nullable Integer minZ,
        @Nullable Integer maxX,
        @Nullable Integer maxY,
        @Nullable Integer maxZ
) {
    public SpawnRegion {
        if (world != null && world.isBlank()) {
            world = null;
        }
    }

    public static SpawnRegion undefined() {
        return new SpawnRegion(null, null, null, null, null, null, null);
    }

    public boolean isDefined() {
        return world != null
                && minX != null && minY != null && minZ != null
                && maxX != null && maxY != null && maxZ != null;
    }

    public boolean containsBlock(String worldName, int x, int y, int z) {
        if (!isDefined() || worldName == null) {
            return false;
        }
        if (!world.equalsIgnoreCase(worldName)) {
            return false;
        }
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Player feet / entity location containment (block-aligned AABB inclusive on max faces).
     */
    public boolean containsLocation(String worldName, double x, double y, double z) {
        if (!isDefined() || worldName == null) {
            return false;
        }
        if (!world.equalsIgnoreCase(worldName)) {
            return false;
        }
        return x >= minX && x < maxX + 1
                && y >= minY && y < maxY + 1
                && z >= minZ && z < maxZ + 1;
    }

    public SpawnRegion withCorners(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
        return new SpawnRegion(
                worldName.trim(),
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        );
    }
}
