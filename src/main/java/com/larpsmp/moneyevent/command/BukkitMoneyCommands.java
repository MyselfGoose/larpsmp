package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.auction.BidCommandController;
import com.larpsmp.moneyevent.auth.AccountAdminService;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BukkitMoneyCommands implements CommandExecutor, TabCompleter {
    private final MoneyCommandController controller;
    private final @Nullable TeamInfoCommandController teamInfo;
    private final @Nullable PlayerHelpCommandController playerHelp;
    private final @Nullable AccountAdminService accountAdmins;
    private final @Nullable BidCommandController bidCommands;

    public BukkitMoneyCommands(MoneyCommandController controller) {
        this(controller, null, null, null, null);
    }

    public BukkitMoneyCommands(MoneyCommandController controller, @Nullable AccountAdminService accountAdmins) {
        this(controller, null, null, accountAdmins, null);
    }

    public BukkitMoneyCommands(
            MoneyCommandController controller,
            @Nullable TeamInfoCommandController teamInfo,
            @Nullable PlayerHelpCommandController playerHelp,
            @Nullable AccountAdminService accountAdmins) {
        this(controller, teamInfo, playerHelp, accountAdmins, null);
    }

    public BukkitMoneyCommands(
            MoneyCommandController controller,
            @Nullable TeamInfoCommandController teamInfo,
            @Nullable PlayerHelpCommandController playerHelp,
            @Nullable AccountAdminService accountAdmins,
            @Nullable BidCommandController bidCommands) {
        this.controller = controller;
        this.teamInfo = teamInfo;
        this.playerHelp = playerHelp;
        this.accountAdmins = accountAdmins;
        this.bidCommands = bidCommands;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments) {
        CommandSource source = CommandSources.of(sender, accountAdmins);
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> controller.balance(source, arguments);
            case "pay" -> controller.pay(source, arguments);
            case "top" -> controller.top(source, arguments);
            case "team" -> {
                if (teamInfo == null) {
                    source.send("Team info is not available.", MessageKind.ERROR);
                } else {
                    teamInfo.team(source, arguments);
                }
            }
            case "help" -> {
                if (playerHelp == null) {
                    source.send("Help is not available.", MessageKind.ERROR);
                } else {
                    playerHelp.help(source);
                }
            }
            case "bid" -> {
                if (bidCommands == null) {
                    source.send("Auctions are not available.", MessageKind.ERROR);
                } else {
                    bidCommands.bid(source, arguments);
                }
            }
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
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("team") && teamInfo != null) {
            return teamInfo.tabComplete(arguments);
        }
        if (name.equals("help")) {
            return List.of();
        }
        if (name.equals("bid")) {
            return bidCommands == null ? List.of() : bidCommands.tabComplete(arguments);
        }
        return controller.tabComplete(name, CommandSources.of(sender, accountAdmins), arguments);
    }
}
