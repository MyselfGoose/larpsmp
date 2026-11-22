package com.larpsmp.moneyevent.spawn;

import java.time.Duration;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Typed spawn-protection settings from {@code config.yml}.
 */
public final class SpawnConfig {

    private final Duration protectionDuration;
    private final SpawnRegion region;

    public SpawnConfig(Duration protectionDuration, SpawnRegion region) {
        Objects.requireNonNull(protectionDuration, "protectionDuration");
        if (protectionDuration.isNegative() || protectionDuration.isZero()) {
            throw new IllegalArgumentException("protectionDuration must be positive");
        }
        this.protectionDuration = protectionDuration;
        this.region = Objects.requireNonNull(region, "region");
    }

    public Duration protectionDuration() {
        return protectionDuration;
    }

    public int protectionSeconds() {
        return (int) Math.min(Integer.MAX_VALUE, protectionDuration.toSeconds());
    }

    public SpawnRegion region() {
        return region;
    }

    public SpawnConfig withRegion(SpawnRegion updated) {
        return new SpawnConfig(protectionDuration, updated);
    }

    public static SpawnConfig from(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("spawn");
        if (root == null) {
            return defaults();
        }
        int seconds = Math.max(1, root.getInt("protection-seconds",
                (int) SpawnRules.DEFAULT_PROTECTION.toSeconds()));
        SpawnRegion region = parseRegion(root.getConfigurationSection("region"));
        return new SpawnConfig(Duration.ofSeconds(seconds), region);
    }

    public static SpawnConfig defaults() {
        return new SpawnConfig(SpawnRules.DEFAULT_PROTECTION, SpawnRegion.undefined());
    }

    private static SpawnRegion parseRegion(@Nullable ConfigurationSection section) {
        if (section == null) {
            return SpawnRegion.undefined();
        }
        String world = section.getString("world", "");
        if (world == null || world.isBlank()) {
            return SpawnRegion.undefined();
        }
        if (!section.contains("min-x")
                || !section.contains("min-y")
                || !section.contains("min-z")
                || !section.contains("max-x")
                || !section.contains("max-y")
                || !section.contains("max-z")) {
            return SpawnRegion.undefined();
        }
        return new SpawnRegion(
                world.trim(),
                section.getInt("min-x"),
                section.getInt("min-y"),
                section.getInt("min-z"),
                section.getInt("max-x"),
                section.getInt("max-y"),
                section.getInt("max-z")
        );
    }
}
