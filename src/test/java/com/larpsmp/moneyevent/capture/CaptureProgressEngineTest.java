package com.larpsmp.moneyevent.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaptureProgressEngineTest {

    private static final UUID BLUE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RED = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private CaptureConfig config;
    private CaptureProgressEngine engine;
    private long fullOne;

    @BeforeEach
    void setUp() {
        config = CaptureConfig.defaults();
        engine = new CaptureProgressEngine(config);
        fullOne = config.fullCaptureMillis(1);
    }

    @Test
    void onePlayerBuildsAtBaseRateTowardCompletion() {
        CaptureProgressState state = CaptureProgressState.empty(fullOne);
        CaptureAdvantage advantage = CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1));

        // Half of 10 minutes
        engine.tick(state, advantage, fullOne / 2);
        assertEquals(BLUE, state.owningTeamId());
        assertEquals(50, state.progressPercent());

        CaptureProgressEngine.TickResult done = engine.tick(state, advantage, fullOne / 2 + 1);
        assertTrue(done.completed());
        assertEquals(100, state.progressPercent());
    }

    @Test
    void higherAdvantageBuildsFaster() {
        CaptureProgressState slow = CaptureProgressState.empty(fullOne);
        CaptureProgressState fast = CaptureProgressState.empty(fullOne);
        long dt = 60_000L;
        engine.tick(slow, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), dt);
        engine.tick(fast, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 4)), dt);
        assertTrue(fast.progressMillis() > slow.progressMillis());
    }

    @Test
    void tieFreezesProgress() {
        CaptureProgressState state = CaptureProgressState.empty(fullOne);
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), fullOne / 4);
        long before = state.progressMillis();
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 2, RED, 2)), 60_000L);
        assertEquals(before, state.progressMillis());
        assertEquals(BLUE, state.owningTeamId());
    }

    @Test
    void enemyMustNeutralizeBeforeBuilding() {
        CaptureProgressState state = CaptureProgressState.empty(fullOne);
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), fullOne / 2);
        assertEquals(50, state.progressPercent());
        assertEquals(BLUE, state.owningTeamId());

        // Red takes lead with advantage 1 — should neutralize, not jump to red ownership at 50%.
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(RED, 1)), fullOne / 4);
        assertEquals(BLUE, state.owningTeamId());
        assertEquals(25, state.progressPercent());

        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(RED, 1)), fullOne / 4);
        assertNull(state.owningTeamId());
        assertEquals(0, state.progressMillis());

        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(RED, 1)), fullOne / 10);
        assertEquals(RED, state.owningTeamId());
        assertTrue(state.progressMillis() > 0);
    }

    @Test
    void originalTeamResumesRemainingProgress() {
        CaptureProgressState state = CaptureProgressState.empty(fullOne);
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), fullOne * 60 / 100);
        assertEquals(60, state.progressPercent());

        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(RED, 1)), fullOne * 20 / 100);
        assertEquals(BLUE, state.owningTeamId());
        assertEquals(40, state.progressPercent());

        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), fullOne * 10 / 100);
        assertEquals(BLUE, state.owningTeamId());
        assertEquals(50, state.progressPercent());
    }

    @Test
    void emptyDecaysTwiceAsFastAsOnePlayerBuild() {
        CaptureProgressState state = CaptureProgressState.empty(fullOne);
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), fullOne);
        assertEquals(fullOne, state.progressMillis());

        // decayFull = fullOne / 2 → half duration to clear
        long decayFull = config.decayFullMillis();
        assertEquals(fullOne / 2, decayFull);
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of()), decayFull);
        assertEquals(0, state.progressMillis());
        assertNull(state.owningTeamId());
    }

    @Test
    void announcesThresholdsOnceAndResetsOnOwnershipChange() {
        CaptureProgressState state = CaptureProgressState.empty(fullOne);
        CaptureProgressEngine.TickResult first = engine.tick(
                state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), fullOne / 2);
        assertTrue(first.newlyAnnouncedThresholds().contains(25));
        assertTrue(first.newlyAnnouncedThresholds().contains(50));

        CaptureProgressEngine.TickResult again = engine.tick(
                state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), 1);
        assertTrue(again.newlyAnnouncedThresholds().isEmpty());

        // Neutralize fully then red builds past 25 → thresholds reset.
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(RED, 1)), fullOne);
        assertEquals(0, state.progressMillis());
        CaptureProgressEngine.TickResult red = engine.tick(
                state, CaptureAdvantage.fromHeadcounts(Map.of(RED, 1)), fullOne / 4);
        assertEquals(RED, state.owningTeamId());
        assertTrue(red.newlyAnnouncedThresholds().contains(25));
    }

    @Test
    void freezeOnZeroDelta() {
        CaptureProgressState state = CaptureProgressState.empty(fullOne);
        engine.tick(state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), 10_000L);
        long before = state.progressMillis();
        CaptureProgressEngine.TickResult result = engine.tick(
                state, CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 1)), 0L);
        assertFalse(result.completed());
        assertEquals(before, state.progressMillis());
    }
}
