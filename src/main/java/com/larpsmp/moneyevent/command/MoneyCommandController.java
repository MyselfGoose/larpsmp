package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.auth.MoneyAdminAction;
import com.larpsmp.moneyevent.auth.MoneyAdminAuthorizer;
import com.larpsmp.moneyevent.display.BalanceDisplayControl;
import com.larpsmp.moneyevent.notification.PaymentNotificationStore;
import com.larpsmp.moneyevent.wallet.AccountCreationResult;
import com.larpsmp.moneyevent.wallet.MoneyAuditContext;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TransactionResult;
import com.larpsmp.moneyevent.wallet.TransactionStatus;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class MoneyCommandController {
    private final MoneyService money;
    private final PlayerLookup lookup;
    private final OnlinePlayerAccess onlinePlayers;
    private final PaymentNotificationStore notifications;
    private final Consumer<String> errorLogger;
    private final MoneyAdminAuthorizer authorizer;
    private final BalanceDisplayControl display;

    public MoneyCommandController(
            MoneyService money,
            PlayerLookup lookup,
            OnlinePlayerAccess onlinePlayers,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            MoneyAdminAuthorizer authorizer,
            BalanceDisplayControl display) {
        this.money = money;
        this.lookup = lookup;
        this.onlinePlayers = onlinePlayers;
        this.notifications = notifications;
        this.errorLogger = errorLogger;
        this.authorizer = authorizer;
        this.display = display;
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
            source.send("Your balance: $" + account.balance(), MessageKind.INFO);
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
        source.send(account.lastKnownUsername() + "'s balance: $" + account.balance(), MessageKind.INFO);
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
        if (!sender.initialized()) {
            source.send("Your wallet has not been initialized.", MessageKind.ERROR);
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

        source.send("You sent " + recipient.lastKnownUsername() + " $" + amount
                + ". Your balance is now $" + result.sourceBalanceAfter() + ".", MessageKind.SUCCESS);
        String recipientMessage = source.name() + " sent you $" + amount
                + ". Your balance is now $" + result.destinationBalanceAfter() + ".";
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

    public void larp(CommandSource source, String[] arguments) {
        if (arguments.length < 2 || !arguments[0].equalsIgnoreCase("money")) {
            source.send("Usage: /larp money <account|initialize|give|take|set|display>", MessageKind.ERROR);
            return;
        }
        String operation = arguments[1].toLowerCase(Locale.ROOT);
        switch (operation) {
            case "account" -> accountCreate(source, arguments);
            case "initialize" -> initialize(source, arguments);
            case "give" -> adminChange(source, arguments, MoneyAdminAction.MONEY_GIVE);
            case "take" -> adminChange(source, arguments, MoneyAdminAction.MONEY_TAKE);
            case "set" -> adminChange(source, arguments, MoneyAdminAction.MONEY_SET);
            case "display" -> display(source, arguments);
            default -> source.send("Unknown protected money operation.", MessageKind.ERROR);
        }
    }

    private void accountCreate(CommandSource source, String[] arguments) {
        MoneyAdminAction action = MoneyAdminAction.MONEY_ACCOUNT_CREATE;
        if (!allowed(source, action)) return;
        if (arguments.length != 4 || !arguments[2].equalsIgnoreCase("create")) {
            source.send("Usage: /larp money account create <online-player>", MessageKind.ERROR);
            return;
        }
        Optional<OnlinePlayerIdentity> target = onlinePlayers.findExact(arguments[3]);
        if (target.isEmpty()) {
            source.send("That player is not online with that exact name.", MessageKind.ERROR);
            return;
        }
        OnlinePlayerIdentity player = target.orElseThrow();
        AccountCreationResult result = money.createAccount(player.playerId(), player.username());
        if (result.status() == AccountCreationResult.Status.CREATED) {
            errorLogger.accept("Money account created by " + source.name() + " for " + player.playerId());
            source.send("Created a $0 wallet for " + player.username() + ".", MessageKind.SUCCESS);
            display.refresh(player.playerId());
        } else if (result.status() == AccountCreationResult.Status.ALREADY_EXISTS) {
            source.send(player.username() + " already has a wallet. Current balance: $"
                    + result.account().balance() + ".", MessageKind.INFO);
        } else {
            source.send("The wallet could not be saved. Please contact an admin.", MessageKind.ERROR);
        }
    }

    private void initialize(CommandSource source, String[] arguments) {
        if (!allowed(source, MoneyAdminAction.MONEY_COMPETITOR_INITIALIZE)) return;
        if (arguments.length != 3) {
            source.send("Usage: /larp money initialize <online-player>", MessageKind.ERROR);
            return;
        }
        Optional<OnlinePlayerIdentity> target = onlinePlayers.findExact(arguments[2]);
        if (target.isEmpty()) {
            source.send("That player is not online with that exact name.", MessageKind.ERROR);
            return;
        }
        OnlinePlayerIdentity player = target.orElseThrow();
        TransactionResult result = money.initializeEligibleCompetitor(player.playerId(), player.username());
        if (result.status() == TransactionStatus.SUCCESS) {
            source.send("Initialized " + player.username() + "'s competitor wallet with $200.",
                    MessageKind.SUCCESS);
        } else if (result.status() == TransactionStatus.ALREADY_INITIALIZED) {
            source.send(player.username() + "'s competitor wallet was already initialized. Current balance: $"
                    + result.sourceBalanceAfter() + ".", MessageKind.INFO);
        } else {
            errorLogger.accept("Could not initialize wallet for " + player.playerId()
                    + ": transaction status " + result.status());
            source.send("The wallet could not be saved. Please contact an admin.", MessageKind.ERROR);
        }
    }

    private void adminChange(CommandSource source, String[] arguments, MoneyAdminAction action) {
        if (!allowed(source, action)) return;
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
        if (resolved == null) return;
        WalletAccount target = account(resolved.player().playerId(), source);
        if (target == null) return;
        MoneyAuditContext context = new MoneyAuditContext(
                action.name(), "/larp money " + arguments[1].toLowerCase(Locale.ROOT),
                source.name(), source.isConsole() ? "CONSOLE" : "PLAYER");
        TransactionResult result = switch (action) {
            case MONEY_GIVE -> money.add(target.playerId(), amount, reason, source.playerId().orElse(null), context);
            case MONEY_TAKE -> money.remove(target.playerId(), amount, reason, source.playerId().orElse(null), context);
            case MONEY_SET -> money.set(target.playerId(), amount, reason, source.playerId().orElse(null), context);
            default -> throw new IllegalStateException("Unexpected action " + action);
        };
        if (!result.successful()) {
            handleAdminFailure(source, result, set);
            return;
        }
        String verb = switch (action) {
            case MONEY_GIVE -> "Added $" + amount + " to " + target.lastKnownUsername() + ".";
            case MONEY_TAKE -> "Removed $" + amount + " from " + target.lastKnownUsername() + ".";
            case MONEY_SET -> "Set " + target.lastKnownUsername() + "'s balance.";
            default -> throw new IllegalStateException();
        };
        source.send(verb, MessageKind.SUCCESS);
        source.send("Balance: $" + result.sourceBalanceBefore() + " → $" + result.sourceBalanceAfter(),
                MessageKind.INFO);
        source.send("Reason: " + reason, MessageKind.INFO);
    }

    private void display(CommandSource source, String[] arguments) {
        if (!allowed(source, MoneyAdminAction.MONEY_DISPLAY_TOGGLE)) return;
        if (arguments.length != 3 || (!arguments[2].equalsIgnoreCase("on")
                && !arguments[2].equalsIgnoreCase("off"))) {
            source.send("Usage: /larp money display <on|off>", MessageKind.ERROR);
            return;
        }
        boolean enabled = arguments[2].equalsIgnoreCase("on");
        try {
            boolean changed = display.setEnabled(enabled);
            source.send("Personal balance display " + (changed ? "" : "is already ")
                    + (enabled ? "enabled." : "disabled."), changed ? MessageKind.SUCCESS : MessageKind.INFO);
        } catch (IOException exception) {
            errorLogger.accept("Could not persist display setting: " + exception.getMessage());
            source.send("The display setting could not be saved. Please contact an admin.", MessageKind.ERROR);
        }
    }

    public List<String> tabComplete(String command, CommandSource source, String[] arguments) {
        List<String> candidates;
        if (command.equalsIgnoreCase("balance") && arguments.length == 1) {
            candidates = lookup.suggestions();
        } else if (command.equalsIgnoreCase("pay") && arguments.length == 1) {
            candidates = lookup.suggestions().stream()
                    .filter(name -> !name.equalsIgnoreCase(source.name())).toList();
        } else if (command.equalsIgnoreCase("larp")) {
            if (arguments.length == 1) {
                candidates = authorizedActions(source).isEmpty() ? List.of() : List.of("money");
            } else if (arguments.length == 2 && arguments[0].equalsIgnoreCase("money")) {
                candidates = authorizedActions(source);
            } else if (arguments.length == 3 && arguments[0].equalsIgnoreCase("money")
                    && arguments[1].equalsIgnoreCase("account")
                    && authorizer.isAllowed(source, MoneyAdminAction.MONEY_ACCOUNT_CREATE)) {
                candidates = List.of("create");
            } else if (arguments.length == 4 && arguments[0].equalsIgnoreCase("money")
                    && arguments[1].equalsIgnoreCase("account") && arguments[2].equalsIgnoreCase("create")
                    && authorizer.isAllowed(source, MoneyAdminAction.MONEY_ACCOUNT_CREATE)) {
                candidates = onlinePlayers.onlinePlayers().stream()
                        .map(OnlinePlayerIdentity::username).toList();
            } else if (arguments.length == 3 && arguments[0].equalsIgnoreCase("money")
                    && arguments[1].equalsIgnoreCase("initialize")
                    && authorizer.isAllowed(source, MoneyAdminAction.MONEY_COMPETITOR_INITIALIZE)) {
                candidates = onlinePlayers.onlinePlayers().stream().map(OnlinePlayerIdentity::username).toList();
            } else if (arguments.length == 3 && arguments[0].equalsIgnoreCase("money")
                    && adminAction(arguments[1]).map(action -> authorizer.isAllowed(source, action)).orElse(false)) {
                candidates = lookup.suggestions();
            } else if (arguments.length == 3 && arguments[0].equalsIgnoreCase("money")
                    && arguments[1].equalsIgnoreCase("display")
                    && authorizer.isAllowed(source, MoneyAdminAction.MONEY_DISPLAY_TOGGLE)) {
                candidates = List.of("on", "off");
            } else {
                return List.of();
            }
        } else {
            return List.of();
        }
        String prefix = arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private boolean allowed(CommandSource source, MoneyAdminAction action) {
        if (authorizer.isAllowed(source, action)) return true;
        source.send("You are not authorized to perform that action.", MessageKind.ERROR);
        return false;
    }

    private List<String> authorizedActions(CommandSource source) {
        List<String> values = new java.util.ArrayList<>();
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_ACCOUNT_CREATE)) values.add("account");
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_COMPETITOR_INITIALIZE)) values.add("initialize");
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_GIVE)) values.add("give");
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_TAKE)) values.add("take");
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_SET)) values.add("set");
        if (authorizer.isAllowed(source, MoneyAdminAction.MONEY_DISPLAY_TOGGLE)) values.add("display");
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
            case INSUFFICIENT_FUNDS -> source.send("That wallet only has $" + result.sourceBalanceBefore() + ".",
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
                        ? "Your wallet has not been initialized."
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
            case FOUND -> { return result; }
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
            case INSUFFICIENT_FUNDS -> source.send("You only have $" + result.sourceBalanceBefore() + ".",
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
        if (!value.matches("[0-9]+")) return null;
        try {
            long amount = Long.parseLong(value);
            return amount > 0 || (zeroAllowed && amount == 0) ? amount : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
