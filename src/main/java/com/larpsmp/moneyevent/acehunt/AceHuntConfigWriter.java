package com.larpsmp.moneyevent.acehunt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Writes Ace Hunt territory / lockup definitions back into {@code config.yml}.
 */
public final class AceHuntConfigWriter {

    private AceHuntConfigWriter() {
    }

    public static void write(JavaPlugin plugin, AceHuntConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        FileConfiguration yaml = plugin.getConfig();

        yaml.set("ace-hunt.hunt-seconds", config.huntDuration().toSeconds());
        yaml.set("ace-hunt.surface-warning-seconds", config.surfaceWarning().toSeconds());
        yaml.set("ace-hunt.respawn-lock-seconds", config.respawnLock().toSeconds());
        yaml.set("ace-hunt.underground-damage-per-second", config.undergroundDamagePerSecond());

        int[] hunt = config.huntPlacementPoints();
        yaml.set("ace-hunt.points.hunt.first", hunt[0]);
        yaml.set("ace-hunt.points.hunt.second", hunt[1]);
        yaml.set("ace-hunt.points.hunt.third", hunt[2]);
        int[] survival = config.survivalPlacementPoints();
        yaml.set("ace-hunt.points.survival.first", survival[0]);
        yaml.set("ace-hunt.points.survival.second", survival[1]);
        yaml.set("ace-hunt.points.survival.third", survival[2]);

        List<Map<String, Object>> territories = new ArrayList<>();
        for (AceHuntTerritory territory : config.territories()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("team", territory.teamSlug());
            row.put("team-id", territory.teamId().toString());
            writeCuboid(row, territory.region());
            row.put("ace-spawn", spawnMap(territory.aceSpawn()));
            row.put("hunter-spawn", spawnMap(territory.hunterSpawn()));
            if (territory.surfaceY() != null) {
                row.put("surface-y", territory.surfaceY());
            }
            if (territory.maxLegalY() != null) {
                row.put("max-legal-y", territory.maxLegalY());
            }
            territories.add(row);
        }
        yaml.set("ace-hunt.territories", territories);

        AceHuntCuboid lockup = config.lockup();
        if (lockup.isDefined()) {
            yaml.set("ace-hunt.lockup.world", lockup.world());
            yaml.set("ace-hunt.lockup.min-x", lockup.minX());
            yaml.set("ace-hunt.lockup.min-y", lockup.minY());
            yaml.set("ace-hunt.lockup.min-z", lockup.minZ());
            yaml.set("ace-hunt.lockup.max-x", lockup.maxX());
            yaml.set("ace-hunt.lockup.max-y", lockup.maxY());
            yaml.set("ace-hunt.lockup.max-z", lockup.maxZ());
        } else {
            yaml.set("ace-hunt.lockup.world", "");
        }
        AceHuntSpawnPoint lockupSpawn = config.lockupSpawn();
        if (lockupSpawn.isDefined()) {
            yaml.set("ace-hunt.lockup.spawn.world", lockupSpawn.world());
            yaml.set("ace-hunt.lockup.spawn.x", lockupSpawn.x());
            yaml.set("ace-hunt.lockup.spawn.y", lockupSpawn.y());
            yaml.set("ace-hunt.lockup.spawn.z", lockupSpawn.z());
            yaml.set("ace-hunt.lockup.spawn.yaw", lockupSpawn.yaw());
            yaml.set("ace-hunt.lockup.spawn.pitch", lockupSpawn.pitch());
        } else {
            yaml.set("ace-hunt.lockup.spawn.world", "");
        }

        AceHuntConfig.Messages messages = config.messages();
        yaml.set("ace-hunt.messages.prep-started", messages.prepStarted());
        yaml.set("ace-hunt.messages.hunt-started", messages.huntStarted());
        yaml.set("ace-hunt.messages.surface-warning", messages.surfaceWarning());
        yaml.set("ace-hunt.messages.underground-ban", messages.undergroundBan());
        yaml.set("ace-hunt.messages.ace-died", messages.aceDied());
        yaml.set("ace-hunt.messages.hunt-scored", messages.huntScored());
        yaml.set("ace-hunt.messages.hunt-missed", messages.huntMissed());
        yaml.set("ace-hunt.messages.event-ended", messages.eventEnded());
        yaml.set("ace-hunt.messages.lockup-enter", messages.lockupEnter());
        yaml.set("ace-hunt.messages.lockup-release", messages.lockupRelease());

        plugin.saveConfig();
    }

    private static void writeCuboid(Map<String, Object> row, AceHuntCuboid cuboid) {
        row.put("world", cuboid.world() == null ? "" : cuboid.world());
        if (cuboid.isDefined()) {
            row.put("min-x", cuboid.minX());
            row.put("min-y", cuboid.minY());
            row.put("min-z", cuboid.minZ());
            row.put("max-x", cuboid.maxX());
            row.put("max-y", cuboid.maxY());
            row.put("max-z", cuboid.maxZ());
        }
    }

    private static Map<String, Object> spawnMap(AceHuntSpawnPoint spawn) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", spawn.world() == null ? "" : spawn.world());
        if (spawn.isDefined()) {
            map.put("x", spawn.x());
            map.put("y", spawn.y());
            map.put("z", spawn.z());
            map.put("yaw", spawn.yaw());
            map.put("pitch", spawn.pitch());
        }
        return map;
    }
}
