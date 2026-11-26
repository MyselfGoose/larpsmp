package com.larpsmp.moneyevent.gladiator;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Axis-aligned region. Incomplete until both corners are defined.
 */
public record GladiatorCuboid(
        @Nullable String world,
        @Nullable Integer minX,
        @Nullable Integer minY,
        @Nullable Integer minZ,
        @Nullable Integer maxX,
        @Nullable Integer maxY,
        @Nullable Integer maxZ
) {
    public GladiatorCuboid {
        if (world != null && world.isBlank()) {
            world = null;
        }
    }

    public static GladiatorCuboid undefined() {
        return new GladiatorCuboid(null, null, null, null, null, null, null);
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

    public GladiatorCuboid withCorners(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
        return new GladiatorCuboid(
                worldName.trim(),
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        );
    }

    public long blockVolume() {
        requireDefined();
        long width = (long) maxX - minX + 1L;
        long height = (long) maxY - minY + 1L;
        long depth = (long) maxZ - minZ + 1L;
        return width * height * depth;
    }

    public double clampX(double x) {
        requireDefined();
        return Math.max(minX + 0.5, Math.min(maxX + 0.5, x));
    }

    public double clampY(double y) {
        requireDefined();
        return Math.max(minY + 0.0, Math.min(maxY + 0.0, y));
    }

    public double clampZ(double z) {
        requireDefined();
        return Math.max(minZ + 0.5, Math.min(maxZ + 0.5, z));
    }

    private void requireDefined() {
        if (!isDefined()) {
            throw new IllegalStateException("Cuboid is not defined");
        }
    }
}
