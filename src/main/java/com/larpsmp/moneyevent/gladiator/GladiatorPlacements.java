package com.larpsmp.moneyevent.gladiator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Placement math for Gladiators points. Ties award the worse place in the tied group.
 */
public final class GladiatorPlacements {

    private GladiatorPlacements() {
    }

    /**
     * @return teamId → zero-based place (0 = 1st). Higher points rank better.
     */
    public static Map<UUID, Integer> placesFromPoints(Map<UUID, Integer> pointsByTeam) {
        Objects.requireNonNull(pointsByTeam, "pointsByTeam");
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(pointsByTeam.entrySet());
        ranked.sort(Comparator
                .<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(entry -> entry.getKey().toString()));
        return worsePlaceTies(
                ranked.stream().map(Map.Entry::getKey).toList(),
                ranked.stream().map(Map.Entry::getValue).toList());
    }

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

    public static long prizeForPlace(int zeroBasedPlace, long[] prizes) {
        Objects.requireNonNull(prizes, "prizes");
        if (zeroBasedPlace < 0 || zeroBasedPlace >= prizes.length) {
            return 0L;
        }
        return Math.max(0L, prizes[zeroBasedPlace]);
    }
}
