package com.larpsmp.moneyevent.capture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Result of counting rostered players on the active point.
 */
public record CaptureAdvantage(
        @Nullable UUID leadingTeamId,
        int advantage,
        boolean tied,
        boolean empty,
        Map<UUID, Integer> headcounts
) {
    public CaptureAdvantage {
        headcounts = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(headcounts, "headcounts")));
        if (empty) {
            leadingTeamId = null;
            advantage = 0;
            tied = false;
        } else if (tied) {
            leadingTeamId = null;
            advantage = 0;
        } else {
            Objects.requireNonNull(leadingTeamId, "leadingTeamId");
            if (advantage < 1) {
                throw new IllegalArgumentException("advantage must be >= 1 when not tied/empty");
            }
        }
    }

    public static CaptureAdvantage fromHeadcounts(Map<UUID, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        Map<UUID, Integer> positive = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                positive.put(entry.getKey(), entry.getValue());
            }
        }
        if (positive.isEmpty()) {
            return new CaptureAdvantage(null, 0, false, true, Map.of());
        }

        int first = 0;
        int second = 0;
        UUID leader = null;
        int leadersAtFirst = 0;
        for (Map.Entry<UUID, Integer> entry : positive.entrySet()) {
            int count = entry.getValue();
            if (count > first) {
                second = first;
                first = count;
                leader = entry.getKey();
                leadersAtFirst = 1;
            } else if (count == first) {
                leadersAtFirst++;
                second = first;
            } else if (count > second) {
                second = count;
            }
        }
        if (leadersAtFirst > 1) {
            return new CaptureAdvantage(null, 0, true, false, positive);
        }
        int advantage = first - second;
        return new CaptureAdvantage(leader, advantage, false, false, positive);
    }
}
