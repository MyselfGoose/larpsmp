package com.larpsmp.moneyevent.team;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of a team admin mutation. Soft roster-rule breaches succeed with warnings.
 */
public record TeamMutationResult(
        boolean success,
        @Nullable String error,
        List<String> messages,
        List<String> warnings,
        @Nullable Team team
) {
    public static TeamMutationResult ok(String message, @Nullable Team team, List<String> warnings) {
        return new TeamMutationResult(true, null, List.of(message), List.copyOf(warnings), team);
    }

    public static TeamMutationResult ok(String message, @Nullable Team team) {
        return ok(message, team, List.of());
    }

    public static TeamMutationResult fail(String error) {
        return new TeamMutationResult(false, error, List.of(), List.of(), null);
    }

    public List<String> allFeedback() {
        if (!success) {
            return error == null ? List.of() : List.of(error);
        }
        java.util.ArrayList<String> feedback = new java.util.ArrayList<>(messages);
        feedback.addAll(warnings);
        return List.copyOf(feedback);
    }
}
