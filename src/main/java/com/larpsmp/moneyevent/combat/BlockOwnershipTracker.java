package com.larpsmp.moneyevent.combat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who last placed or configured trap / hazard blocks.
 * Ownership is in-memory only; after restart unknown ownership is environmental (no money).
 */
public final class BlockOwnershipTracker {

    private final ConcurrentHashMap<BlockKey, UUID> owners = new ConcurrentHashMap<>();

    public void setOwner(BlockKey key, UUID placerMinecraftUuid) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placerMinecraftUuid, "placerMinecraftUuid");
        owners.put(key, placerMinecraftUuid);
    }

    public Optional<UUID> ownerOf(BlockKey key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(owners.get(key));
    }

    public void clear(BlockKey key) {
        if (key != null) {
            owners.remove(key);
        }
    }

    public void clearAll() {
        owners.clear();
    }

    public int size() {
        return owners.size();
    }
}
