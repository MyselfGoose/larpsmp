package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.auth.AccountAdminService;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Builds {@link CommandSource} views. Privilege bits come from LarpSMP account tables, never from
 * {@link Player#isOp()}.
 */
public final class CommandSources {

    private CommandSources() {
    }

    public static CommandSource of(CommandSender sender, @Nullable AccountAdminService admins) {
        Predicate<UUID> pluginAdminCheck = minecraftUuid ->
                admins != null && admins.isAdminMinecraftUuid(minecraftUuid);
        Predicate<UUID> serverOpCheck = minecraftUuid ->
                admins != null && admins.isServerOperatorMinecraftUuid(minecraftUuid);
        return of(sender, pluginAdminCheck, serverOpCheck);
    }

    public static CommandSource of(CommandSender sender, Predicate<UUID> isAccountAdmin) {
        return of(sender, isAccountAdmin, uuid -> false);
    }

    public static CommandSource of(
            CommandSender sender,
            Predicate<UUID> isPluginAdmin,
            Predicate<UUID> isServerOperator) {
        return new CommandSource() {
            @Override
            public Optional<UUID> playerId() {
                return sender instanceof Player player
                        ? Optional.of(player.getUniqueId())
                        : Optional.empty();
            }

            @Override
            public String name() {
                return sender.getName();
            }

            @Override
            public boolean isConsole() {
                return !(sender instanceof Player);
            }

            @Override
            public boolean isOperator() {
                return isPluginAdmin() || isServerOperator();
            }

            @Override
            public boolean isPluginAdmin() {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                return isPluginAdmin.test(player.getUniqueId());
            }

            @Override
            public boolean isServerOperator() {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                return isServerOperator.test(player.getUniqueId());
            }

            @Override
            public void send(String message, MessageKind kind) {
                sender.sendMessage(BukkitOnlinePlayerAccess.component(message, kind));
            }

            @Override
            public void send(Component message) {
                sender.sendMessage(message);
            }
        };
    }

    public static CommandSource ofPlayer(Player player, AccountAdminService admins) {
        return of(player, admins);
    }
}
