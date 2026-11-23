package com.larpsmp.moneyevent.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HudBoardModelTest {

    @Test
    void visibleModeFitsUnderFifteenLinesAndPadsEmptyRanks() {
        HudBoardModel model = HudBoardModel.build(
                true,
                List.of(new HudStandingsSnapshot.TopTeamRow(1, "Blue", 0x5555FF, 1200)),
                List.of(new HudStandingsSnapshot.TopPlayerRow(1, "alice", 400)),
                1200L,
                250L);

        List<String> lines = model.plainLines();
        assertEquals(14, lines.size());
        assertEquals("TOP TEAMS", lines.get(0));
        assertEquals("1. Blue $1,200", lines.get(1));
        assertEquals("2. —", lines.get(2));
        assertEquals("3. —", lines.get(3));
        assertEquals("··········", lines.get(4));
        assertEquals("TOP PLAYERS", lines.get(5));
        assertEquals("1. alice $400", lines.get(6));
        assertEquals("5. —", lines.get(10));
        assertEquals("Team Money $1,200", lines.get(12));
        assertEquals("Wallet $250", lines.get(13));
        assertEquals(14, model.lines().size());
    }

    @Test
    void hiddenTeamTotalsOmitsTeamsAndTeamMoney() {
        HudBoardModel model = HudBoardModel.build(
                false,
                List.of(new HudStandingsSnapshot.TopTeamRow(1, "Blue", 0x5555FF, 9999)),
                List.of(
                        new HudStandingsSnapshot.TopPlayerRow(1, "alice", 500),
                        new HudStandingsSnapshot.TopPlayerRow(2, "bob", 400)),
                9999L,
                180L);

        List<String> lines = model.plainLines();
        assertEquals(8, lines.size());
        assertFalse(lines.stream().anyMatch(line -> line.contains("TOP TEAMS")));
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Team Money")));
        assertTrue(lines.contains("TOP PLAYERS"));
        assertEquals("1. alice $500", lines.get(1));
        assertEquals("Wallet $180", lines.getLast());
    }

    @Test
    void truncatesLongNamesForSidebarWidth() {
        assertEquals("VeryLongN…", HudTheme.truncate("VeryLongNameIndeed"));
        assertEquals("1. VeryLongN… $10",
                HudTheme.plainPlayerRow(1, "VeryLongNameIndeed", 10L));
    }
}
