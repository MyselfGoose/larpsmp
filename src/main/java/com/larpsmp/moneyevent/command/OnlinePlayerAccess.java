package com.larpsmp.moneyevent.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public interface OnlinePlayerAccess {
    List<OnlinePlayerIdentity> onlinePlayers();

    Optional<OnlinePlayerIdentity> findExact(String username);

    boolean isOnline(UUID playerId);

    void send(UUID playerId, String message, MessageKind kind);

    /** Sends a pre-colored Adventure component (multi-style lines). */
    void send(UUID playerId, Component message);
}
