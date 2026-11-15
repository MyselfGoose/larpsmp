package com.larpsmp.moneyevent.team;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Per-admin navigation state while using the Teams Admin dialog console.
 */
public final class TeamAdminSession {

    private final Map<UUID, SessionState> states = new ConcurrentHashMap<>();

    public void selectTeam(UUID playerId, UUID teamId) {
        SessionState current = states.getOrDefault(playerId, SessionState.empty());
        states.put(playerId, current.withTeam(teamId));
    }

    public void selectAccount(UUID playerId, UUID accountId) {
        SessionState current = states.getOrDefault(playerId, SessionState.empty());
        states.put(playerId, current.withAccount(accountId));
    }

    public Optional<UUID> selectedTeam(UUID playerId) {
        return Optional.ofNullable(states.get(playerId)).map(SessionState::teamId).filter(id -> id != null);
    }

    public Optional<UUID> selectedAccount(UUID playerId) {
        return Optional.ofNullable(states.get(playerId)).map(SessionState::accountId).filter(id -> id != null);
    }

    public void clear(UUID playerId) {
        states.remove(playerId);
    }

    public void clearTeam(@Nullable UUID teamId) {
        if (teamId == null) {
            return;
        }
        states.entrySet().removeIf(entry -> teamId.equals(entry.getValue().teamId()));
    }

    private record SessionState(@Nullable UUID teamId, @Nullable UUID accountId) {
        static SessionState empty() {
            return new SessionState(null, null);
        }

        SessionState withTeam(UUID teamId) {
            return new SessionState(teamId, accountId);
        }

        SessionState withAccount(UUID accountId) {
            return new SessionState(teamId, accountId);
        }
    }
}
