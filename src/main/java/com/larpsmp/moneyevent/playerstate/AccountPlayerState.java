package com.larpsmp.moneyevent.playerstate;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Durable Minecraft body snapshot keyed by LarpSMP account id.
 */
public record AccountPlayerState(
        UUID accountId,
        int schemaVersion,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        byte[] inventoryData,
        byte[] armorData,
        byte[] offhandData,
        byte[] enderChestData,
        double health,
        int foodLevel,
        float saturation,
        float exhaustion,
        int level,
        int totalExperience,
        float exp,
        String gameMode,
        byte[] potionEffectsData,
        @Nullable Instant updatedAt
) {
    public AccountPlayerState {
        inventoryData = inventoryData == null ? new byte[0] : inventoryData.clone();
        armorData = armorData == null ? new byte[0] : armorData.clone();
        offhandData = offhandData == null ? new byte[0] : offhandData.clone();
        enderChestData = enderChestData == null ? new byte[0] : enderChestData.clone();
        potionEffectsData = potionEffectsData == null ? new byte[0] : potionEffectsData.clone();
    }

    @Override
    public byte[] inventoryData() {
        return inventoryData.clone();
    }

    @Override
    public byte[] armorData() {
        return armorData.clone();
    }

    @Override
    public byte[] offhandData() {
        return offhandData.clone();
    }

    @Override
    public byte[] enderChestData() {
        return enderChestData.clone();
    }

    @Override
    public byte[] potionEffectsData() {
        return potionEffectsData.clone();
    }
}
