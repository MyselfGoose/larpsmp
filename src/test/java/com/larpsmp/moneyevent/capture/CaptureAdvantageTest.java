package com.larpsmp.moneyevent.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaptureAdvantageTest {

    private static final UUID BLUE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RED = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID YELLOW = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void docAdvantageTable() {
        assertAdvantage(Map.of(BLUE, 1), BLUE, 1, false);
        assertAdvantage(Map.of(BLUE, 2, RED, 1), BLUE, 1, false);
        assertAdvantage(Map.of(BLUE, 3, RED, 1), BLUE, 2, false);
        assertAdvantage(Map.of(BLUE, 4), BLUE, 4, false);
        assertAdvantage(Map.of(BLUE, 3, RED, 2, YELLOW, 2), BLUE, 1, false);

        CaptureAdvantage tie = CaptureAdvantage.fromHeadcounts(Map.of(BLUE, 2, RED, 2, YELLOW, 1));
        assertTrue(tie.tied());
        assertNull(tie.leadingTeamId());
        assertEquals(0, tie.advantage());
        assertFalse(tie.empty());
    }

    @Test
    void emptyWhenNoPlayers() {
        CaptureAdvantage empty = CaptureAdvantage.fromHeadcounts(Map.of());
        assertTrue(empty.empty());
        assertFalse(empty.tied());
        assertNull(empty.leadingTeamId());
    }

    private static void assertAdvantage(
            Map<UUID, Integer> counts,
            UUID expectedLeader,
            int expectedAdvantage,
            boolean expectedTie) {
        CaptureAdvantage result = CaptureAdvantage.fromHeadcounts(new LinkedHashMap<>(counts));
        assertEquals(expectedTie, result.tied());
        assertEquals(expectedLeader, result.leadingTeamId());
        assertEquals(expectedAdvantage, result.advantage());
        assertFalse(result.empty());
    }
}
