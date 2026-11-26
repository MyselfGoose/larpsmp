package com.larpsmp.moneyevent.gladiator;

import java.util.Objects;
import java.util.UUID;

/**
 * Frozen personal body for the duration of a Gladiators session.
 */
public record GladiatorBodySnapshot(
        UUID accountId,
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
        byte[] potionEffectsData
) {
    public GladiatorBodySnapshot {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(worldName, "worldName");
        inventoryData = inventoryData == null ? new byte[0] : inventoryData.clone();
        armorData = armorData == null ? new byte[0] : armorData.clone();
        offhandData = offhandData == null ? new byte[0] : offhandData.clone();
        enderChestData = enderChestData == null ? new byte[0] : enderChestData.clone();
        potionEffectsData = potionEffectsData == null ? new byte[0] : potionEffectsData.clone();
        Objects.requireNonNull(gameMode, "gameMode");
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
