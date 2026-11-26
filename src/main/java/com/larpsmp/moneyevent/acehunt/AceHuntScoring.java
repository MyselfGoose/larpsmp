package com.larpsmp.moneyevent.acehunt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Ace Hunt placement math. Ties award the worse place in the tied group.
 */
public final class AceHuntScoring {

    private AceHuntScoring() {
    }

    /**
     * Survival points: living aces share the best remaining places (worse-place ties);
     * among the dead, last death ranks above earlier deaths.
     *
     * @param deathOrder ace team ids in death order (first death first)
     * @param livingAceTeams ace teams still alive
     */
    public static Map<UUID, Integer> survivalPoints(
            List<UUID> deathOrder,
            List<UUID> livingAceTeams
    ) {
        Objects.requireNonNull(deathOrder, "deathOrder");
        Objects.requireNonNull(livingAceTeams, "livingAceTeams");

        int total = deathOrder.size() + livingAceTeams.size();
        Map<UUID, Integer> points = new LinkedHashMap<>();

        if (!livingAceTeams.isEmpty()) {
            int worseLivingPlace = livingAceTeams.size() - 1;
            int livingPoints = AceHuntRules.pointsForPlacement(worseLivingPlace);
            for (UUID teamId : livingAceTeams) {
                points.put(teamId, livingPoints);
            }
        }

        // Dead: last death gets the next place after the living block.
        int nextPlace = livingAceTeams.size();
        for (int i = deathOrder.size() - 1; i >= 0; i--) {
            UUID teamId = deathOrder.get(i);
            points.put(teamId, AceHuntRules.pointsForPlacement(nextPlace));
            nextPlace++;
        }

        // Sanity: every known team should be present; unused slots ignored.
        if (points.size() != total && total > 0) {
            // Duplicates in inputs are ignored by later put; leave as-is.
        }
        return points;
    }

    /**
     * Hunt points by hunting team. Only kills by the assigned hunter team count.
     *
     * @param killOrder hunting team ids that scored a valid ace kill, chronological
     * @param allHuntingTeams every hunting team (for 0-point inclusion)
     */
    public static Map<UUID, Integer> huntPoints(
            List<UUID> killOrder,
            List<UUID> allHuntingTeams
    ) {
        Objects.requireNonNull(killOrder, "killOrder");
        Objects.requireNonNull(allHuntingTeams, "allHuntingTeams");

        Map<UUID, Integer> scored = new LinkedHashMap<>();
        int place = 0;
        for (UUID hunterTeamId : killOrder) {
            if (scored.containsKey(hunterTeamId)) {
                continue;
            }
            scored.put(hunterTeamId, AceHuntRules.pointsForPlacement(place));
            place++;
        }

        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (UUID teamId : allHuntingTeams) {
            result.put(teamId, scored.getOrDefault(teamId, AceHuntRules.POINTS_THIRD));
        }
        for (Map.Entry<UUID, Integer> entry : scored.entrySet()) {
            result.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Whether a kill awards hunt placement to the killer's team.
     */
    public static boolean isAssignedHunterKill(
            UUID victimAceTeamId,
            @Nullable UUID killerTeamId,
            Map<UUID, UUID> huntAssignments
    ) {
        Objects.requireNonNull(victimAceTeamId, "victimAceTeamId");
        Objects.requireNonNull(huntAssignments, "huntAssignments");
        if (killerTeamId == null) {
            return false;
        }
        UUID assignedTarget = huntAssignments.get(killerTeamId);
        return victimAceTeamId.equals(assignedTarget);
    }

    /**
     * Sum hunt + survival points per team.
     */
    public static Map<UUID, Integer> totalPoints(
            Map<UUID, Integer> hunt,
            Map<UUID, Integer> survival
    ) {
        Objects.requireNonNull(hunt, "hunt");
        Objects.requireNonNull(survival, "survival");
        Map<UUID, Integer> totals = new LinkedHashMap<>();
        for (UUID teamId : unionKeys(hunt, survival)) {
            totals.put(teamId, hunt.getOrDefault(teamId, 0) + survival.getOrDefault(teamId, 0));
        }
        return totals;
    }

    /**
     * Final standings: higher total is better. Ties receive the worse place among the tied group.
     *
     * @return teamId → zero-based place (0 = 1st)
     */
    public static Map<UUID, Integer> finalPlaces(Map<UUID, Integer> totals) {
        Objects.requireNonNull(totals, "totals");
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(totals.entrySet());
        ranked.sort(Comparator
                .<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(entry -> entry.getKey().toString()));
        return worsePlaceTies(
                ranked.stream().map(Map.Entry::getKey).toList(),
                ranked.stream().map(Map.Entry::getValue).toList());
    }

    /**
     * Given items sorted best-first with associated scores, assign zero-based places
     * using worse-place ties (two tied for best → both place 1).
     */
    public static Map<UUID, Integer> worsePlaceTies(List<UUID> orderBestFirst, List<Integer> scoresBestFirst) {
        Objects.requireNonNull(orderBestFirst, "orderBestFirst");
        Objects.requireNonNull(scoresBestFirst, "scoresBestFirst");
        if (orderBestFirst.size() != scoresBestFirst.size()) {
            throw new IllegalArgumentException("order and scores size mismatch");
        }
        Map<UUID, Integer> places = new LinkedHashMap<>();
        int i = 0;
        while (i < orderBestFirst.size()) {
            int score = scoresBestFirst.get(i);
            int j = i + 1;
            while (j < orderBestFirst.size() && Objects.equals(scoresBestFirst.get(j), score)) {
                j++;
            }
            int worsePlace = j - 1;
            for (int k = i; k < j; k++) {
                places.put(orderBestFirst.get(k), worsePlace);
            }
            i = j;
        }
        return places;
    }

    private static List<UUID> unionKeys(Map<UUID, Integer> a, Map<UUID, Integer> b) {
        Map<UUID, Boolean> seen = new HashMap<>();
        List<UUID> keys = new ArrayList<>();
        for (UUID key : a.keySet()) {
            if (seen.put(key, Boolean.TRUE) == null) {
                keys.add(key);
            }
        }
        for (UUID key : b.keySet()) {
            if (seen.put(key, Boolean.TRUE) == null) {
                keys.add(key);
            }
        }
        return keys;
    }
}
