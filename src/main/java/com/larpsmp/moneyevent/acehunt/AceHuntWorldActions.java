package com.larpsmp.moneyevent.acehunt;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Bukkit-side side effects invoked by {@link AceHuntService}. Tests may no-op these.
 */
public interface AceHuntWorldActions {

    AceHuntWorldActions NOOP = new AceHuntWorldActions() {
    };

    default void teleportToSpawn(UUID accountId, AceHuntSpawnPoint spawn) {
    }

    default void teleportToSnapshot(UUID accountId, AceHuntLocationSnapshot snapshot) {
    }

    default void applyTerritoryBorder(UUID accountId, AceHuntTerritory territory) {
    }

    default void clearBorder(UUID accountId) {
    }

    default void setSpectator(UUID accountId) {
    }

    default void setSurvival(UUID accountId) {
    }

    default void correctOfflineGameMode(UUID accountId, String gameMode) {
    }

    default @Nullable AceHuntLocationSnapshot captureLocation(
            UUID accountId,
            AceHuntLocationSnapshot.ParticipantRole role
    ) {
        return null;
    }

    default void broadcast(String message) {
    }

    default void send(UUID accountId, String message) {
    }

    default void damage(UUID accountId, double amount) {
    }
}
