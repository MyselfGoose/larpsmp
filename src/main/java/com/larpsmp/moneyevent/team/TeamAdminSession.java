package com.larpsmp.moneyevent.team;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Per-player selected team context while navigating Teams Admin dialogs.
 */
public final class TeamAdminSession {

    private final Map<UUID, UUID> selectedTeamByPlayer = new ConcurrentHashMap<>();

    public void selectTeam(UUID playerId, UUID teamId) {
        selectedTeamByPlayer.put(playerId, teamId);
    }

    public Optional<UUID> selectedTeam(UUID playerId) {
        return Optional.ofNullable(selectedTeamByPlayer.get(playerId));
    }

    public void clear(UUID playerId) {
        selectedTeamByPlayer.remove(playerId);
    }

    public void clearTeam(@Nullable UUID teamId) {
        if (teamId == null) {
            return;
        }
        selectedTeamByPlayer.entrySet().removeIf(entry -> teamId.equals(entry.getValue()));
    }
}
