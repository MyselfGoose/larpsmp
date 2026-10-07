package com.larpsmp.moneyevent.auth;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Pending authentication attempt held during the configuration phase.
 */
public final class AuthSession {

    private final UUID profileId;
    private final PlayerConfigurationConnection connection;
    private final CompletableFuture<Boolean> result;

    public AuthSession(UUID profileId, PlayerConfigurationConnection connection, CompletableFuture<Boolean> result) {
        this.profileId = profileId;
        this.connection = connection;
        this.result = result;
    }

    public UUID profileId() {
        return profileId;
    }

    public PlayerConfigurationConnection connection() {
        return connection;
    }

    public CompletableFuture<Boolean> result() {
        return result;
    }

    public boolean isPending() {
        return !result.isDone();
    }
}
