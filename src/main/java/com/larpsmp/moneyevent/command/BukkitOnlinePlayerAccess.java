package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class BukkitOnlinePlayerAccess implements OnlinePlayerAccess {
    private final Server server;
    private final @Nullable AccountSessionManager accountSessions;

    public BukkitOnlinePlayerAccess(Server server) {
        this(server, null);
    }

    public BukkitOnlinePlayerAccess(Server server, @Nullable AccountSessionManager accountSessions) {
        this.server = server;
        this.accountSessions = accountSessions;
    }

    @Override
    public List<OnlinePlayerIdentity> onlinePlayers() {
        return server.getOnlinePlayers().stream()
                .map(this::identityOf)
                .toList();
    }

    @Override
    public Optional<OnlinePlayerIdentity> findExact(String username) {
        for (OnlinePlayerIdentity identity : onlinePlayers()) {
            if (identity.username().equalsIgnoreCase(username)) {
                return Optional.of(identity);
            }
        }
        Player player = server.getPlayerExact(username);
        return player == null ? Optional.empty() : Optional.of(identityOf(player));
    }

    @Override
    public boolean isOnline(UUID playerId) {
        Player player = server.getPlayer(playerId);
        return player != null && player.isOnline();
    }

    @Override
    public void send(UUID playerId, String message, MessageKind kind) {
        Player player = server.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(component(message, kind));
        }
    }

    private OnlinePlayerIdentity identityOf(Player player) {
        String username = accountSessions == null
                ? player.getName()
                : accountSessions.findByMinecraftUuid(player.getUniqueId())
                        .map(session -> session.username())
                        .orElse(player.getName());
        return new OnlinePlayerIdentity(player.getUniqueId(), username);
    }

    static Component component(String message, MessageKind kind) {
        NamedTextColor color = switch (kind) {
            case INFO -> NamedTextColor.GOLD;
            case SUCCESS -> NamedTextColor.GREEN;
            case ERROR -> NamedTextColor.RED;
        };
        return Component.text(message, color);
    }
}
