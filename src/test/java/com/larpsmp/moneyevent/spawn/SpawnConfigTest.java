package com.larpsmp.moneyevent.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SpawnConfigTest {

    @Test
    void defaultsAreTenSecondsWithUndefinedRegion() {
        SpawnConfig config = SpawnConfig.defaults();
        assertEquals(10, config.protectionSeconds());
        assertFalse(config.region().isDefined());
    }

    @Test
    void loadsFromYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawn.protection-seconds", 15);
        yaml.set("spawn.region.world", "world");
        yaml.set("spawn.region.min-x", -10);
        yaml.set("spawn.region.min-y", 60);
        yaml.set("spawn.region.min-z", -10);
        yaml.set("spawn.region.max-x", 10);
        yaml.set("spawn.region.max-y", 80);
        yaml.set("spawn.region.max-z", 10);

        SpawnConfig config = SpawnConfig.from(yaml);
        assertEquals(15, config.protectionSeconds());
        assertTrue(config.region().isDefined());
        assertTrue(config.region().containsBlock("world", 0, 64, 0));
        assertFalse(config.region().containsBlock("world", 11, 64, 0));
    }

    @Test
    void blankWorldMeansUndefinedRegion() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawn.protection-seconds", 10);
        yaml.set("spawn.region.world", "");
        yaml.set("spawn.region.min-x", 0);
        yaml.set("spawn.region.min-y", 0);
        yaml.set("spawn.region.min-z", 0);
        yaml.set("spawn.region.max-x", 10);
        yaml.set("spawn.region.max-y", 10);
        yaml.set("spawn.region.max-z", 10);

        SpawnConfig config = SpawnConfig.from(yaml);
        assertFalse(config.region().isDefined());
    }

    @Test
    void clampsProtectionSecondsToAtLeastOne() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawn.protection-seconds", 0);
        SpawnConfig config = SpawnConfig.from(yaml);
        assertEquals(1, config.protectionSeconds());
    }

    @Test
    void withRegionPreservesDuration() {
        SpawnConfig config = SpawnConfig.defaults()
                .withRegion(SpawnRegion.undefined().withCorners("world", 0, 64, 0, 5, 70, 5));
        assertEquals(10, config.protectionSeconds());
        assertTrue(config.region().isDefined());
    }
}
