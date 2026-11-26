package com.larpsmp.moneyevent.gladiator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GladiatorScheduleTest {

    @Test
    void threeTeamsProduceSixDoubleRoundRobinBouts() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<GladiatorSchedule.ScheduledBout> bouts = GladiatorSchedule.doubleRoundRobin(List.of(a, b, c));
        assertEquals(6, bouts.size());
        assertEquals(a, bouts.get(0).teamAId());
        assertEquals(b, bouts.get(0).teamBId());
        assertEquals(a, bouts.get(3).teamAId());
        assertEquals(b, bouts.get(3).teamBId());
    }

    @Test
    void eachTeamAppearsFourTimesWithThreeTeams() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<GladiatorSchedule.ScheduledBout> bouts = GladiatorSchedule.doubleRoundRobin(List.of(a, b, c));
        assertEquals(4, countAppearances(bouts, a));
        assertEquals(4, countAppearances(bouts, b));
        assertEquals(4, countAppearances(bouts, c));
    }

    @Test
    void singleTeamProducesNoBouts() {
        assertTrue(GladiatorSchedule.doubleRoundRobin(List.of(UUID.randomUUID())).isEmpty());
    }

    private static int countAppearances(List<GladiatorSchedule.ScheduledBout> bouts, UUID teamId) {
        int count = 0;
        for (GladiatorSchedule.ScheduledBout bout : bouts) {
            if (bout.teamAId().equals(teamId) || bout.teamBId().equals(teamId)) {
                count++;
            }
        }
        return count;
    }
}
