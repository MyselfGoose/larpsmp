package com.larpsmp.moneyevent.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class BukkitMoneyCommands implements CommandExecutor, TabCompleter {
    private final MoneyCommandController controller;

    public BukkitMoneyCommands(MoneyCommandController controller) {
        this.controller = controller;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments) {
        CommandSource source = source(sender);
        switch (command.getName().toLowerCase(java.util.Locale.ROOT)) {
            case "balance" -> controller.balance(source, arguments);
            case "pay" -> controller.pay(source, arguments);
            case "larp" -> controller.larp(source, arguments);
            default -> { return false; }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] arguments) {
        return controller.tabComplete(command.getName(), source(sender), arguments);
    }

    private static CommandSource source(CommandSender sender) {
        return new CommandSource() {
            @Override
            public Optional<UUID> playerId() {
                return sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
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
                return sender.isOp();
            }

            @Override
            public void send(String message, MessageKind kind) {
                sender.sendMessage(BukkitOnlinePlayerAccess.component(message, kind));
            }
        };
    }
}
