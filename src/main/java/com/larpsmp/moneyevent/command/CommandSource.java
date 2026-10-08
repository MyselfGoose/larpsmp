package com.larpsmp.moneyevent.command;

import java.util.Optional;
import java.util.UUID;

public interface CommandSource {
    Optional<UUID> playerId();

    String name();

    boolean isConsole();

    boolean isOperator();

    void send(String message, MessageKind kind);
}
