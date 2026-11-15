package com.larpsmp.moneyevent.auth;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Binding between an account and a Minecraft profile UUID.
 */
public record AccountIdentity(
        UUID id,
        UUID accountId,
        UUID minecraftUuid,
        @Nullable String minecraftName,
        Instant boundAt,
        @Nullable Instant lastSeenAt
) {
}
