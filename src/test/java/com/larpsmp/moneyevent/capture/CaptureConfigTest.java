package com.larpsmp.moneyevent.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CaptureConfigTest {

    @Test
    void defaultsIncludeThreeNamedPointsAndDurationTable() {
        CaptureConfig config = CaptureConfig.defaults();
        assertEquals(3, config.points().size());
        assertTrue(config.point("quarry").isPresent());
        assertTrue(config.point("tower").isPresent());
        assertTrue(config.point("ruins").isPresent());
        assertEquals(600_000L, config.fullCaptureMillis(1));
        assertEquals(420_000L, config.fullCaptureMillis(2));
        assertEquals(300_000L, config.fullCaptureMillis(3));
        assertEquals(210_000L, config.fullCaptureMillis(4));
        assertEquals(300_000L, config.decayFullMillis());
        assertEquals(3, config.heightBlocks());
        assertEquals(30, config.tempBlockSeconds());
    }

    @Test
    void loadsFromYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("capture.tick-seconds", 2);
        yaml.set("capture.height-blocks", 3);
        yaml.set("capture.temp-block-seconds", 45);
        yaml.set("capture.decay-multiplier", 1.5);
        yaml.set("capture.durations-seconds.1", 100);
        yaml.set("capture.durations-seconds.2", 50);
        yaml.set("capture.points", java.util.List.of(
                java.util.Map.of(
                        "id", "alpha",
                        "name", "Alpha Point",
                        "world", "world",
                        "min-x", 0,
                        "min-y", 64,
                        "min-z", 0,
                        "max-x", 10,
                        "max-y", 64,
                        "max-z", 10
                )
        ));
        yaml.set("capture.messages.open", "OPEN {point}");

        CaptureConfig config = CaptureConfig.from(yaml);
        assertEquals(2, config.tickSeconds());
        assertEquals(45, config.tempBlockSeconds());
        assertEquals(1.5, config.decayMultiplier());
        assertEquals(100_000L, config.fullCaptureMillis(1));
        assertEquals(50_000L, config.fullCaptureMillis(2));
        assertEquals(1, config.points().size());
        CapturePointDefinition point = config.point("alpha").orElseThrow();
        assertEquals("Alpha Point", point.displayName());
        assertTrue(point.isRegionDefined());
        assertTrue(point.containsOccupant(5.5, 64.0, 5.5, 3));
        assertFalse(point.containsOccupant(5.5, 68.0, 5.5, 3));
        assertEquals("OPEN {point}", config.messages().open());
    }

    @Test
    void pointOccupancyUsesPlatformPlusHeight() {
        CapturePointDefinition point = new CapturePointDefinition(
                "test", "Test", "world", 0, 70, 0, 5, 70, 5);
        assertTrue(point.containsOccupant(1.2, 70.0, 1.2, 3));
        assertTrue(point.containsOccupant(1.2, 72.9, 1.2, 3));
        assertFalse(point.containsOccupant(1.2, 74.0, 1.2, 3));
        assertFalse(point.containsOccupant(6.0, 70.0, 1.2, 3));
        assertTrue(point.containsBlock(2, 70, 2));
        assertFalse(point.containsBlock(2, 71, 2));
    }
}
