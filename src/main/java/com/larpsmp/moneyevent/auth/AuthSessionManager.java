package com.larpsmp.moneyevent.auth;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tracks players waiting in the configuration-phase auth gate.
 */
public final class AuthSessionManager {

    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<>();

    public AuthSession begin(PlayerConfigurationConnection connection, UUID profileId, int timeoutSeconds) {
        CompletableFuture<AuthResult> result = new CompletableFuture<>();
        result.completeOnTimeout(AuthResult.REJECTED, timeoutSeconds, TimeUnit.SECONDS);

        AuthSession session = new AuthSession(profileId, connection, result);
        AuthSession previous = sessions.put(profileId, session);
        if (previous != null && previous.isPending()) {
            previous.result().complete(AuthResult.REJECTED);
        }
        return session;
    }

    public Optional<AuthSession> get(UUID profileId) {
        return Optional.ofNullable(sessions.get(profileId));
    }

    public boolean complete(UUID profileId, AuthResult outcome) {
        AuthSession session = sessions.get(profileId);
        if (session == null) {
            return false;
        }
        return session.result().complete(outcome);
    }

    public void remove(UUID profileId) {
        sessions.remove(profileId);
    }

    public void cancel(UUID profileId) {
        AuthSession session = sessions.remove(profileId);
        if (session != null && session.isPending()) {
            session.result().complete(AuthResult.REJECTED);
        }
    }

    public void cancelAll() {
        for (UUID profileId : sessions.keySet()) {
            cancel(profileId);
        }
        sessions.clear();
    }
}
