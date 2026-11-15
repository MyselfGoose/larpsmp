package com.larpsmp.moneyevent.playerstate;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative online/pending sessions: one Minecraft UUID and one account id at a time.
 *
 * <p>Policy: a second concurrent login for the same account is rejected ({@link #tryBegin} returns empty).
 */
public final class AccountSessionManager {

    private final ConcurrentHashMap<UUID, AccountSession> byMinecraftUuid = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> accountToMinecraft = new ConcurrentHashMap<>();

    /**
     * Registers a pending session for {@code accountId} bound to {@code minecraftUuid}.
     *
     * @return the new session, or empty if the account already has a session
     */
    public Optional<AccountSession> tryBegin(UUID accountId, UUID minecraftUuid, String username) {
        UUID previous = accountToMinecraft.putIfAbsent(accountId, minecraftUuid);
        if (previous != null) {
            return Optional.empty();
        }

        AccountSession session = new AccountSession(accountId, username, minecraftUuid);
        AccountSession displaced = byMinecraftUuid.put(minecraftUuid, session);
        if (displaced != null && !displaced.accountId().equals(accountId)) {
            accountToMinecraft.remove(displaced.accountId(), displaced.minecraftUuid());
        }
        return Optional.of(session);
    }

    public Optional<AccountSession> findByMinecraftUuid(UUID minecraftUuid) {
        return Optional.ofNullable(byMinecraftUuid.get(minecraftUuid));
    }

    public Optional<AccountSession> findByAccountId(UUID accountId) {
        UUID minecraftUuid = accountToMinecraft.get(accountId);
        if (minecraftUuid == null) {
            return Optional.empty();
        }
        return findByMinecraftUuid(minecraftUuid);
    }

    public void activate(UUID minecraftUuid) {
        AccountSession session = byMinecraftUuid.get(minecraftUuid);
        if (session != null) {
            session.markActive();
        }
    }

    /**
     * Ends the session for this Minecraft profile if present.
     */
    public Optional<AccountSession> endByMinecraftUuid(UUID minecraftUuid) {
        AccountSession removed = byMinecraftUuid.remove(minecraftUuid);
        if (removed == null) {
            return Optional.empty();
        }
        accountToMinecraft.remove(removed.accountId(), minecraftUuid);
        return Optional.of(removed);
    }

    /**
     * Drops a pending session that never reached the world (timeout / disconnect during configure).
     */
    public void cancelPending(UUID minecraftUuid) {
        AccountSession session = byMinecraftUuid.get(minecraftUuid);
        if (session == null || session.isActive() || session.isApplied()) {
            return;
        }
        endByMinecraftUuid(minecraftUuid);
    }

    public Collection<AccountSession> allSessions() {
        return byMinecraftUuid.values();
    }

    public void clear() {
        byMinecraftUuid.clear();
        accountToMinecraft.clear();
    }
}
