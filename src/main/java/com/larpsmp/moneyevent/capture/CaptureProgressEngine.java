package com.larpsmp.moneyevent.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Pure capture progress state machine (no Bukkit).
 *
 * <ul>
 *   <li>Advantage = lead − runner-up; ties freeze.</li>
 *   <li>Empty point decays toward zero.</li>
 *   <li>Enemy lead must neutralize to 0 before building their own bar.</li>
 *   <li>Original team retaking lead mid-neutralize resumes remaining progress.</li>
 * </ul>
 */
public final class CaptureProgressEngine {

    private final CaptureConfig config;

    public CaptureProgressEngine(CaptureConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public TickResult tick(CaptureProgressState state, CaptureAdvantage advantage, long deltaMillis) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(advantage, "advantage");
        if (deltaMillis <= 0) {
            return TickResult.unchanged(state);
        }

        UUID previousOwner = state.owningTeamId();
        int previousPercent = state.progressPercent();

        if (advantage.empty()) {
            applyDecay(state, deltaMillis);
        } else if (advantage.tied()) {
            // freeze — no progress change
        } else {
            UUID leader = Objects.requireNonNull(advantage.leadingTeamId(), "leadingTeamId");
            int adv = advantage.advantage();
            UUID owner = state.owningTeamId();
            if (owner == null || owner.equals(leader)) {
                applyBuild(state, leader, adv, deltaMillis);
            } else {
                applyNeutralize(state, adv, deltaMillis);
                // If neutralized to zero in this tick and time remains conceptually,
                // building starts next tick (do not inherit leftover dt into new owner).
            }
        }

        UUID newOwner = state.owningTeamId();
        boolean ownershipChanged = !Objects.equals(previousOwner, newOwner)
                && previousOwner != null
                && newOwner != null
                && !previousOwner.equals(newOwner);
        // Threshold announcements reset when a different team takes ownership of the bar.
        // Ownership "takes" means: previous owner lost the bar to another team building,
        // OR we transitioned from one owner to another after neutralize-to-zero then build.
        // Simpler rule from docs Suggestion: reset when a different team takes ownership.
        if (previousOwner != null && newOwner != null && !previousOwner.equals(newOwner)) {
            state.clearAnnouncedThresholds();
        } else if (previousOwner != null && newOwner == null) {
            // Cleared via neutralize/decay — clear thresholds so next builder starts fresh.
            state.clearAnnouncedThresholds();
        } else if (previousOwner == null && newOwner != null && previousPercent > 0) {
            // Should not happen; progress without owner is cleared to 0.
            state.clearAnnouncedThresholds();
        }

        List<Integer> newlyAnnounced = new ArrayList<>();
        if (newOwner != null && state.progressMillis() > 0) {
            int percent = state.progressPercent();
            for (int threshold : CaptureRules.THRESHOLD_PERCENTS) {
                if (percent >= threshold && state.markAnnounced(threshold)) {
                    newlyAnnounced.add(threshold);
                }
            }
        }

        boolean completed = newOwner != null && state.progressMillis() >= state.referenceFullMillis();
        return new TickResult(
                state,
                completed,
                newlyAnnounced,
                previousOwner,
                newOwner,
                ownershipChanged || (previousOwner != null && newOwner != null && !previousOwner.equals(newOwner))
        );
    }

    private void applyBuild(CaptureProgressState state, UUID teamId, int advantage, long deltaMillis) {
        long full = config.fullCaptureMillis(advantage);
        long rateReference = state.referenceFullMillis();
        // Convert wall dt into reference-progress millis: (dt / full) * referenceFull
        long deltaProgress = Math.round((deltaMillis * (double) rateReference) / (double) full);
        if (deltaProgress < 1 && deltaMillis > 0) {
            deltaProgress = 1;
        }
        if (state.owningTeamId() == null) {
            state.setOwningTeamId(teamId);
        }
        state.setProgressMillis(state.progressMillis() + deltaProgress);
    }

    private void applyNeutralize(CaptureProgressState state, int advantage, long deltaMillis) {
        long full = config.fullCaptureMillis(advantage);
        long rateReference = state.referenceFullMillis();
        long deltaProgress = Math.round((deltaMillis * (double) rateReference) / (double) full);
        if (deltaProgress < 1 && deltaMillis > 0) {
            deltaProgress = 1;
        }
        state.setProgressMillis(state.progressMillis() - deltaProgress);
    }

    private void applyDecay(CaptureProgressState state, long deltaMillis) {
        if (state.progressMillis() <= 0) {
            return;
        }
        long decayFull = config.decayFullMillis();
        long rateReference = state.referenceFullMillis();
        long deltaProgress = Math.round((deltaMillis * (double) rateReference) / (double) decayFull);
        if (deltaProgress < 1 && deltaMillis > 0) {
            deltaProgress = 1;
        }
        state.setProgressMillis(state.progressMillis() - deltaProgress);
    }

    public record TickResult(
            CaptureProgressState state,
            boolean completed,
            List<Integer> newlyAnnouncedThresholds,
            @Nullable UUID previousOwnerTeamId,
            @Nullable UUID owningTeamId,
            boolean ownershipChanged
    ) {
        public TickResult {
            newlyAnnouncedThresholds = List.copyOf(newlyAnnouncedThresholds);
        }

        static TickResult unchanged(CaptureProgressState state) {
            return new TickResult(
                    state,
                    false,
                    List.of(),
                    state.owningTeamId(),
                    state.owningTeamId(),
                    false
            );
        }
    }
}
