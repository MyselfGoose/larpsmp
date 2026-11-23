package com.larpsmp.moneyevent.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class HudBoardModelTest {

    @Test
    void personalBalancesLeadWithDividerBetweenRankingBoards() {
        HudBoardModel model = HudBoardModel.build(
                true,
                List.of(new HudStandingsSnapshot.TopTeamRow(1, "Blue", 0x5555FF, 1200)),
                List.of(new HudStandingsSnapshot.TopPlayerRow(1, "alice", 400)),
                1200L,
                250L);

        assertEquals("Team Money",
                PlainTextComponentSerializer.plainText().serialize(model.title()));

        List<String> lines = model.plainLines();
        assertEquals("Team Money", lines.get(0));
        assertEquals("$1,200", lines.get(1));
        assertEquals("Wallet", lines.get(3));
        assertEquals("$250", lines.get(4));
        assertEquals("TOP TEAMS", lines.get(6));
        int playersAt = lines.indexOf("TOP PLAYERS");
        assertTrue(playersAt > 0);
        assertEquals("··········", lines.get(playersAt - 1));
        assertTrue(lines.size() <= HudBoardModel.MAX_LINES + 1); // +1 title in plainLines
        assertTrue(model.lines().size() <= HudBoardModel.MAX_LINES);
    }

    @Test
    void hiddenTeamTotalsUsesWalletTitleAndOmitsTeamBoards() {
        HudBoardModel model = HudBoardModel.build(
                false,
                List.of(new HudStandingsSnapshot.TopTeamRow(1, "Blue", 0x5555FF, 9999)),
                List.of(
                        new HudStandingsSnapshot.TopPlayerRow(1, "alice", 500),
                        new HudStandingsSnapshot.TopPlayerRow(2, "bob", 400)),
                9999L,
                180L);

        assertEquals("Wallet",
                PlainTextComponentSerializer.plainText().serialize(model.title()));
        List<String> lines = model.plainLines();
        assertEquals("Wallet", lines.get(0));
        assertFalse(lines.stream().anyMatch(line -> line.contains("TOP TEAMS")));
        assertFalse(lines.stream().anyMatch(line -> line.equals("Team Money")));
        assertTrue(lines.contains("TOP PLAYERS"));
    }

    @Test
    void headingsShareOneStyleAndNamesAreNotTeamColored() {
        assertEquals(HudTheme.HEADING, HudTheme.teamMoneyLabel().color());
        assertEquals(HudTheme.HEADING, HudTheme.walletLabel().color());
        assertEquals(HudTheme.HEADING, HudTheme.teamsHeader().color());
        assertEquals(HudTheme.HEADING, HudTheme.playersHeader().color());
        assertEquals(HudTheme.BODY,
                HudTheme.teamNamePrefix(1, "Blue").children().get(1).color());
        assertEquals(HudTheme.BODY,
                HudTheme.playerNamePrefix(1, "alice").children().get(1).color());
    }
}
