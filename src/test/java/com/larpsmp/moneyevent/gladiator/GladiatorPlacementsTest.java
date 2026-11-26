package com.larpsmp.moneyevent.gladiator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GladiatorPlacementsTest {

    @Test
    void worsePlaceTiesAwardSharedLowerPlace() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, Integer> points = new LinkedHashMap<>();
        points.put(a, 3);
        points.put(b, 3);
        points.put(c, 1);
        Map<UUID, Integer> places = GladiatorPlacements.placesFromPoints(points);
        assertEquals(1, places.get(a));
        assertEquals(1, places.get(b));
        assertEquals(2, places.get(c));
        assertEquals(50L, GladiatorPlacements.prizeForPlace(1, new long[] {125, 50, 0}));
        assertEquals(0L, GladiatorPlacements.prizeForPlace(2, new long[] {125, 50, 0}));
    }
}
