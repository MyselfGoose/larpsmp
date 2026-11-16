package com.larpsmp.moneyevent.capture;

import java.time.Instant;
import java.util.Objects;

/**
 * Temporary block placed on an active capture platform.
 */
public record CaptureTempBlock(
        String world,
        int x,
        int y,
        int z,
        String priorBlockData,
        String placedMaterial,
        Instant placedAt,
        Instant expiresAt
) {
    public CaptureTempBlock {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(priorBlockData, "priorBlockData");
        Objects.requireNonNull(placedMaterial, "placedMaterial");
        Objects.requireNonNull(placedAt, "placedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (world.isBlank()) {
            throw new IllegalArgumentException("world cannot be blank");
        }
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }
}
