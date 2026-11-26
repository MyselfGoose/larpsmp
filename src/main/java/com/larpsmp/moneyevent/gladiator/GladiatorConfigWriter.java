package com.larpsmp.moneyevent.gladiator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persists Gladiators geometry and prize settings into {@code config.yml}.
 */
public final class GladiatorConfigWriter {

    private GladiatorConfigWriter() {
    }

    public static void write(JavaPlugin plugin, GladiatorConfig config) {
        long[] prizes = config.placementPrizes();
        plugin.getConfig().set("gladiators.prizes.first", prizes.length > 0 ? prizes[0] : 0L);
        plugin.getConfig().set("gladiators.prizes.second", prizes.length > 1 ? prizes[1] : 0L);
        plugin.getConfig().set("gladiators.prizes.third", prizes.length > 2 ? prizes[2] : 0L);

        writeCuboid(plugin, "gladiators.arena", config.arena());
        writeCuboid(plugin, "gladiators.seats", config.seats());
        writeCuboid(plugin, "gladiators.barriers", config.barriers());

        Map<String, Object> teamSpawns = new LinkedHashMap<>();
        for (Map.Entry<String, List<GladiatorSpawnPoint>> entry : config.teamSpawnsBySlug().entrySet()) {
            List<Map<String, Object>> pads = new ArrayList<>();
            for (GladiatorSpawnPoint spawn : entry.getValue()) {
                pads.add(spawnMap(spawn));
            }
            teamSpawns.put(entry.getKey(), pads);
        }
        plugin.getConfig().set("gladiators.team-spawns", teamSpawns);

        plugin.getConfig().set("gladiators.duel-spawns.a", spawnMap(config.duelSpawnA()));
        plugin.getConfig().set("gladiators.duel-spawns.b", spawnMap(config.duelSpawnB()));
        plugin.getConfig().set("gladiators.seats-spawn", spawnMap(config.seatsSpawn()));

        GladiatorConfig.Messages messages = config.messages();
        plugin.getConfig().set("gladiators.messages.session-opened", messages.sessionOpened());
        plugin.getConfig().set("gladiators.messages.session-stopped", messages.sessionStopped());
        plugin.getConfig().set("gladiators.messages.fight-started", messages.fightStarted());
        plugin.getConfig().set("gladiators.messages.fight-won", messages.fightWon());
        plugin.getConfig().set("gladiators.messages.fight-draw", messages.fightDraw());
        plugin.getConfig().set("gladiators.messages.fight-skipped", messages.fightSkipped());
        plugin.getConfig().set("gladiators.messages.duel-nominated", messages.duelNominated());
        plugin.getConfig().set("gladiators.messages.kit-banned", messages.kitBanned());
        plugin.getConfig().set("gladiators.messages.duel-started", messages.duelStarted());
        plugin.getConfig().set("gladiators.messages.payout-done", messages.payoutDone());

        plugin.saveConfig();
    }

    private static void writeCuboid(JavaPlugin plugin, String path, GladiatorCuboid cuboid) {
        if (!cuboid.isDefined()) {
            plugin.getConfig().set(path, null);
            return;
        }
        plugin.getConfig().set(path + ".world", cuboid.world());
        plugin.getConfig().set(path + ".min-x", cuboid.minX());
        plugin.getConfig().set(path + ".min-y", cuboid.minY());
        plugin.getConfig().set(path + ".min-z", cuboid.minZ());
        plugin.getConfig().set(path + ".max-x", cuboid.maxX());
        plugin.getConfig().set(path + ".max-y", cuboid.maxY());
        plugin.getConfig().set(path + ".max-z", cuboid.maxZ());
    }

    private static Map<String, Object> spawnMap(GladiatorSpawnPoint spawn) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (!spawn.isDefined()) {
            return row;
        }
        row.put("world", spawn.world());
        row.put("x", spawn.x());
        row.put("y", spawn.y());
        row.put("z", spawn.z());
        row.put("yaw", spawn.yaw());
        row.put("pitch", spawn.pitch());
        return row;
    }
}
