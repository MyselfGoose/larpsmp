package com.larpsmp.moneyevent.acehunt;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computed hunt + survival + total points for the active session.
 */
public record AceHuntScoreboard(
        Map<UUID, Integer> huntPoints,
        Map<UUID, Integer> survivalPoints,
        Map<UUID, Integer> totals,
        Map<UUID, Integer> finalPlaces
) {
    public static AceHuntScoreboard from(AceHuntSession session) {
        List<UUID> huntingTeams = List.copyOf(session.huntAssignments().keySet());
        if (huntingTeams.isEmpty()) {
            huntingTeams = List.copyOf(session.acesByTeam().keySet());
        }
        Map<UUID, Integer> hunt = AceHuntScoring.huntPoints(session.scoredHuntTeams(), huntingTeams);
        Map<UUID, Integer> survival = AceHuntScoring.survivalPoints(
                session.deathOrderTeams(),
                session.livingAceTeams());
        Map<UUID, Integer> totals = AceHuntScoring.totalPoints(hunt, survival);
        Map<UUID, Integer> places = AceHuntScoring.finalPlaces(totals);
        return new AceHuntScoreboard(hunt, survival, totals, places);
    }
}
