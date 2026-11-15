package com.larpsmp.moneyevent.playerstate;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Typed player-body persistence settings from {@code config.yml}.
 */
public record PlayerStateConfig(
        int autosaveSeconds,
        DefaultSpawn defaultSpawn
) {

    public record DefaultSpawn(
            @Nullable String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        public boolean hasWorld() {
            return world != null && !world.isBlank();
        }
    }

    public static PlayerStateConfig from(FileConfiguration config) {
        int autosave = Math.max(0, config.getInt("player-state.autosave-seconds", 60));
        String world = config.getString("player-state.default-spawn.world", "");
        if (world != null && world.isBlank()) {
            world = null;
        }
        return new PlayerStateConfig(
                autosave,
                new DefaultSpawn(
                        world,
                        config.getDouble("player-state.default-spawn.x", 0.5),
                        config.getDouble("player-state.default-spawn.y", 64.0),
                        config.getDouble("player-state.default-spawn.z", 0.5),
                        (float) config.getDouble("player-state.default-spawn.yaw", 0.0),
                        (float) config.getDouble("player-state.default-spawn.pitch", 0.0)
                )
        );
    }
}
