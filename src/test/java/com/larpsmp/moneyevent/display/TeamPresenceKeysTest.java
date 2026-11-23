package com.larpsmp.moneyevent.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamPresenceKeysTest {

    @Test
    void membershipKeysSortByTeamThenTier() {
        Team first = team(0, "Blue");
        Team second = team(1, "Red");
        String blueT1 = TeamPresenceKeys.forMembership(first, PlayerTier.TIER_1);
        String blueT2 = TeamPresenceKeys.forMembership(first, PlayerTier.TIER_2);
        String redT1 = TeamPresenceKeys.forMembership(second, PlayerTier.TIER_1);
        String unassigned = TeamPresenceKeys.UNASSIGNED;

        assertEquals("t00_1", blueT1);
        assertEquals("t00_2", blueT2);
        assertEquals("t01_1", redT1);
        assertTrue(blueT1.compareTo(blueT2) < 0);
        assertTrue(blueT2.compareTo(redT1) < 0);
        assertTrue(redT1.compareTo(unassigned) < 0);
    }

    @Test
    void missingTierUsesZeroSlot() {
        assertEquals("t02_0", TeamPresenceKeys.forMembership(team(2, "Yellow"), null));
    }

    @Test
    void presenceTeamDetectionIgnoresSidebarSlots() {
        assertTrue(TeamPresenceKeys.isPresenceTeam("t00_1"));
        assertTrue(TeamPresenceKeys.isPresenceTeam(TeamPresenceKeys.UNASSIGNED));
        assertFalse(TeamPresenceKeys.isPresenceTeam("sb_tm_lbl"));
        assertFalse(TeamPresenceKeys.isPresenceTeam("sb_wl_amt"));
    }

    private static Team team(int sortOrder, String name) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new Team(UUID.randomUUID(), name.toLowerCase(), name, TeamColor.BLUE, sortOrder, null, now, now);
    }
}
