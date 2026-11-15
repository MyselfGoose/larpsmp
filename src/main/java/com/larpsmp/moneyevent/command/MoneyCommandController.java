package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.auth.AccountAdminRepository;
import com.larpsmp.moneyevent.auth.AccountAdminService;
import com.larpsmp.moneyevent.auth.MoneyAdminAction;
import com.larpsmp.moneyevent.auth.MoneyAdminAuthorizer;
import com.larpsmp.moneyevent.display.BalanceDisplayControl;
import com.larpsmp.moneyevent.notification.PaymentNotificationStore;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamCommandController;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyAuditContext;
import com.larpsmp.moneyevent.wallet.MoneyFormats;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TransactionResult;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class MoneyCommandController {
    private final MoneyService money;
    private final PlayerLookup lookup;
    private final OnlinePlayerAccess onlinePlayers;
    private final PaymentNotificationStore notifications;
    private final Consumer<String> errorLogger;
    private final MoneyAdminAuthorizer authorizer;
    private final @Nullable TeamCommandController teamCommands;
    private final @Nullable TeamService teams;
    private final @Nullable BalanceDisplayControl balanceDisplay;
    private final @Nullable AccountAdminService accountAdmins;

    public MoneyCommandController(
            MoneyService money,
            PlayerLookup lookup,
            OnlinePlayerAccess onlinePlayers,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            MoneyAdminAuthorizer authorizer) {
        this(money, lookup, onlinePlayers, notifications, errorLogger, authorizer, null, null, null, null);
    }

    public MoneyCommandController(
            MoneyService money,
            PlayerLookup lookup,
            OnlinePlayerAccess onlinePlayers,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            MoneyAdminAuthorizer authorizer,
            @Nullable TeamCommandController teamCommands) {
        this(money, lookup, onlinePlayers, notifications, errorLogger, authorizer, teamCommands, null, null, null);
    }

    public MoneyCommandController(
            MoneyService money,
            PlayerLookup lookup,
            OnlinePlayerAccess onlinePlayers,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            MoneyAdminAuthorizer authorizer,
            @Nullable TeamCommandController teamCommands,
            @Nullable TeamService teams,
            @Nullable BalanceDisplayControl balanceDisplay) {
        this(money, lookup, onlinePlayers, notifications, errorLogger, authorizer, teamCommands, teams,
                balanceDisplay, null);
    }

    public MoneyCommandController(
            MoneyService money,
            PlayerLookup lookup,
            OnlinePlayerAccess onlinePlayers,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            MoneyAdminAuthorizer authorizer,
            @Nullable TeamCommandController teamCommands,
            @Nullable TeamService teams,
            @Nullable BalanceDisplayControl balanceDisplay,
            @Nullable AccountAdminService accountAdmins) {
        this.money = money;
        this.lookup = lookup;
        this.onlinePlayers = onlinePlayers;
        this.notifications = notifications;
        this.errorLogger = errorLogger;
        this.authorizer = authorizer;
        this.teamCommands = teamCommands;
        this.teams = teams;
        this.balanceDisplay = balanceDisplay;
        this.accountAdmins = accountAdmins;
    }

    public void balance(CommandSource source, String[] arguments) {
        if (arguments.length == 0) {
            Optional<UUID> playerId = source.playerId();
            if (playerId.isEmpty()) {
                source.send("Console must specify a player: /balance <player>", MessageKind.ERROR);
                return;
            }
            WalletAccount account = account(playerId.get(), source);
            if (account == null) {
                return;
            }
            source.send("Your balance: " + MoneyFormats.currency(account.balance()), MessageKind.INFO);
            return;
        }
        if (arguments.length != 1) {
            source.send("Usage: /balance [player]", MessageKind.ERROR);
            return;
        }
        PlayerLookupResult resolved = lookup(arguments[0], source);
        if (resolved == null) {
            return;
        }
        WalletAccount account = account(resolved.player().playerId(), source);
        if (account == null) {
            return;
        }
        source.send(account.lastKnownUsername() + "'s balance: "
                + MoneyFormats.currency(account.balance()), MessageKind.INFO);
    }

    public void pay(CommandSource source, String[] arguments) {
        if (source.playerId().isEmpty()) {
            source.send("Only players can use /pay.", MessageKind.ERROR);
            return;
        }
        if (arguments.length != 2) {
            source.send("Usage: /pay <player> <amount>", MessageKind.ERROR);
            return;
        }
        Long amount = parsePositiveAmount(arguments[1]);
        if (amount == null) {
            source.send("Amounts must be positive whole dollars.", MessageKind.ERROR);
            return;
        }
        UUID senderId = source.playerId().orElseThrow();
        WalletAccount sender = account(senderId, source);
        if (sender == null) {
            return;
        }
        PlayerLookupResult resolved = lookup(arguments[0], source);
        if (resolved == null) {
            return;
        }
        UUID recipientId = resolved.player().playerId();
        if (senderId.equals(recipientId)) {
            source.send("You cannot pay yourself.", MessageKind.ERROR);
            return;
        }
        WalletAccount recipient = account(recipientId, source);
        if (recipient == null) {
            return;
        }

        String reason = "Player payment from " + senderId + " to " + recipientId;
        TransactionResult result = money.transfer(senderId, recipientId, amount, reason, senderId);
        if (!result.successful()) {
            handlePaymentFailure(source, result);
            return;
        }

        long senderAfter = result.sourceBalanceAfter() == null ? 0L : result.sourceBalanceAfter();
        long recipientAfter = result.destinationBalanceAfter() == null ? 0L : result.destinationBalanceAfter();
        source.send("You sent " + recipient.lastKnownUsername() + " " + MoneyFormats.currency(amount)
                + ". Your balance is now " + MoneyFormats.currency(senderAfter) + ".",
                MessageKind.SUCCESS);
        String recipientMessage = source.name() + " sent you " + MoneyFormats.currency(amount)
                + ". Your balance is now " + MoneyFormats.currency(recipientAfter) + ".";
        if (onlinePlayers.isOnline(recipientId)) {
            onlinePlayers.send(recipientId, recipientMessage, MessageKind.SUCCESS);
            return;
        }
        try {
            notifications.enqueue(recipientId, result.transactionId());
            if (onlinePlayers.isOnline(recipientId)) {
                notifications.deliver(recipientId,
                        message -> onlinePlayers.send(recipientId, message, MessageKind.SUCCESS));
            }
        } catch (IOException exception) {
            errorLogger.accept("Payment " + result.transactionId()
                    + " succeeded but its offline notification could not be saved: " + exception.getMessage());
        }
    }

    public void top(CommandSource source, String[] arguments) {
        if (arguments.length != 1) {
            source.send("Usage: /top <teams|money>", MessageKind.ERROR);
            return;
        }
        String mode = arguments[0].toLowerCase(Locale.ROOT);
        try {
            if (mode.equals("teams")) {
                topTeams(source);
            } else if (mode.equals("money")) {
                topMoney(source);
            } else {
                source.send("Usage: /top <teams|money>", MessageKind.ERROR);
            }
        } catch (IOException exception) {
            errorLogger.accept("Could not load standings: " + exception.getMessage());
            source.send("Standings could not be loaded. Please contact an admin.", MessageKind.ERROR);
        }
    }

    public void larp(CommandSource source, String[] arguments) {
        if (arguments.length >= 1 && teamCommands != null && teamCommands.handle(source, arguments)) {
            return;
        }
        if (arguments.length < 1) {
            source.send("Usage: /larp <money|killmoney|killreward|visibility|op|deop|ops|teams|team|captain|tier> ...",
                    MessageKind.ERROR);
            return;
        }
        String head = arguments[0].toLowerCase(Locale.ROOT);
        switch (head) {
            case "money" -> larpMoney(source, arguments);
            case "killmoney" -> larpKillMoney(source, arguments);
            case "killreward" -> larpKillReward(source, arguments);
            case "visibility" -> larpVisibility(source, arguments);
            case "op" -> larpOp(source, arguments);
            case "deop" -> larpDeop(source, arguments);
            case "ops" -> larpOps(source);
            default -> source.send(
                    "Usage: /larp <money|killmoney|killreward|visibility|op|deop|ops|teams|team|captain|tier> ...",
                    MessageKind.ERROR);
        }
    }

    private void larpOp(CommandSource source, String[] arguments) {
        if (!requireAccountAdminService(source) || !allowedPluginAdminManage(source)) {
            return;
        }
        if (arguments.length < 2) {
            source.send("Usage: /larp op <larpsmp-username>", MessageKind.ERROR);
            return;
        }
        String username = arguments[1].trim();
        AccountAdminService.GrantResult result = accountAdmins.grant(username, source.name());
        switch (result) {
            case AccountAdminService.GrantResult.Granted granted -> {
                syncOnlineOp(granted.account().id());
                source.send("Granted plugin admin to account '" + granted.account().username() + "'.",
                        MessageKind.SUCCESS);
            }
            case AccountAdminService.GrantResult.Already already ->
                    source.send("Account '" + already.account().username() + "' is already a plugin admin.",
                            MessageKind.INFO);
            case AccountAdminService.GrantResult.NotFound ignored ->
                    source.send("No LarpSMP account named '" + username + "'.", MessageKind.ERROR);
            case AccountAdminService.GrantResult.Failed failed ->
                    source.send("Could not grant admin: " + failed.message(), MessageKind.ERROR);
            default -> source.send("Could not grant admin.", MessageKind.ERROR);
        }
    }

    private void larpDeop(CommandSource source, String[] arguments) {
        if (!requireAccountAdminService(source) || !allowedPluginAdminManage(source)) {
            return;
        }
        if (arguments.length < 2) {
            source.send("Usage: /larp deop <larpsmp-username>", MessageKind.ERROR);
            return;
        }
        String username = arguments[1].trim();
        AccountAdminService.GrantResult result = accountAdmins.revoke(username);
        switch (result) {
            case AccountAdminService.GrantResult.Revoked revoked -> {
                syncOnlineOp(revoked.account().id());
                source.send("Removed plugin admin from account '" + revoked.account().username() + "'.",
                        MessageKind.SUCCESS);
            }
            case AccountAdminService.GrantResult.NotAdmin notAdmin ->
                    source.send("Account '" + notAdmin.account().username() + "' is not a plugin admin.",
                            MessageKind.INFO);
            case AccountAdminService.GrantResult.NotFound ignored ->
                    source.send("No LarpSMP account named '" + username + "'.", MessageKind.ERROR);
            case AccountAdminService.GrantResult.Failed failed ->
                    source.send("Could not revoke admin: " + failed.message(), MessageKind.ERROR);
            default -> source.send("Could not revoke admin.", MessageKind.ERROR);
        }
    }

    private void larpOps(CommandSource source) {
        if (!requireAccountAdminService(source) || !allowedPluginAdminManage(source)) {
            return;
        }
        List<AccountAdminRepository.AdminRow> admins = accountAdmins.listAdmins();
        if (admins.isEmpty()) {
            source.send("No plugin admins yet. From console: /larp op <larpsmp-username>", MessageKind.INFO);
            return;
        }
        source.send("— Plugin admins (LarpSMP accounts) —", MessageKind.INFO);
        for (AccountAdminRepository.AdminRow row : admins) {
            source.send("- " + row.username(), MessageKind.INFO);
        }
    }

    private boolean requireAccountAdminService(CommandSource source) {
        if (accountAdmins != null) {
            return true;
        }
        source.send("Account admin service is not available.", MessageKind.ERROR);
        return false;
    }

    private boolean allowedPluginAdminManage(CommandSource source) {
        // Console always; in-game only existing account admins.
        if (source.isConsole() || source.isOperator()) {
            return true;
        }
        source.send("You are not authorized to perform that action.", MessageKind.ERROR);
        return false;
    }

    private void syncOnlineOp(UUID accountId) {
        if (accountAdmins == null) {
            return;
        }
        accountAdmins.syncBukkitOperatorIfOnline(accountId, uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            return player != null && player.isOnline() ? player : null;
        });
    }

    private void topTeams(CommandSource source) throws IOException {
        if (teams == null) {
            source.send("Teams are not available.", MessageKind.ERROR);
            return;
        }
        List<Team> teamList = new ArrayList<>(teams.listTeams());
        if (teamList.isEmpty()) {
            source.send("No teams registered.", MessageKind.INFO);
            return;
        }
        List<TeamStanding> standings = new ArrayList<>();
        for (Team team : teamList) {
            standings.add(new TeamStanding(team.displayName(), money.teamWealth(team.id())));
        }
        standings.sort(Comparator.comparingLong(TeamStanding::wealth).reversed()
                .thenComparing(TeamStanding::name, String.CASE_INSENSITIVE_ORDER));
        source.send("— Team wealth —", MessageKind.INFO);
        int rank = 1;
        for (TeamStanding standing : standings) {
            source.send(rank + ". " + standing.name() + " — " + MoneyFormats.currency(standing.wealth()),
                    MessageKind.INFO);
            rank++;
        }
    }

    private void topMoney(CommandSource source) throws IOException {
        List<WalletAccount> standings = money.competitorStandings();
        if (standings.isEmpty()) {
            source.send("No competitor balances yet.", MessageKind.INFO);
            return;
        }
        source.send("— Personal wealth —", MessageKind.INFO);
        int rank = 1;
        for (WalletAccount wallet : standings) {
            source.send(rank + ". " + wallet.lastKnownUsername() + " — "
                    + MoneyFormats.currency(wallet.balance()), MessageKind.INFO);
            rank++;
        }
    }

    private void larpMoney(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.send("Usage: /larp money <give|take|set> <player> <amount> <reason...>", MessageKind.ERROR);
            return;
        }
        String operation = arguments[1].toLowerCase(Locale.ROOT);
        switch (operation) {
            case "give" -> adminChange(source, arguments, MoneyAdminAction.MONEY_GIVE);
            case "take" -> adminChange(source, arguments, MoneyAdminAction.MONEY_TAKE);
            case "set" -> adminChange(source, arguments, MoneyAdminAction.MONEY_SET);
            default -> source.send("Unknown protected money operation.", MessageKind.ERROR);
        }
    }

    private void larpKillMoney(CommandSource source, String[] arguments) {
        if (!allowed(source, MoneyAdminAction.KILL_MONEY)) {
            return;
        }
        if (arguments.length < 2) {
            source.send("Usage: /larp killmoney <global|event|player|status> ...", MessageKind.ERROR);
            return;
        }
        String scope = arguments[1].toLowerCase(Locale.ROOT);
        String actor = source.name();
        String senderType = source.isConsole() ? "CONSOLE" : "PLAYER";
        try {
            switch (scope) {
                case "status" -> {
                    MoneyService.KillMoneyStatus status = money.killMoneyStatus();
                    source.send("Kill money global: " + (status.killMoneyGlobal() ? "on" : "off"),
                            MessageKind.INFO);
                    source.send("Team totals visible: " + (status.teamTotalsVisible() ? "yes" : "no"),
                            MessageKind.INFO);
                    source.send("Reward rotation index: " + status.rewardRotationIndex(), MessageKind.INFO);
                    for (Map.Entry<Integer, Long> entry : status.killPrices().entrySet()) {
                        source.send("Tier " + entry.getKey() + " death price: "
                                + MoneyFormats.currency(entry.getValue()), MessageKind.INFO);
                    }
                }
                case "global" -> {
                    if (arguments.length < 3) {
                        source.send("Usage: /larp killmoney global <on|off>", MessageKind.ERROR);
                        return;
                    }
                    Boolean enabled = parseOnOff(arguments[2]);
                    if (enabled == null) {
                        source.send("Use on or off.", MessageKind.ERROR);
                        return;
                    }
                    String reason = joinReason(arguments, 3);
                    money.setKillMoneyGlobal(enabled, actor, senderType, reason);
                    source.send("Kill money global is now " + (enabled ? "on" : "off") + ".",
                            MessageKind.SUCCESS);
                }
                case "event" -> handleKillMoneyEvent(source, arguments, actor, senderType);
                case "player" -> handleKillMoneyPlayer(source, arguments, actor, senderType);
                default -> source.send("Usage: /larp killmoney <global|event|player|status> ...",
                        MessageKind.ERROR);
            }
        } catch (IOException exception) {
            errorLogger.accept("Kill money command failed: " + exception.getMessage());
            source.send("Could not update kill money settings.", MessageKind.ERROR);
        }
    }

    private void handleKillMoneyEvent(
            CommandSource source, String[] arguments, String actor, String senderType) throws IOException {
        if (arguments.length < 4) {
            source.send("Usage: /larp killmoney event <eventId> <on|off|add|remove|clear> [player]",
                    MessageKind.ERROR);
            return;
        }
        String eventId = arguments[2];
        String op = arguments[3].toLowerCase(Locale.ROOT);
        switch (op) {
            case "on", "off" -> {
                boolean enabled = op.equals("on");
                String reason = joinReason(arguments, 4);
                money.setKillMoneyEvent(eventId, enabled, actor, senderType, reason);
                source.send("Kill money for event '" + eventId + "' is now " + op + ".", MessageKind.SUCCESS);
            }
            case "add" -> {
                if (arguments.length < 5) {
                    source.send("Usage: /larp killmoney event <eventId> add <player>", MessageKind.ERROR);
                    return;
                }
                PlayerLookupResult resolved = lookup(arguments[4], source);
                if (resolved == null) {
                    return;
                }
                WalletAccount wallet = account(resolved.player().playerId(), source);
                if (wallet == null) {
                    return;
                }
                money.addKillMoneyEventParticipant(eventId, wallet.accountId());
                source.send("Added " + wallet.lastKnownUsername() + " to event '" + eventId + "'.",
                        MessageKind.SUCCESS);
            }
            case "remove" -> {
                if (arguments.length < 5) {
                    source.send("Usage: /larp killmoney event <eventId> remove <player>", MessageKind.ERROR);
                    return;
                }
                PlayerLookupResult resolved = lookup(arguments[4], source);
                if (resolved == null) {
                    return;
                }
                WalletAccount wallet = account(resolved.player().playerId(), source);
                if (wallet == null) {
                    return;
                }
                money.removeKillMoneyEventParticipant(eventId, wallet.accountId());
                source.send("Removed " + wallet.lastKnownUsername() + " from event '" + eventId + "'.",
                        MessageKind.SUCCESS);
            }
            case "clear" -> {
                money.clearKillMoneyEventParticipants(eventId);
                source.send("Cleared participants for event '" + eventId + "'.", MessageKind.SUCCESS);
            }
            default -> source.send(
                    "Usage: /larp killmoney event <eventId> <on|off|add|remove|clear> [player]",
                    MessageKind.ERROR);
        }
    }

    private void handleKillMoneyPlayer(
            CommandSource source, String[] arguments, String actor, String senderType) throws IOException {
        if (arguments.length < 4) {
            source.send("Usage: /larp killmoney player <player> <on|off|clear>", MessageKind.ERROR);
            return;
        }
        PlayerLookupResult resolved = lookup(arguments[2], source);
        if (resolved == null) {
            return;
        }
        WalletAccount wallet = account(resolved.player().playerId(), source);
        if (wallet == null) {
            return;
        }
        String op = arguments[3].toLowerCase(Locale.ROOT);
        String reason = joinReason(arguments, 4);
        switch (op) {
            case "on", "off" -> {
                money.setPlayerKillMoneyOverride(wallet.accountId(), op.equals("on"), actor, senderType, reason);
                source.send("Kill money override for " + wallet.lastKnownUsername() + " is now " + op + ".",
                        MessageKind.SUCCESS);
            }
            case "clear" -> {
                money.clearPlayerKillMoneyOverride(wallet.accountId(), actor, senderType, reason);
                source.send("Cleared kill money override for " + wallet.lastKnownUsername() + ".",
                        MessageKind.SUCCESS);
            }
            default -> source.send("Usage: /larp killmoney player <player> <on|off|clear>", MessageKind.ERROR);
        }
    }

    private void larpKillReward(CommandSource source, String[] arguments) {
        if (!allowed(source, MoneyAdminAction.KILL_REWARD)) {
            return;
        }
        if (arguments.length < 4 || !arguments[1].equalsIgnoreCase("set")) {
            source.send("Usage: /larp killreward set <1-4> <amount>", MessageKind.ERROR);
            return;
        }
        Integer tier = parseTier(arguments[2]);
        if (tier == null) {
            source.send("Tier must be 1, 2, 3, or 4.", MessageKind.ERROR);
            return;
        }
        Long amount = parseAmount(arguments[3], true);
        if (amount == null) {
            source.send("Amount must be a non-negative whole dollar amount.", MessageKind.ERROR);
            return;
        }
        String reason = joinReason(arguments, 4);
        try {
            money.setKillReward(tier, amount, source.name(),
                    source.isConsole() ? "CONSOLE" : "PLAYER", reason);
            source.send("Tier " + tier + " death price set to " + MoneyFormats.currency(amount) + ".",
                    MessageKind.SUCCESS);
        } catch (IOException exception) {
            errorLogger.accept("Kill reward set failed: " + exception.getMessage());
            source.send("Could not update kill reward.", MessageKind.ERROR);
        }
    }

    private void larpVisibility(CommandSource source, String[] arguments) {
        if (!allowed(source, MoneyAdminAction.VISIBILITY)) {
            return;
        }
        if (arguments.length < 3 || !arguments[1].equalsIgnoreCase("teamtotals")) {
            source.send("Usage: /larp visibility teamtotals <show|hide>", MessageKind.ERROR);
            return;
        }
        String mode = arguments[2].toLowerCase(Locale.ROOT);
        boolean visible;
        if (mode.equals("show")) {
            visible = true;
        } else if (mode.equals("hide")) {
            visible = false;
        } else {
            source.send("Usage: /larp visibility teamtotals <show|hide>", MessageKind.ERROR);
            return;
        }
        String reason = joinReason(arguments, 3);
        try {
            money.setTeamTotalsVisible(visible, source.name(),
                    source.isConsole() ? "CONSOLE" : "PLAYER", reason);
            if (balanceDisplay != null) {
                balanceDisplay.refreshAll();
            }
            source.send("Team totals are now " + (visible ? "visible" : "hidden") + ".", MessageKind.SUCCESS);
        } catch (IOException exception) {
            errorLogger.accept("Visibility toggle failed: " + exception.getMessage());
            source.send("Could not update visibility.", MessageKind.ERROR);
        }
    }

    private void adminChange(CommandSource source, String[] arguments, MoneyAdminAction action) {
        if (!allowed(source, action)) {
            return;
        }
        if (arguments.length < 5) {
            source.send("A nonblank reason is required.", MessageKind.ERROR);
            return;
        }
        boolean set = action == MoneyAdminAction.MONEY_SET;
        Long amount = parseAmount(arguments[3], set);
        if (amount == null) {
            source.send(set ? "Balance must be a non-negative whole dollar amount."
                    : "Amounts must be positive whole dollars.", MessageKind.ERROR);
            return;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(arguments, 4, arguments.length)).trim();
        if (reason.isBlank()) {
            source.send("A nonblank reason is required.", MessageKind.ERROR);
            return;
        }
        PlayerLookupResult resolved = lookup(arguments[2], source);
        if (resolved == null) {
            return;
        }
        WalletAccount target = account(resolved.player().playerId(), source);
        if (target == null) {
            return;
        }
        MoneyAuditContext context = new MoneyAuditContext(
                action.name(), "/larp money " + arguments[1].toLowerCase(Locale.ROOT),
                source.name(), source.isConsole() ? "CONSOLE" : "PLAYER");
        TransactionResult result = switch (action) {
            case MONEY_GIVE -> money.add(target.playerId(), amount, reason, source.playerId().orElse(null), context);
            case MONEY_TAKE -> money.remove(target.playerId(), amount, reason, source.playerId().orElse(null), context);
            case MONEY_SET -> money.set(target.playerId(), amount, reason, source.playerId().orElse(null), context);
            default -> throw new IllegalStateException("Not a money mutation: " + action);
        };
        if (!result.successful()) {
            handleAdminFailure(source, result, set);
            return;
        }
        String verb = switch (action) {
            case MONEY_GIVE -> "Added " + MoneyFormats.currency(amount) + " to "
                    + target.lastKnownUsername() + ".";
            case MONEY_TAKE -> "Removed " + MoneyFormats.currency(amount) + " from "
                    + target.lastKnownUsername() + ".";
            case MONEY_SET -> "Set " + target.lastKnownUsername() + "'s balance.";
            default -> "Updated money.";
        };
        source.send(verb, MessageKind.SUCCESS);
        long before = result.sourceBalanceBefore() == null ? 0L : result.sourceBalanceBefore();
        long after = result.sourceBalanceAfter() == null ? 0L : result.sourceBalanceAfter();
        source.send("Balance: " + MoneyFormats.currency(before) + " → " + MoneyFormats.currency(after),
                MessageKind.INFO);
        source.send("Reason: " + reason, MessageKind.INFO);
    }

    public List<String> tabComplete(String command, CommandSource source, String[] arguments) {
        List<String> candidates;
        if (command.equalsIgnoreCase("balance") && arguments.length == 1) {
            candidates = lookup.suggestions();
        } else if (command.equalsIgnoreCase("pay") && arguments.length == 1) {
            candidates = lookup.suggestions().stream()
                    .filter(name -> !name.equalsIgnoreCase(source.name())).toList();
        } else if (command.equalsIgnoreCase("top")) {
            if (arguments.length == 1) {
                candidates = List.of("teams", "money");
            } else {
                return List.of();
            }
        } else if (command.equalsIgnoreCase("larp")) {
            if (arguments.length == 1) {
                candidates = new ArrayList<>();
                if (!authorizedActions(source).isEmpty()) {
                    candidates.add("money");
                }
                if (authorizer.isAllowed(source, MoneyAdminAction.KILL_MONEY)) {
                    candidates.add("killmoney");
                }
                if (authorizer.isAllowed(source, MoneyAdminAction.KILL_REWARD)) {
                    candidates.add("killreward");
                }
                if (authorizer.isAllowed(source, MoneyAdminAction.VISIBILITY)) {
                    candidates.add("visibility");
                }
                if (source.isConsole() || source.isOperator()) {
                    candidates.add("op");
                    candidates.add("deop");
                    candidates.add("ops");
                }
                if (teamCommands != null) {
                    candidates.addAll(teamCommands.tabComplete(source, arguments));
                }
            } else if (arguments[0].equalsIgnoreCase("op") || arguments[0].equalsIgnoreCase("deop")) {
                if (arguments.length == 2) {
                    candidates = lookup.suggestions();
                } else {
                    return List.of();
                }
            } else if (arguments[0].equalsIgnoreCase("money")) {
                if (arguments.length == 2) {
                    candidates = authorizedActions(source);
                } else if (arguments.length == 3
                        && adminAction(arguments[1]).map(action -> authorizer.isAllowed(source, action)).orElse(false)) {
                    candidates = lookup.suggestions();
                } else {
                    return List.of();
                }
            } else if (arguments[0].equalsIgnoreCase("killmoney")) {
                candidates = killMoneyTab(arguments);
            } else if (arguments[0].equalsIgnoreCase("killreward") && arguments.length == 2) {
                candidates = List.of("set");
            } else if (arguments[0].equalsIgnoreCase("visibility")) {
                if (arguments.length == 2) {
                    candidates = List.of("teamtotals");
                } else if (arguments.length == 3 && arguments[1].equalsIgnoreCase("teamtotals")) {
                    candidates = List.of("show", "hide");
                } else {
                    return List.of();
                }
            } else if (teamCommands != null) {
                return teamCommands.tabComplete(source, arguments);
            } else {
                return List.of();
            }
        } else {
            return List.of();
        }
        String prefix = arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private List<String> killMoneyTab(String[] arguments) {
        if (arguments.length == 2) {
            return List.of("global", "event", "player", "status");
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("global")) {
            return List.of("on", "off");
        }
        if (arguments.length == 4 && arguments[1].equalsIgnoreCase("event")) {
            return List.of("on", "off", "add", "remove", "clear");
        }
        if (arguments.length == 5 && arguments[1].equalsIgnoreCase("event")
                && (arguments[3].equalsIgnoreCase("add") || arguments[3].equalsIgnoreCase("remove"))) {
            return lookup.suggestions();
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("player")) {
            return lookup.suggestions();
        }
        if (arguments.length == 4 && arguments[1].equalsIgnoreCase("player")) {
            return List.of("on", "off", "clear");
        }
        return List.of();
    }

    private boolean allowed(CommandSource source, MoneyAdminAction action) {
        if (authorizer.isAllowed(source, action)) {
            return true;
        }
        source.send("You are not authorized to perform that action.", MessageKind.ERROR);
        return false;
    }

    private List<String> authorizedActions(CommandSource source) {
        List<String> values = new java.util.ArrayList<>();
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_GIVE)) {
            values.add("give");
        }
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_TAKE)) {
            values.add("take");
        }
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_SET)) {
            values.add("set");
        }
        return values;
    }

    private static Optional<MoneyAdminAction> adminAction(String operation) {
        return switch (operation.toLowerCase(Locale.ROOT)) {
            case "give" -> Optional.of(MoneyAdminAction.MONEY_GIVE);
            case "take" -> Optional.of(MoneyAdminAction.MONEY_TAKE);
            case "set" -> Optional.of(MoneyAdminAction.MONEY_SET);
            default -> Optional.empty();
        };
    }

    private void handleAdminFailure(CommandSource source, TransactionResult result, boolean set) {
        switch (result.status()) {
            case INSUFFICIENT_FUNDS -> source.send(
                    "That wallet only has "
                            + MoneyFormats.currency(result.sourceBalanceBefore() == null
                                    ? 0L : result.sourceBalanceBefore()) + ".",
                    MessageKind.ERROR);
            case INVALID_AMOUNT -> source.send(set ? "Balance must be a non-negative whole dollar amount."
                    : "Amounts must be positive whole dollars.", MessageKind.ERROR);
            case WALLET_NOT_FOUND -> source.send("That player does not have a registered wallet.", MessageKind.ERROR);
            case STORAGE_FAILURE -> {
                errorLogger.accept("Admin money transaction " + result.transactionId() + " failed to persist");
                source.send("The money change could not be saved. Please contact an admin.", MessageKind.ERROR);
            }
            default -> source.send("The money change could not be completed: " + result.failureReason(),
                    MessageKind.ERROR);
        }
    }

    private WalletAccount account(UUID playerId, CommandSource source) {
        try {
            Optional<WalletAccount> account = money.account(playerId);
            if (account.isEmpty()) {
                source.send(source.playerId().filter(playerId::equals).isPresent()
                        ? "You do not have a wallet. Sign up for an account first."
                        : "That player does not have a registered wallet.", MessageKind.ERROR);
                return null;
            }
            return account.orElseThrow();
        } catch (IOException exception) {
            errorLogger.accept("Could not load wallet " + playerId + ": " + exception.getMessage());
            source.send("The wallet could not be loaded. Please contact an admin.", MessageKind.ERROR);
            return null;
        }
    }

    private PlayerLookupResult lookup(String username, CommandSource source) {
        PlayerLookupResult result = lookup.resolve(username);
        switch (result.status()) {
            case FOUND -> {
                return result;
            }
            case AMBIGUOUS -> source.send("That name matches multiple registered wallets. Please contact an admin.",
                    MessageKind.ERROR);
            case STORAGE_FAILURE -> {
                errorLogger.accept("Storage failure while resolving username " + username);
                source.send("Player lookup could not be completed. Please contact an admin.", MessageKind.ERROR);
            }
            case NOT_FOUND -> source.send("That player does not have a registered wallet.", MessageKind.ERROR);
        }
        return null;
    }

    private void handlePaymentFailure(CommandSource source, TransactionResult result) {
        switch (result.status()) {
            case INSUFFICIENT_FUNDS -> source.send(
                    "You only have "
                            + MoneyFormats.currency(result.sourceBalanceBefore() == null
                                    ? 0L : result.sourceBalanceBefore()) + ".",
                    MessageKind.ERROR);
            case SAME_WALLET -> source.send("You cannot pay yourself.", MessageKind.ERROR);
            case WALLET_NOT_FOUND -> source.send("That player does not have a registered wallet.", MessageKind.ERROR);
            case INVALID_AMOUNT -> source.send("Amounts must be positive whole dollars.", MessageKind.ERROR);
            case STORAGE_FAILURE -> {
                errorLogger.accept("Payment transaction " + result.transactionId() + " failed to persist");
                source.send("The payment could not be saved. Please contact an admin.", MessageKind.ERROR);
            }
            default -> source.send("The payment could not be completed.", MessageKind.ERROR);
        }
    }

    private static Long parsePositiveAmount(String value) {
        if (!value.matches("[0-9]+")) {
            return null;
        }
        try {
            long amount = Long.parseLong(value);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long parseAmount(String value, boolean zeroAllowed) {
        if (!value.matches("[0-9]+")) {
            return null;
        }
        try {
            long amount = Long.parseLong(value);
            return amount > 0 || (zeroAllowed && amount == 0) ? amount : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static @Nullable Boolean parseOnOff(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes" -> true;
            case "off", "false", "no" -> false;
            default -> null;
        };
    }

    private static @Nullable Integer parseTier(String value) {
        if (!value.matches("[1-4]")) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private static String joinReason(String[] arguments, int fromIndex) {
        if (arguments.length <= fromIndex) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(arguments, fromIndex, arguments.length)).trim();
    }

    private record TeamStanding(String name, long wealth) {
    }
}
