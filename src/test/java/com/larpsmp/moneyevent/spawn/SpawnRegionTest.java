package com.larpsmp.moneyevent.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpawnRegionTest {

    @Test
    void undefinedRegionContainsNothing() {
        SpawnRegion region = SpawnRegion.undefined();
        assertFalse(region.isDefined());
        assertFalse(region.containsBlock("world", 0, 64, 0));
        assertFalse(region.containsLocation("world", 0.5, 64.0, 0.5));
    }

    @Test
    void withCornersNormalizesAndContainsBlocks() {
        SpawnRegion region = SpawnRegion.undefined().withCorners("world", 10, 70, 10, 0, 64, 0);
        assertTrue(region.isDefined());
        assertEquals("world", region.world());
        assertEquals(0, region.minX());
        assertEquals(64, region.minY());
        assertEquals(0, region.minZ());
        assertEquals(10, region.maxX());
        assertEquals(70, region.maxY());
        assertEquals(10, region.maxZ());

        assertTrue(region.containsBlock("world", 0, 64, 0));
        assertTrue(region.containsBlock("world", 10, 70, 10));
        assertTrue(region.containsBlock("WORLD", 5, 65, 5));
        assertFalse(region.containsBlock("world", 11, 65, 5));
        assertFalse(region.containsBlock("other", 5, 65, 5));
    }

    @Test
    void containsLocationUsesInclusiveMaxBlockFaces() {
        SpawnRegion region = new SpawnRegion("world", 0, 64, 0, 5, 70, 5);
        assertTrue(region.containsLocation("world", 0.0, 64.0, 0.0));
        assertTrue(region.containsLocation("world", 5.9, 70.9, 5.9));
        assertFalse(region.containsLocation("world", 6.0, 64.0, 0.0));
        assertFalse(region.containsLocation("world", 0.0, 71.0, 0.0));
    }
}
