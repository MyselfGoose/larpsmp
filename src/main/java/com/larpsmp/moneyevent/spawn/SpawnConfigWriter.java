package com.larpsmp.moneyevent.spawn;

import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Writes {@link SpawnConfig} region definitions back into {@code config.yml}.
 */
public final class SpawnConfigWriter {

    private SpawnConfigWriter() {
    }

    public static void write(JavaPlugin plugin, SpawnConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        FileConfiguration yaml = plugin.getConfig();
        yaml.set("spawn.protection-seconds", config.protectionSeconds());
        SpawnRegion region = config.region();
        if (region.isDefined()) {
            yaml.set("spawn.region.world", region.world());
            yaml.set("spawn.region.min-x", region.minX());
            yaml.set("spawn.region.min-y", region.minY());
            yaml.set("spawn.region.min-z", region.minZ());
            yaml.set("spawn.region.max-x", region.maxX());
            yaml.set("spawn.region.max-y", region.maxY());
            yaml.set("spawn.region.max-z", region.maxZ());
        } else {
            yaml.set("spawn.region.world", "");
            yaml.set("spawn.region.min-x", 0);
            yaml.set("spawn.region.min-y", 0);
            yaml.set("spawn.region.min-z", 0);
            yaml.set("spawn.region.max-x", 0);
            yaml.set("spawn.region.max-y", 0);
            yaml.set("spawn.region.max-z", 0);
        }
        plugin.saveConfig();
    }
}
