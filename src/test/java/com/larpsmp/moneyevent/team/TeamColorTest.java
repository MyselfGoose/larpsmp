package com.larpsmp.moneyevent.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeamColorTest {

    @Test
    void fromKeyAcceptsAliases() {
        assertEquals(TeamColor.BLUE, TeamColor.fromKey("blue").orElseThrow());
        assertEquals(TeamColor.LIGHT_PURPLE, TeamColor.fromKey("light-purple").orElseThrow());
        assertEquals(TeamColor.LIGHT_PURPLE, TeamColor.fromKey("LIGHT_PURPLE").orElseThrow());
        assertTrue(TeamColor.fromKey("neon").isEmpty());
    }
}
