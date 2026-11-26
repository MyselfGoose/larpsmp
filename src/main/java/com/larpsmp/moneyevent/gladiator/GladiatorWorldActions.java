package com.larpsmp.moneyevent.gladiator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Bukkit-side side effects invoked by {@link GladiatorService}. Tests may no-op these.
 */
public interface GladiatorWorldActions {

    GladiatorWorldActions NOOP = new GladiatorWorldActions() {
    };

    default void teleport(UUID accountId, GladiatorSpawnPoint spawn) {
    }

    default void applyKit(UUID accountId, GladiatorKit kit) {
    }

    default void clearInventory(UUID accountId) {
    }

    default void healFully(UUID accountId) {
    }

    default void setAdventure(UUID accountId) {
    }

    default void setSurvival(UUID accountId) {
    }

    default Optional<GladiatorBodySnapshot> captureBody(UUID accountId) {
        return Optional.empty();
    }

    default void restoreBody(GladiatorBodySnapshot snapshot) {
    }

    default Optional<GladiatorKit> captureKitFromPlayer(UUID accountId, String kitId) {
        return Optional.empty();
    }

    /**
     * Fills the region with barrier blocks and returns prior-block undo rows.
     */
    default List<GladiatorBarrierBlock> fillBarriers(GladiatorCuboid region) {
        return List.of();
    }

    default int clearBarriers(List<GladiatorBarrierBlock> blocks) {
        return 0;
    }

    default boolean isOnline(UUID accountId) {
        return false;
    }

    default void broadcast(String message) {
    }

    default void send(UUID accountId, String message) {
    }

    default @Nullable String teamDisplayName(UUID teamId) {
        return null;
    }

    default @Nullable String username(UUID accountId) {
        return null;
    }
}
