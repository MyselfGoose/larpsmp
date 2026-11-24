package com.larpsmp.moneyevent.auction;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import com.larpsmp.moneyevent.command.PlayerLookup;
import com.larpsmp.moneyevent.command.PlayerLookupResult;
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

/**
 * Admin commands for auctions ({@code /larp auction ...}).
 */
public final class AuctionCommandController {

    private final AuctionService auctionService;
    private final MoneyService money;
    private final PlayerLookup playerLookup;
    private final Consumer<String> errorLogger;

    public AuctionCommandController(
            AuctionService auctionService,
            MoneyService money,
            PlayerLookup playerLookup,
            Consumer<String> errorLogger) {
        this.auctionService = Objects.requireNonNull(auctionService, "auctionService");
        this.money = Objects.requireNonNull(money, "money");
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
            case "countdown" -> respond(source, auctionService.countdown());
            case "close" -> close(source);
            case "forfeit" -> forfeit(source);
            case "delivered" -> respond(source, auctionService.markDelivered());
            case "status" -> status(source);
            case "end" -> respond(source, auctionService.endSession());
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
            source.send("Usage: /larp auction session start", MessageKind.ERROR);
            return;
        }
        switch (auctionService.startSession()) {
            case AuctionService.SessionStartResult.Started started ->
                    source.send("Auction session started (" + started.session().sessionId() + ").",
                            MessageKind.SUCCESS);
            case AuctionService.SessionStartResult.AlreadyActive already ->
                    source.send("An auction session is already active ("
                            + already.session().sessionId() + ").", MessageKind.ERROR);
            case AuctionService.SessionStartResult.Failed failed ->
                    source.send("Could not start auction session: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void payee(CommandSource source, String[] arguments) {
        if (arguments.length == 2) {
            source.send(
                    "Auction payment recipient: " + auctionService.config().paymentUsername()
                            + ". Change with /larp auction payee <player>.",
                    MessageKind.INFO);
            return;
        }
        if (arguments.length != 3) {
            source.send("Usage: /larp auction payee <player>", MessageKind.ERROR);
            return;
        }
        WalletAccount wallet = resolveWallet(arguments[2], source);
        if (wallet == null) {
            return;
        }
        String senderType = source.isConsole() ? "console" : "player";
        switch (auctionService.setPaymentRecipient(wallet.lastKnownUsername(), source.name(), senderType)) {
            case AuctionService.PayeeResult.Changed changed -> {
                source.send("Auction payment recipient: "
                                + changed.previousUsername() + " → " + changed.newUsername() + ".",
                        MessageKind.SUCCESS);
                if (changed.lotAwaitingPayment()) {
                    source.send(
                            "A lot is awaiting payment — the winner must /pay "
                                    + changed.newUsername() + " the exact owed amount.",
                            MessageKind.INFO);
                }
            }
            case AuctionService.PayeeResult.Unchanged unchanged ->
                    source.send("Payment recipient is already " + unchanged.username() + ".",
                            MessageKind.INFO);
            case AuctionService.PayeeResult.InvalidUsername ignored ->
                    source.send("Usage: /larp auction payee <player>", MessageKind.ERROR);
            case AuctionService.PayeeResult.AccountNotFound missing ->
                    source.send("No LarpSMP account named '" + missing.username() + "'.", MessageKind.ERROR);
            case AuctionService.PayeeResult.WalletMissing missing ->
                    source.send("Account '" + missing.username() + "' has no wallet.", MessageKind.ERROR);
            case AuctionService.PayeeResult.Failed failed ->
                    source.send("Could not update payment recipient: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void rep(CommandSource source, String[] arguments) {
        if (arguments.length != 3) {
            source.send("Usage: /larp auction rep <player>", MessageKind.ERROR);
            return;
        }
        WalletAccount wallet = resolveWallet(arguments[2], source);
        if (wallet == null) {
            return;
        }
        switch (auctionService.registerRepresentative(wallet.accountId())) {
            case AuctionService.RepResult.Ok ok ->
                    source.send("Registered " + wallet.lastKnownUsername()
                            + " as " + ok.team().displayName() + " representative.", MessageKind.SUCCESS);
            case AuctionService.RepResult.NoSession ignored ->
                    source.send("Start an auction session first: /larp auction session start",
                            MessageKind.ERROR);
            case AuctionService.RepResult.Locked ignored ->
                    source.send("Representatives are locked after the first lot opens.", MessageKind.ERROR);
            case AuctionService.RepResult.NotOnTeam ignored ->
                    source.send("That player is not on a competing team.", MessageKind.ERROR);
            case AuctionService.RepResult.AlreadyOtherTeam ignored ->
                    source.send("That player is already registered for another team.", MessageKind.ERROR);
            case AuctionService.RepResult.Failed failed ->
                    source.send("Could not register representative: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void open(CommandSource source, String[] arguments) {
        if (arguments.length < 4) {
            source.send("Usage: /larp auction open <theme...> <startingBid>", MessageKind.ERROR);
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
            case AuctionService.OpenLotResult.Opened opened ->
                    source.send("Opened lot " + opened.lot().lotNumber() + ": \""
                            + opened.lot().theme() + "\" at "
                            + MoneyFormats.currency(opened.lot().startingBid()) + ".",
                            MessageKind.SUCCESS);
            case AuctionService.OpenLotResult.NoSession ignored ->
                    source.send("Start an auction session first: /larp auction session start",
                            MessageKind.ERROR);
            case AuctionService.OpenLotResult.LotBusy busy ->
                    source.send("Lot " + busy.lot().lotNumber() + " is still "
                            + busy.lot().status() + ".", MessageKind.ERROR);
            case AuctionService.OpenLotResult.MissingReps missing ->
                    source.send("Missing representatives for: " + String.join(", ", missing.teamNames()),
                            MessageKind.ERROR);
            case AuctionService.OpenLotResult.InvalidTheme ignored ->
                    source.send("Theme cannot be blank.", MessageKind.ERROR);
            case AuctionService.OpenLotResult.InvalidStartingBid ignored ->
                    source.send("Starting bid must be a non-negative whole dollar amount.", MessageKind.ERROR);
            case AuctionService.OpenLotResult.Failed failed ->
                    source.send("Could not open lot: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void close(CommandSource source) {
        switch (auctionService.closeLot()) {
            case AuctionService.CloseLotResult.AwaitingPayment awaiting ->
                    source.send("Lot " + awaiting.lot().lotNumber() + " sold for "
                            + MoneyFormats.currency(awaiting.lot().owedAmount() == null
                            ? 0L : awaiting.lot().owedAmount())
                            + ". Waiting for payment.", MessageKind.SUCCESS);
            case AuctionService.CloseLotResult.Unsold unsold ->
                    source.send("Lot " + unsold.lot().lotNumber() + " closed with no bids (unsold).",
                            MessageKind.INFO);
            case AuctionService.CloseLotResult.NoOpenLot ignored ->
                    source.send("No open lot to close.", MessageKind.ERROR);
            case AuctionService.CloseLotResult.Failed failed ->
                    source.send("Could not close lot: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void forfeit(CommandSource source) {
        switch (auctionService.forfeit()) {
            case AuctionService.ForfeitResult.Offered offered ->
                    source.send("Offered lot to previous bidder for "
                            + MoneyFormats.currency(offered.bid().amount()) + ".", MessageKind.SUCCESS);
            case AuctionService.ForfeitResult.Unsold unsold ->
                    source.send("Lot " + unsold.lot().lotNumber()
                            + " unsold — no previous bidder could pay.", MessageKind.INFO);
            case AuctionService.ForfeitResult.NoPendingPayment ignored ->
                    source.send("No lot is awaiting payment.", MessageKind.ERROR);
            case AuctionService.ForfeitResult.Failed failed ->
                    source.send("Could not forfeit: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void status(CommandSource source) {
        AuctionService.StatusSnapshot snapshot = auctionService.status();
        if (snapshot.session() == null) {
            source.send("No active auction session.", MessageKind.INFO);
            return;
        }
        source.send("Session " + snapshot.session().sessionId() + " — " + snapshot.session().status(),
                MessageKind.INFO);
        source.send("Payment recipient: " + snapshot.paymentUsername(), MessageKind.INFO);
        if (snapshot.representatives().isEmpty()) {
            source.send("Representatives: none registered yet.", MessageKind.INFO);
        } else {
            source.send("Representatives registered: " + snapshot.representatives().size(), MessageKind.INFO);
            for (AuctionRepresentative rep : snapshot.representatives()) {
                String username = resolveAccountUsername(rep.accountId());
                source.send("  - " + username + " (team " + rep.teamId() + ")", MessageKind.INFO);
            }
        }
        if (snapshot.lot() == null) {
            source.send("Active lot: none.", MessageKind.INFO);
            return;
        }
        AuctionLot lot = snapshot.lot();
        source.send("Lot " + lot.lotNumber() + " \"" + lot.theme() + "\" — " + lot.status()
                        + "; starting " + MoneyFormats.currency(lot.startingBid())
                        + (lot.highBid() == null ? "" : "; high " + MoneyFormats.currency(lot.highBid()))
                        + (lot.owedAmount() == null
                        ? ""
                        : "; owed " + MoneyFormats.currency(lot.owedAmount())),
                MessageKind.INFO);
    }

    private void respond(CommandSource source, AuctionService.AdminResult result) {
        switch (result) {
            case AuctionService.AdminResult.Ok ok -> source.send(ok.message(), MessageKind.SUCCESS);
            case AuctionService.AdminResult.Fail fail -> source.send(fail.message(), MessageKind.ERROR);
        }
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
        source.send(
                "Usage: /larp auction <session start|rep|payee|open|countdown|close|forfeit|delivered|status|end> ...",
                MessageKind.ERROR);
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
