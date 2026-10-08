package com.larpsmp.moneyevent.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class BukkitOnlinePlayerAccess implements OnlinePlayerAccess {
    private final Server server;

    public BukkitOnlinePlayerAccess(Server server) {
        this.server = server;
    }

    @Override
    public List<OnlinePlayerIdentity> onlinePlayers() {
        return server.getOnlinePlayers().stream()
                .map(player -> new OnlinePlayerIdentity(player.getUniqueId(), player.getName()))
                .toList();
    }

    @Override
    public Optional<OnlinePlayerIdentity> findExact(String username) {
        Player player = server.getPlayerExact(username);
        return player == null
                ? Optional.empty()
                : Optional.of(new OnlinePlayerIdentity(player.getUniqueId(), player.getName()));
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

    static Component component(String message, MessageKind kind) {
        NamedTextColor color = switch (kind) {
            case INFO -> NamedTextColor.GOLD;
            case SUCCESS -> NamedTextColor.GREEN;
            case ERROR -> NamedTextColor.RED;
        };
        return Component.text(message, color);
    }
}
