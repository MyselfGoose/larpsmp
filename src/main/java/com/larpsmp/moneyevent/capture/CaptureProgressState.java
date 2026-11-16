package com.larpsmp.moneyevent.capture;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable-in-place snapshot of progress for the active capture point.
 *
 * <p>Progress is stored as millis of "work" toward a nominal full bar of
 * {@link CaptureConfig#fullCaptureMillis(int) fullCaptureMillis(1)} so that
 * rate changes with advantage do not rewrite the stored fraction incorrectly.
 * Percent display uses {@code progressMillis / referenceFullMillis}.
 */
public final class CaptureProgressState {

    private @Nullable UUID owningTeamId;
    private long progressMillis;
    private final long referenceFullMillis;
    private final Set<Integer> announcedThresholds;

    public CaptureProgressState(
            @Nullable UUID owningTeamId,
            long progressMillis,
            long referenceFullMillis,
            Set<Integer> announcedThresholds) {
        if (referenceFullMillis < 1) {
            throw new IllegalArgumentException("referenceFullMillis must be >= 1");
        }
        this.owningTeamId = owningTeamId;
        this.progressMillis = Math.max(0L, Math.min(progressMillis, referenceFullMillis));
        this.referenceFullMillis = referenceFullMillis;
        this.announcedThresholds = new LinkedHashSet<>(
                Objects.requireNonNullElse(announcedThresholds, Set.of()));
        if (this.progressMillis == 0L) {
            this.owningTeamId = null;
        }
    }

    public static CaptureProgressState empty(long referenceFullMillis) {
        return new CaptureProgressState(null, 0L, referenceFullMillis, Set.of());
    }

    public @Nullable UUID owningTeamId() {
        return owningTeamId;
    }

    public long progressMillis() {
        return progressMillis;
    }

    public long referenceFullMillis() {
        return referenceFullMillis;
    }

    public Set<Integer> announcedThresholds() {
        return Collections.unmodifiableSet(announcedThresholds);
    }

    public double progressFraction() {
        return (double) progressMillis / (double) referenceFullMillis;
    }

    public int progressPercent() {
        return (int) Math.floor(progressFraction() * 100.0);
    }

    void setOwningTeamId(@Nullable UUID teamId) {
        this.owningTeamId = teamId;
    }

    void setProgressMillis(long value) {
        this.progressMillis = Math.max(0L, Math.min(value, referenceFullMillis));
        if (this.progressMillis == 0L) {
            this.owningTeamId = null;
        }
    }

    void clearAnnouncedThresholds() {
        announcedThresholds.clear();
    }

    boolean markAnnounced(int percent) {
        return announcedThresholds.add(percent);
    }

    public String announcedThresholdsCsv() {
        if (announcedThresholds.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (int value : announcedThresholds.stream().sorted().toList()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(value);
            first = false;
        }
        return builder.toString();
    }

    public static Set<Integer> parseAnnouncedThresholds(@Nullable String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<Integer> values = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return values;
    }
}
