package com.larpsmp.moneyevent.combat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Last credited attack against a victim (Minecraft profile UUID keyed).
 */
public record AttackRecord(
        UUID attackerMinecraftUuid,
        AttributionCause cause,
        Instant at,
        @Nullable String detail
) {

    public AttackRecord {
        Objects.requireNonNull(attackerMinecraftUuid, "attackerMinecraftUuid");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(at, "at");
        detail = detail == null ? "" : detail;
    }
}
