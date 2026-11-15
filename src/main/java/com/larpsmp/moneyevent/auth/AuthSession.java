package com.larpsmp.moneyevent.auth;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pending authentication attempt held during the configuration phase.
 */
public final class AuthSession {

    private final UUID profileId;
    private final PlayerConfigurationConnection connection;
    private final CompletableFuture<AuthResult> result;
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AuthFlowContext flow = new AuthFlowContext();

    public AuthSession(UUID profileId, PlayerConfigurationConnection connection, CompletableFuture<AuthResult> result) {
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

    public CompletableFuture<AuthResult> result() {
        return result;
    }

    public AuthFlowContext flow() {
        return flow;
    }

    public boolean isPending() {
        return !result.isDone();
    }

    /**
     * Atomically claims the session for an in-flight login/signup attempt.
     *
     * @return true if this caller may proceed with processing
     */
    public boolean tryStartProcessing() {
        return isPending() && processing.compareAndSet(false, true);
    }

    public void finishProcessing() {
        processing.set(false);
    }
}
