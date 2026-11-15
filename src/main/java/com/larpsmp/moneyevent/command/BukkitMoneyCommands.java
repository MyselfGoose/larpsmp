package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.auth.AccountAdminService;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BukkitMoneyCommands implements CommandExecutor, TabCompleter {
    private final MoneyCommandController controller;
    private final @Nullable AccountAdminService accountAdmins;

    public BukkitMoneyCommands(MoneyCommandController controller) {
        this(controller, null);
    }

    public BukkitMoneyCommands(MoneyCommandController controller, @Nullable AccountAdminService accountAdmins) {
        this.controller = controller;
        this.accountAdmins = accountAdmins;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments) {
        CommandSource source = CommandSources.of(sender, accountAdmins);
        switch (command.getName().toLowerCase(java.util.Locale.ROOT)) {
            case "balance" -> controller.balance(source, arguments);
            case "pay" -> controller.pay(source, arguments);
            case "top" -> controller.top(source, arguments);
            case "larp" -> controller.larp(source, arguments);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] arguments) {
        return controller.tabComplete(command.getName(), CommandSources.of(sender, accountAdmins), arguments);
    }
}
