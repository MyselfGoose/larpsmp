package com.larpsmp.moneyevent.gladiator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Double round-robin pairings: each ordered team fights every other team twice.
 */
public final class GladiatorSchedule {

    private GladiatorSchedule() {
    }

    /**
     * Builds bout pairings for teams ordered by caller (typically sort_order then slug).
     * For three teams A,B,C: A-B, A-C, B-C, A-B, A-C, B-C.
     */
    public static List<ScheduledBout> doubleRoundRobin(List<UUID> orderedTeamIds) {
        Objects.requireNonNull(orderedTeamIds, "orderedTeamIds");
        if (orderedTeamIds.size() < 2) {
            return List.of();
        }
        List<UUID> teams = List.copyOf(orderedTeamIds);
        List<ScheduledBout> firstPass = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                firstPass.add(new ScheduledBout(teams.get(i), teams.get(j)));
            }
        }
        List<ScheduledBout> result = new ArrayList<>(firstPass.size() * 2);
        result.addAll(firstPass);
        result.addAll(firstPass);
        return List.copyOf(result);
    }

    public static List<UUID> sortTeamIds(List<TeamOrder> teams) {
        Objects.requireNonNull(teams, "teams");
        return teams.stream()
                .sorted(Comparator
                        .comparingInt(TeamOrder::sortOrder)
                        .thenComparing(TeamOrder::slug))
                .map(TeamOrder::teamId)
                .toList();
    }

    public record TeamOrder(UUID teamId, String slug, int sortOrder) {
        public TeamOrder {
            Objects.requireNonNull(teamId, "teamId");
            Objects.requireNonNull(slug, "slug");
        }
    }

    public record ScheduledBout(UUID teamAId, UUID teamBId) {
        public ScheduledBout {
            Objects.requireNonNull(teamAId, "teamAId");
            Objects.requireNonNull(teamBId, "teamBId");
            if (teamAId.equals(teamBId)) {
                throw new IllegalArgumentException("Teams in a bout must differ");
            }
        }
    }
}
