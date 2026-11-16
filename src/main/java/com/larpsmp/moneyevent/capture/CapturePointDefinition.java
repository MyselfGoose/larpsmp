package com.larpsmp.moneyevent.capture;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Named capture location from config. Region may be incomplete until admin defines corners.
 */
public record CapturePointDefinition(
        String id,
        String displayName,
        @Nullable String world,
        @Nullable Integer minX,
        @Nullable Integer minY,
        @Nullable Integer minZ,
        @Nullable Integer maxX,
        @Nullable Integer maxY,
        @Nullable Integer maxZ
) {
    public CapturePointDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        id = id.trim().toLowerCase(java.util.Locale.ROOT);
        displayName = displayName.trim();
        if (world != null && world.isBlank()) {
            world = null;
        }
    }

    public boolean isRegionDefined() {
        return world != null
                && minX != null && minY != null && minZ != null
                && maxX != null && maxY != null && maxZ != null;
    }

    /** Platform surface Y (bottom of the capture volume). */
    public int platformY() {
        if (minY == null) {
            throw new IllegalStateException("Region not defined for point " + id);
        }
        return minY;
    }

    public boolean containsBlock(int x, int y, int z) {
        if (!isRegionDefined()) {
            return false;
        }
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Blocks inside the capture fight volume: region XZ, from platform Y up through
     * {@code heightBlocks} (and any configured maxY).
     */
    public boolean containsVolumeBlock(int x, int y, int z, int heightBlocks) {
        if (!isRegionDefined()) {
            return false;
        }
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }
        int top = Math.max(maxY, platformY() + heightBlocks);
        return y >= platformY() && y <= top;
    }

    /**
     * Player occupancy: inside XZ of the region, from platform surface up {@code heightBlocks}.
     */
    public boolean containsOccupant(double x, double y, double z, int heightBlocks) {
        if (!isRegionDefined()) {
            return false;
        }
        if (x < minX || x >= maxX + 1
                || z < minZ || z >= maxZ + 1) {
            return false;
        }
        double top = platformY() + heightBlocks + 1.0;
        return y >= platformY() && y < top;
    }

    public CapturePointDefinition withCorners(
            String worldName,
            int x1, int y1, int z1,
            int x2, int y2, int z2) {
        return new CapturePointDefinition(
                id,
                displayName,
                worldName,
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        );
    }
}
