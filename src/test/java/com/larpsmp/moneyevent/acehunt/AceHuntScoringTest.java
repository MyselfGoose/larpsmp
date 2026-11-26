package com.larpsmp.moneyevent.acehunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AceHuntScoringTest {

    private static final UUID BLUE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RED = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID YELLOW = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void huntPointsFollowKillOrder() {
        Map<UUID, Integer> points = AceHuntScoring.huntPoints(
                List.of(BLUE, RED, YELLOW),
                List.of(BLUE, RED, YELLOW));
        assertEquals(2, points.get(BLUE));
        assertEquals(1, points.get(RED));
        assertEquals(0, points.get(YELLOW));
    }

    @Test
    void teamsWithoutKillGetZeroHuntPoints() {
        Map<UUID, Integer> points = AceHuntScoring.huntPoints(
                List.of(BLUE),
                List.of(BLUE, RED, YELLOW));
        assertEquals(2, points.get(BLUE));
        assertEquals(0, points.get(RED));
        assertEquals(0, points.get(YELLOW));
    }

    @Test
    void survivalLastDeathGetsTwoPoints() {
        Map<UUID, Integer> points = AceHuntScoring.survivalPoints(
                List.of(BLUE, RED, YELLOW),
                List.of());
        assertEquals(0, points.get(BLUE));
        assertEquals(1, points.get(RED));
        assertEquals(2, points.get(YELLOW));
    }

    @Test
    void survivingAcesShareWorsePlace() {
        Map<UUID, Integer> points = AceHuntScoring.survivalPoints(
                List.of(BLUE),
                List.of(RED, YELLOW));
        assertEquals(0, points.get(BLUE));
        // Living share places 0-1 → worse place 1 → 1 point each
        assertEquals(1, points.get(RED));
        assertEquals(1, points.get(YELLOW));
    }

    @Test
    void allSurviveTiedForThirdPoints() {
        Map<UUID, Integer> points = AceHuntScoring.survivalPoints(
                List.of(),
                List.of(BLUE, RED, YELLOW));
        assertEquals(0, points.get(BLUE));
        assertEquals(0, points.get(RED));
        assertEquals(0, points.get(YELLOW));
    }

    @Test
    void perfectGameIsFourPoints() {
        Map<UUID, Integer> hunt = AceHuntScoring.huntPoints(
                List.of(BLUE, RED, YELLOW),
                List.of(BLUE, RED, YELLOW));
        Map<UUID, Integer> survival = AceHuntScoring.survivalPoints(
                List.of(RED, YELLOW),
                List.of(BLUE));
        Map<UUID, Integer> totals = AceHuntScoring.totalPoints(hunt, survival);
        assertEquals(4, totals.get(BLUE));
    }

    @Test
    void assignedHunterKillRequiresMatchingTarget() {
        Map<UUID, UUID> assignments = Map.of(
                BLUE, RED,
                RED, YELLOW,
                YELLOW, BLUE
        );
        assertTrue(AceHuntScoring.isAssignedHunterKill(RED, BLUE, assignments));
        assertFalse(AceHuntScoring.isAssignedHunterKill(RED, YELLOW, assignments));
        assertFalse(AceHuntScoring.isAssignedHunterKill(RED, null, assignments));
    }

    @Test
    void finalPlacesUseWorsePlaceTies() {
        Map<UUID, Integer> totals = Map.of(
                BLUE, 3,
                RED, 3,
                YELLOW, 1
        );
        Map<UUID, Integer> places = AceHuntScoring.finalPlaces(totals);
        // Blue and Red tied for best → both place 1 (worse of 0..1)
        assertEquals(1, places.get(BLUE));
        assertEquals(1, places.get(RED));
        assertEquals(2, places.get(YELLOW));
    }
}
