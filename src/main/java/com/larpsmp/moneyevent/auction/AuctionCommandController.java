package com.larpsmp.moneyevent.auction;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import com.larpsmp.moneyevent.command.PlayerLookup;
import com.larpsmp.moneyevent.command.PlayerLookupResult;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyFormats;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

/**
 * Admin commands for auctions ({@code /larp auction ...}).
 */
public final class AuctionCommandController {

    private final AuctionService auctionService;
    private final MoneyService money;
    private final TeamService teams;
    private final PlayerLookup playerLookup;
    private final Consumer<String> errorLogger;

    public AuctionCommandController(
            AuctionService auctionService,
            MoneyService money,
            TeamService teams,
            PlayerLookup playerLookup,
            Consumer<String> errorLogger) {
        this.auctionService = Objects.requireNonNull(auctionService, "auctionService");
        this.money = Objects.requireNonNull(money, "money");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public boolean handle(CommandSource source, String[] arguments) {
        if (arguments.length < 1 || !arguments[0].equalsIgnoreCase("auction")) {
            return false;
        }
        if (!source.isConsole() && !source.isOperator()) {
            source.send("You are not authorized to perform that action.", MessageKind.ERROR);
            return true;
        }
        if (arguments.length < 2) {
            usage(source);
            return true;
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "session" -> session(source, arguments);
            case "rep" -> rep(source, arguments);
            case "payee", "recipient" -> payee(source, arguments);
            case "open" -> open(source, arguments);
            case "countdown" -> countdown(source);
            case "close" -> close(source);
            case "forfeit" -> forfeit(source);
            case "delivered" -> delivered(source);
            case "status" -> status(source);
            case "end" -> end(source);
            default -> usage(source);
        }
        return true;
    }

    public List<String> tabComplete(CommandSource source, String[] arguments) {
        if (!source.isConsole() && !source.isOperator()) {
            return List.of();
        }
        if (arguments.length == 1) {
            return List.of("auction");
        }
        if (!arguments[0].equalsIgnoreCase("auction")) {
            return List.of();
        }
        if (arguments.length == 2) {
            return filterPrefix(
                    List.of("session", "rep", "payee", "open", "countdown", "close", "forfeit",
                            "delivered", "status", "end"),
                    arguments[1]);
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("session")) {
            return filterPrefix(List.of("start"), arguments[2]);
        }
        if (arguments.length == 3
                && (arguments[1].equalsIgnoreCase("rep")
                || arguments[1].equalsIgnoreCase("payee")
                || arguments[1].equalsIgnoreCase("recipient"))) {
            String prefix = arguments[2].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String name : playerLookup.suggestions()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(name);
                }
            }
            return matches;
        }
        return List.of();
    }

    private void session(CommandSource source, String[] arguments) {
        if (arguments.length < 3 || !arguments[2].equalsIgnoreCase("start")) {
            source.send(AuctionMessages.usage("Usage: /larp auction session start"));
            return;
        }
        switch (auctionService.startSession()) {
            case AuctionService.SessionStartResult.Started ignored -> {
                source.send(AuctionMessages.title("Auction"));
                source.send(AuctionMessages.success("Session started"));
                source.send(AuctionMessages.labelValue("Pay to", auctionService.config().paymentUsername()));
                source.send(AuctionMessages.info("Next: register one bidder per team with /larp auction rep"));
            }
            case AuctionService.SessionStartResult.AlreadyActive ignored ->
                    source.send("An auction session is already active.", MessageKind.ERROR);
            case AuctionService.SessionStartResult.Failed failed ->
                    source.send("Could not start auction session: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void payee(CommandSource source, String[] arguments) {
        if (arguments.length == 2) {
            source.send(AuctionMessages.title("Auction payee"));
            source.send(AuctionMessages.labelValue("Pay to", auctionService.config().paymentUsername()));
            source.send(AuctionMessages.info("Change with /larp auction payee <player>"));
            return;
        }
        if (arguments.length != 3) {
            source.send(AuctionMessages.usage("Usage: /larp auction payee <player>"));
            return;
        }
        WalletAccount wallet = resolveWallet(arguments[2], source);
        if (wallet == null) {
            return;
        }
        String senderType = source.isConsole() ? "console" : "player";
        switch (auctionService.setPaymentRecipient(wallet.lastKnownUsername(), source.name(), senderType)) {
            case AuctionService.PayeeResult.Changed changed -> {
                source.send(AuctionMessages.title("Auction payee"));
                source.send(Component.text()
                        .append(Component.text(changed.previousUsername(), NamedTextColor.GRAY))
                        .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(changed.newUsername(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .build());
                if (changed.lotAwaitingPayment()) {
                    source.send(Component.text(
                            "A lot is waiting for payment — winner must /pay "
                                    + changed.newUsername() + " the exact amount.",
                            NamedTextColor.YELLOW));
                }
            }
            case AuctionService.PayeeResult.Unchanged unchanged -> {
                source.send(AuctionMessages.title("Auction payee"));
                source.send(AuctionMessages.labelValue("Pay to", unchanged.username()));
                source.send(AuctionMessages.info("Already set to this account."));
            }
            case AuctionService.PayeeResult.InvalidUsername ignored ->
                    source.send(AuctionMessages.usage("Usage: /larp auction payee <player>"));
            case AuctionService.PayeeResult.AccountNotFound missing ->
                    source.send("No LarpSMP account named '" + missing.username() + "'.", MessageKind.ERROR);
            case AuctionService.PayeeResult.WalletMissing missing ->
                    source.send("Account '" + missing.username() + "' has no wallet.", MessageKind.ERROR);
            case AuctionService.PayeeResult.Failed failed ->
                    source.send("Could not update payee: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void rep(CommandSource source, String[] arguments) {
        if (arguments.length != 3) {
            source.send(AuctionMessages.usage("Usage: /larp auction rep <player>"));
            return;
        }
        WalletAccount wallet = resolveWallet(arguments[2], source);
        if (wallet == null) {
            return;
        }
        switch (auctionService.registerRepresentative(wallet.accountId())) {
            case AuctionService.RepResult.Ok ok -> {
                source.send(AuctionMessages.title("Auction bidder"));
                source.send(AuctionMessages.repLine(ok.team(), wallet.lastKnownUsername()));
            }
            case AuctionService.RepResult.NoSession ignored ->
                    source.send("Start an auction session first: /larp auction session start",
                            MessageKind.ERROR);
            case AuctionService.RepResult.Locked ignored ->
                    source.send("Bidders are locked after the first lot opens.", MessageKind.ERROR);
            case AuctionService.RepResult.NotOnTeam ignored ->
                    source.send("That player is not on a competing team.", MessageKind.ERROR);
            case AuctionService.RepResult.AlreadyOtherTeam ignored ->
                    source.send("That player is already registered for another team.", MessageKind.ERROR);
            case AuctionService.RepResult.Failed failed ->
                    source.send("Could not register bidder: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void open(CommandSource source, String[] arguments) {
        if (arguments.length < 4) {
            source.send(AuctionMessages.usage("Usage: /larp auction open <theme...> <startingBid>"));
            return;
        }
        String amountToken = arguments[arguments.length - 1];
        Long startingBid = parseNonNegativeAmount(amountToken);
        if (startingBid == null) {
            source.send("Starting bid must be a non-negative whole dollar amount.", MessageKind.ERROR);
            return;
        }
        StringBuilder theme = new StringBuilder();
        for (int index = 2; index < arguments.length - 1; index++) {
            if (theme.length() > 0) {
                theme.append(' ');
            }
            theme.append(arguments[index]);
        }
        switch (auctionService.openLot(theme.toString(), startingBid)) {
            case AuctionService.OpenLotResult.Opened opened -> {
                AuctionLot lot = opened.lot();
                source.send(AuctionMessages.title("Lot " + lot.lotNumber()));
                source.send(Component.text()
                        .append(Component.text("\"" + lot.theme() + "\"", NamedTextColor.AQUA))
                        .build());
                source.send(AuctionMessages.labelMoney("Starting bid", lot.startingBid()));
            }
            case AuctionService.OpenLotResult.NoSession ignored ->
                    source.send("Start an auction session first: /larp auction session start",
                            MessageKind.ERROR);
            case AuctionService.OpenLotResult.LotBusy busy ->
                    source.send("Lot " + busy.lot().lotNumber() + " is still "
                            + friendlyStatus(busy.lot().status()) + ".", MessageKind.ERROR);
            case AuctionService.OpenLotResult.MissingReps missing ->
                    source.send("Missing bidders for: " + String.join(", ", missing.teamNames()),
                            MessageKind.ERROR);
            case AuctionService.OpenLotResult.InvalidTheme ignored ->
                    source.send("Theme cannot be blank.", MessageKind.ERROR);
            case AuctionService.OpenLotResult.InvalidStartingBid ignored ->
                    source.send("Starting bid must be a non-negative whole dollar amount.", MessageKind.ERROR);
            case AuctionService.OpenLotResult.Failed failed ->
                    source.send("Could not open lot: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void countdown(CommandSource source) {
        switch (auctionService.countdown()) {
            case AuctionService.AdminResult.Ok ignored -> {
                source.send(AuctionMessages.title("Auction"));
                source.send(Component.text("Final countdown — bidding locked", NamedTextColor.YELLOW));
            }
            case AuctionService.AdminResult.Fail fail ->
                    source.send(fail.message(), MessageKind.ERROR);
        }
    }

    private void close(CommandSource source) {
        switch (auctionService.closeLot()) {
            case AuctionService.CloseLotResult.AwaitingPayment awaiting -> {
                AuctionLot lot = awaiting.lot();
                long owed = lot.owedAmount() == null ? 0L : lot.owedAmount();
                String winner = lot.owedAccountId() == null
                        ? "—"
                        : resolveAccountUsername(lot.owedAccountId());
                source.send(AuctionMessages.title("Lot " + lot.lotNumber() + " sold"));
                source.send(AuctionMessages.labelValue("Winner", winner));
                source.send(AuctionMessages.labelMoney("Amount", owed));
                source.send(AuctionMessages.labelValue("Pay to", auctionService.config().paymentUsername()));
                source.send(AuctionMessages.info("Waiting for /pay of the exact amount."));
            }
            case AuctionService.CloseLotResult.Unsold unsold -> {
                source.send(AuctionMessages.title("Lot " + unsold.lot().lotNumber()));
                source.send(AuctionMessages.info("Closed with no bids — unsold."));
            }
            case AuctionService.CloseLotResult.NoOpenLot ignored ->
                    source.send("No open lot to close.", MessageKind.ERROR);
            case AuctionService.CloseLotResult.Failed failed ->
                    source.send("Could not close lot: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void forfeit(CommandSource source) {
        switch (auctionService.forfeit()) {
            case AuctionService.ForfeitResult.Offered offered -> {
                String bidder = resolveAccountUsername(offered.bid().accountId());
                source.send(AuctionMessages.title("Auction forfeit"));
                source.send(AuctionMessages.labelValue("Offered to", bidder));
                source.send(AuctionMessages.labelMoney("Amount", offered.bid().amount()));
                source.send(AuctionMessages.labelValue("Pay to", auctionService.config().paymentUsername()));
            }
            case AuctionService.ForfeitResult.Unsold unsold -> {
                source.send(AuctionMessages.title("Lot " + unsold.lot().lotNumber()));
                source.send(AuctionMessages.info("Unsold — no previous bidder could pay."));
            }
            case AuctionService.ForfeitResult.NoPendingPayment ignored ->
                    source.send("No lot is awaiting payment.", MessageKind.ERROR);
            case AuctionService.ForfeitResult.Failed failed ->
                    source.send("Could not forfeit: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void delivered(CommandSource source) {
        switch (auctionService.markDelivered()) {
            case AuctionService.AdminResult.Ok ok -> {
                source.send(AuctionMessages.title("Auction"));
                source.send(AuctionMessages.success(ok.message()));
            }
            case AuctionService.AdminResult.Fail fail ->
                    source.send(fail.message(), MessageKind.ERROR);
        }
    }

    private void end(CommandSource source) {
        switch (auctionService.endSession()) {
            case AuctionService.AdminResult.Ok ignored -> {
                source.send(AuctionMessages.title("Auction"));
                source.send(AuctionMessages.success("Session ended"));
            }
            case AuctionService.AdminResult.Fail fail ->
                    source.send(fail.message(), MessageKind.ERROR);
        }
    }

    private void status(CommandSource source) {
        AuctionService.StatusSnapshot snapshot = auctionService.status();
        if (snapshot.session() == null) {
            source.send(AuctionMessages.title("Auction status"));
            source.send(AuctionMessages.info("No active session."));
            return;
        }

        source.send(AuctionMessages.title("Auction status"));
        source.send(AuctionMessages.labelValue("Session", friendlySession(snapshot.session().status())));
        source.send(AuctionMessages.labelValue("Pay to", snapshot.paymentUsername()));

        source.send(AuctionMessages.title("Bidders"));
        if (snapshot.representatives().isEmpty()) {
            source.send(AuctionMessages.info("None registered yet."));
        } else {
            for (AuctionRepresentative rep : snapshot.representatives()) {
                Optional<Team> team = teams.listTeams().stream()
                        .filter(candidate -> candidate.id().equals(rep.teamId()))
                        .findFirst();
                String username = resolveAccountUsername(rep.accountId());
                if (team.isPresent()) {
                    source.send(AuctionMessages.repLine(team.get(), username));
                } else {
                    source.send(AuctionMessages.bullet(username));
                }
            }
        }

        source.send(AuctionMessages.title("Active lot"));
        if (snapshot.lot() == null) {
            source.send(AuctionMessages.info("None."));
            return;
        }
        AuctionLot lot = snapshot.lot();
        source.send(Component.text()
                .append(Component.text("Lot " + lot.lotNumber(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text("\"" + lot.theme() + "\"", NamedTextColor.AQUA))
                .build());
        source.send(AuctionMessages.labelValue("State", friendlyStatus(lot.status())));
        source.send(AuctionMessages.labelMoney("Starting", lot.startingBid()));
        if (lot.highBid() != null) {
            String highName = lot.highBidderAccountId() == null
                    ? "—"
                    : resolveAccountUsername(lot.highBidderAccountId());
            Optional<Team> highTeam = lot.highBidderAccountId() == null
                    ? Optional.empty()
                    : teams.teamOf(lot.highBidderAccountId());
            source.send(highBidLine(lot.highBid(), highName, highTeam.orElse(null)));
        }
        if (lot.owedAmount() != null) {
            String owedName = lot.owedAccountId() == null
                    ? "—"
                    : resolveAccountUsername(lot.owedAccountId());
            source.send(AuctionMessages.labelValue("Owes", owedName));
            source.send(AuctionMessages.labelMoney("Amount due", lot.owedAmount()));
        }
    }

    private static Component highBidLine(long amount, String username, @Nullable Team team) {
        Component namePart;
        if (team != null) {
            namePart = Component.text()
                    .append(Component.text(team.displayName(), TextColor.color(team.color().rgb()),
                            TextDecoration.BOLD))
                    .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(username, NamedTextColor.WHITE))
                    .build();
        } else {
            namePart = Component.text(username, NamedTextColor.WHITE);
        }
        return Component.text()
                .append(Component.text("High bid: ", NamedTextColor.GRAY))
                .append(Component.text(MoneyFormats.currency(amount), NamedTextColor.GOLD))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(namePart)
                .build();
    }

    private WalletAccount resolveWallet(String username, CommandSource source) {
        PlayerLookupResult result = playerLookup.resolve(username);
        switch (result.status()) {
            case FOUND -> {
                try {
                    Optional<WalletAccount> wallet = money.account(result.player().playerId());
                    if (wallet.isEmpty()) {
                        source.send("That player does not have a registered wallet.", MessageKind.ERROR);
                        return null;
                    }
                    return wallet.get();
                } catch (IOException exception) {
                    errorLogger.accept("Could not load wallet for " + username + ": " + exception.getMessage());
                    source.send("The wallet could not be loaded. Please contact an admin.", MessageKind.ERROR);
                    return null;
                }
            }
            case AMBIGUOUS -> source.send(
                    "That name matches multiple registered wallets. Please contact an admin.",
                    MessageKind.ERROR);
            case STORAGE_FAILURE -> {
                errorLogger.accept("Storage failure while resolving username " + username);
                source.send("Player lookup could not be completed. Please contact an admin.", MessageKind.ERROR);
            }
            case NOT_FOUND -> source.send("That player does not have a registered wallet.", MessageKind.ERROR);
        }
        return null;
    }

    private String resolveAccountUsername(UUID accountId) {
        try {
            Optional<WalletAccount> wallet = money.accountByAccountId(accountId);
            return wallet.map(WalletAccount::lastKnownUsername).orElse(accountId.toString());
        } catch (IOException exception) {
            return accountId.toString();
        }
    }

    private static void usage(CommandSource source) {
        source.send(AuctionMessages.title("Auction commands"));
        source.send(AuctionMessages.usage(
                "/larp auction <session start|rep|payee|open|countdown|close|forfeit|delivered|status|end>"));
    }

    private static String friendlySession(AuctionSessionStatus status) {
        return switch (status) {
            case SETUP -> "Setup (register bidders)";
            case ACTIVE -> "Active";
            case CLOSED -> "Closed";
        };
    }

    private static String friendlyStatus(AuctionLotStatus status) {
        return switch (status) {
            case OPEN -> "Open for bids";
            case COUNTDOWN -> "Final countdown";
            case AWAITING_PAYMENT -> "Waiting for payment";
            case PAID -> "Paid — deliver manually";
            case DELIVERED -> "Delivered";
            case UNSOLD -> "Unsold";
        };
    }

    private static Long parseNonNegativeAmount(String raw) {
        if (!raw.matches("[0-9]+")) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
