package com.larpsmp.moneyevent.command;

import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public interface CommandSource {
    Optional<UUID> playerId();

    String name();

    boolean isConsole();

    /**
     * True when the sender holds plugin admin and/or server operator.
     * Prefer {@link #isPluginAdmin()} / {@link #isServerOperator()} for new checks.
     */
    boolean isOperator();

    /**
     * True when the account is a LarpSMP plugin admin (or a server operator, which implies it).
     */
    default boolean isPluginAdmin() {
        return isOperator();
    }

    /**
     * True when the account holds full server operator (Bukkit op).
     */
    default boolean isServerOperator() {
        return false;
    }

    void send(String message, MessageKind kind);

    /**
     * Rich chat (e.g. multi-color standings). Default flattens to plain {@link MessageKind#INFO}.
     */
    default void send(Component message) {
        send(PlainTextComponentSerializer.plainText().serialize(message), MessageKind.INFO);
    }
}
