package com.larpsmp.moneyevent.spawn;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory newly-respawned protection window per Minecraft player UUID.
 */
public final class SpawnProtectionTracker {

    private final ConcurrentHashMap<UUID, Instant> expiresAtByPlayer = new ConcurrentHashMap<>();
    private final Duration duration;
    private final Clock clock;

    public SpawnProtectionTracker(Duration duration) {
        this(duration, Clock.systemUTC());
    }

    public SpawnProtectionTracker(Duration duration, Clock clock) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        this.duration = duration;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Duration duration() {
        return duration;
    }

    public void grant(UUID minecraftUuid) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        expiresAtByPlayer.put(minecraftUuid, clock.instant().plus(duration));
    }

    public boolean isProtected(UUID minecraftUuid) {
        return isProtected(minecraftUuid, clock.instant());
    }

    public boolean isProtected(UUID minecraftUuid, Instant now) {
        Objects.requireNonNull(minecraftUuid, "minecraftUuid");
        Objects.requireNonNull(now, "now");
        Instant expiresAt = expiresAtByPlayer.get(minecraftUuid);
        if (expiresAt == null) {
            return false;
        }
        if (isExpired(expiresAt, now)) {
            expiresAtByPlayer.remove(minecraftUuid, expiresAt);
            return false;
        }
        return true;
    }

    public void clear(UUID minecraftUuid) {
        if (minecraftUuid != null) {
            expiresAtByPlayer.remove(minecraftUuid);
        }
    }

    public void clearExpired() {
        Instant now = clock.instant();
        expiresAtByPlayer.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    /** Protected through the exact expiry instant; expires strictly after. */
    private static boolean isExpired(Instant expiresAt, Instant now) {
        return expiresAt.isBefore(now);
    }
}
