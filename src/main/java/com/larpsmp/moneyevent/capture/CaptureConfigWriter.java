package com.larpsmp.moneyevent.capture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Writes {@link CaptureConfig} point definitions back into {@code config.yml}.
 */
public final class CaptureConfigWriter {

    private CaptureConfigWriter() {
    }

    public static void writePoints(JavaPlugin plugin, CaptureConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        FileConfiguration yaml = plugin.getConfig();
        List<Map<String, Object>> points = new ArrayList<>();
        for (CapturePointDefinition point : config.points()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", point.id());
            row.put("name", point.displayName());
            row.put("world", point.world() == null ? "" : point.world());
            if (point.minX() != null) {
                row.put("min-x", point.minX());
                row.put("min-y", point.minY());
                row.put("min-z", point.minZ());
                row.put("max-x", point.maxX());
                row.put("max-y", point.maxY());
                row.put("max-z", point.maxZ());
            }
            points.add(row);
        }
        yaml.set("capture.points", points);
        plugin.saveConfig();
    }
}
