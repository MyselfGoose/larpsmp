package com.larpsmp.moneyevent.gladiator;

import java.time.Instant;
import java.util.Objects;

/**
 * Saved kit contents (inventory + armor + offhand).
 */
public record GladiatorKit(
        String kitId,
        byte[] inventoryData,
        byte[] armorData,
        byte[] offhandData,
        Instant updatedAt
) {
    public GladiatorKit {
        Objects.requireNonNull(kitId, "kitId");
        if (kitId.isBlank()) {
            throw new IllegalArgumentException("kitId cannot be blank");
        }
        inventoryData = inventoryData == null ? new byte[0] : inventoryData.clone();
        armorData = armorData == null ? new byte[0] : armorData.clone();
        offhandData = offhandData == null ? new byte[0] : offhandData.clone();
        Objects.requireNonNull(updatedAt, "updatedAt");
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
}
