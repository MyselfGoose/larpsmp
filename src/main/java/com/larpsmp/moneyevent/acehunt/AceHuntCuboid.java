package com.larpsmp.moneyevent.acehunt;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Axis-aligned region. Incomplete until both corners are defined.
 */
public record AceHuntCuboid(
        @Nullable String world,
        @Nullable Integer minX,
        @Nullable Integer minY,
        @Nullable Integer minZ,
        @Nullable Integer maxX,
        @Nullable Integer maxY,
        @Nullable Integer maxZ
) {
    public AceHuntCuboid {
        if (world != null && world.isBlank()) {
            world = null;
        }
    }

    public static AceHuntCuboid undefined() {
        return new AceHuntCuboid(null, null, null, null, null, null, null);
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

    public AceHuntCuboid withCorners(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
        return new AceHuntCuboid(
                worldName.trim(),
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        );
    }

    public double centerX() {
        requireDefined();
        return (minX + maxX + 1) / 2.0;
    }

    public double centerY() {
        requireDefined();
        return (minY + maxY + 1) / 2.0;
    }

    public double centerZ() {
        requireDefined();
        return (minZ + maxZ + 1) / 2.0;
    }

    /** Diameter of the smallest axis-aligned square that covers the cuboid XZ footprint. */
    public double borderDiameter() {
        requireDefined();
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        return Math.max(width, depth);
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
