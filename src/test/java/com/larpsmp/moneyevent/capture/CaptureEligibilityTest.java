package com.larpsmp.moneyevent.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class CaptureEligibilityTest {

    @Test
    void nullPlayerDoesNotCount() {
        assertFalse(CaptureEligibility.counts(null, uuid -> true, uuid -> true));
    }

    @Test
    void requiresOnlineAliveCompetitorWithTeam() {
        assertTrue(CaptureEligibility.countsState(
                true, false, GameMode.SURVIVAL, false, true, true));
        assertFalse(CaptureEligibility.countsState(
                false, false, GameMode.SURVIVAL, false, true, true));
        assertFalse(CaptureEligibility.countsState(
                true, true, GameMode.SURVIVAL, false, true, true));
        assertFalse(CaptureEligibility.countsState(
                true, false, GameMode.SPECTATOR, false, true, true));
        assertFalse(CaptureEligibility.countsState(
                true, false, GameMode.SURVIVAL, true, true, true));
        assertFalse(CaptureEligibility.countsState(
                true, false, GameMode.SURVIVAL, false, false, true));
        assertFalse(CaptureEligibility.countsState(
                true, false, GameMode.SURVIVAL, false, true, false));
    }

    @Test
    void adventureAndCreativeCompetitorsStillCount() {
        assertTrue(CaptureEligibility.countsState(
                true, false, GameMode.ADVENTURE, false, true, true));
        assertTrue(CaptureEligibility.countsState(
                true, false, GameMode.CREATIVE, false, true, true));
    }
}
