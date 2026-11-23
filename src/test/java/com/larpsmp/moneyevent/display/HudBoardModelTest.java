package com.larpsmp.moneyevent.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HudBoardModelTest {

    @Test
    void personalBalancesLeadAndClassicLabelAmountLayout() {
        HudBoardModel model = HudBoardModel.build(
                true,
                List.of(new HudStandingsSnapshot.TopTeamRow(1, "Blue", 0x5555FF, 1200)),
                List.of(new HudStandingsSnapshot.TopPlayerRow(1, "alice", 400)),
                1200L,
                250L);

        List<String> lines = model.plainLines();
        assertEquals("Team Money", lines.get(0));
        assertEquals("$1,200", lines.get(1));
        assertEquals("··········", lines.get(2));
        assertEquals("Wallet", lines.get(3));
        assertEquals("$250", lines.get(4));
        assertEquals("··········", lines.get(5));
        assertEquals("TOP TEAMS", lines.get(6));
        assertEquals("1. Blue $1,200", lines.get(7));
        assertEquals("TOP PLAYERS", lines.get(10));
        assertEquals("1. alice $400", lines.get(11));
        assertFalse(lines.contains("5. —"));
        assertTrue(lines.size() <= HudBoardModel.MAX_LINES);
        assertEquals(lines.size(), model.lines().size());
    }

    @Test
    void hiddenTeamTotalsKeepsWalletOnTopAndOmitsTeamBoards() {
        HudBoardModel model = HudBoardModel.build(
                false,
                List.of(new HudStandingsSnapshot.TopTeamRow(1, "Blue", 0x5555FF, 9999)),
                List.of(
                        new HudStandingsSnapshot.TopPlayerRow(1, "alice", 500),
                        new HudStandingsSnapshot.TopPlayerRow(2, "bob", 400)),
                9999L,
                180L);

        List<String> lines = model.plainLines();
        assertEquals("Wallet", lines.get(0));
        assertEquals("$180", lines.get(1));
        assertFalse(lines.stream().anyMatch(line -> line.contains("TOP TEAMS")));
        assertFalse(lines.stream().anyMatch(line -> line.equals("Team Money")));
        assertTrue(lines.contains("TOP PLAYERS"));
        assertEquals("1. alice $500", lines.get(4));
    }

    @Test
    void omitsEmptyPlayerPlaceholdersAndAllowsLongerNames() {
        assertEquals("myselfgoose", HudTheme.truncate("myselfgoose"));
        assertEquals("verylongnam…", HudTheme.truncate("verylongnamehere"));
        HudBoardModel model = HudBoardModel.build(
                true,
                List.of(),
                List.of(new HudStandingsSnapshot.TopPlayerRow(1, "alice", 10)),
                null,
                10L);
        assertEquals(1, model.players().size());
        List<String> playerSection = model.plainLines().stream()
                .dropWhile(line -> !line.equals("TOP PLAYERS"))
                .skip(1)
                .toList();
        assertEquals(List.of("1. alice $10"), playerSection);
    }
}
