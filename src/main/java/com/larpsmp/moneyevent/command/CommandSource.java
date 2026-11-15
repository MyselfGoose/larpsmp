package com.larpsmp.moneyevent.command;

import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public interface CommandSource {
    Optional<UUID> playerId();

    String name();

    boolean isConsole();

    boolean isOperator();

    void send(String message, MessageKind kind);

    /**
     * Rich chat (e.g. multi-color standings). Default flattens to plain {@link MessageKind#INFO}.
     */
    default void send(Component message) {
        send(PlainTextComponentSerializer.plainText().serialize(message), MessageKind.INFO);
    }
}
