package com.larpsmp.moneyevent.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlinePlayerAccess {
    List<OnlinePlayerIdentity> onlinePlayers();

    Optional<OnlinePlayerIdentity> findExact(String username);

    boolean isOnline(UUID playerId);

    void send(UUID playerId, String message, MessageKind kind);
}
