package com.larpsmp.moneyevent.playerstate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory mapping from a connected Minecraft profile to an authenticated LarpSMP account.
 */
public final class AccountSession {

    private final UUID accountId;
    private final String username;
    private final UUID minecraftUuid;
    private volatile @Nullable AccountPlayerState preloadedState;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean applied = new AtomicBoolean(false);

    public AccountSession(UUID accountId, String username, UUID minecraftUuid) {
        this.accountId = accountId;
        this.username = username;
        this.minecraftUuid = minecraftUuid;
    }

    public UUID accountId() {
        return accountId;
    }

    public String username() {
        return username;
    }

    public UUID minecraftUuid() {
        return minecraftUuid;
    }

    public @Nullable AccountPlayerState preloadedState() {
        return preloadedState;
    }

    public void setPreloadedState(AccountPlayerState state) {
        this.preloadedState = state;
    }

    public boolean isActive() {
        return active.get();
    }

    public void markActive() {
        active.set(true);
    }

    public boolean isApplied() {
        return applied.get();
    }

    public boolean markApplied() {
        return applied.compareAndSet(false, true);
    }
}
