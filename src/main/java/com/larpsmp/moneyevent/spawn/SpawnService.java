package com.larpsmp.moneyevent.spawn;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime spawn-protection state: config + protection tracker.
 */
public final class SpawnService {

    private final AtomicReference<SpawnConfig> config;
    private final AtomicReference<SpawnProtectionTracker> tracker;
    private final Clock clock;

    public SpawnService(SpawnConfig config) {
        this(config, Clock.systemUTC());
    }

    public SpawnService(SpawnConfig config, Clock clock) {
        Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = new AtomicReference<>(config);
        this.tracker = new AtomicReference<>(new SpawnProtectionTracker(config.protectionDuration(), clock));
    }

    public SpawnConfig config() {
        return config.get();
    }

    public SpawnProtectionTracker tracker() {
        return tracker.get();
    }

    public SpawnRegion region() {
        return config.get().region();
    }

    public void replaceConfig(SpawnConfig updated) {
        Objects.requireNonNull(updated, "updated");
        config.set(updated);
        tracker.set(new SpawnProtectionTracker(updated.protectionDuration(), clock));
    }

    public void replaceRegion(SpawnRegion region) {
        SpawnConfig updated = config.get().withRegion(region);
        config.set(updated);
        // Keep existing protection windows when only the region changes.
    }
}
