package com.larpsmp.moneyevent.auction;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
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
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Player command {@code /auction pay} — settles an outstanding auction win for the exact owed amount.
 */
public final class AuctionPayCommandController {

    private final AuctionService auctionService;
    private final MoneyService money;
    private final Consumer<String> errorLogger;

    public AuctionPayCommandController(
            AuctionService auctionService,
            MoneyService money,
            Consumer<String> errorLogger) {
        this.auctionService = Objects.requireNonNull(auctionService, "auctionService");
        this.money = Objects.requireNonNull(money, "money");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void auction(CommandSource source, String[] arguments) {
        if (source.playerId().isEmpty()) {
            source.send("Only players can use /auction.", MessageKind.ERROR);
            return;
        }
        if (arguments.length == 0) {
            source.send(AuctionMessages.usage("Usage: /auction pay"));
            return;
        }
        String sub = arguments[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("pay") || arguments.length != 1) {
            source.send(AuctionMessages.usage("Usage: /auction pay"));
            return;
        }
        pay(source);
    }

    private void pay(CommandSource source) {
        UUID minecraftUuid = source.playerId().orElseThrow();
        WalletAccount wallet;
        try {
            Optional<WalletAccount> loaded = money.account(minecraftUuid);
            if (loaded.isEmpty()) {
                source.send("You do not have a registered wallet.", MessageKind.ERROR);
                return;
            }
            wallet = loaded.get();
        } catch (IOException exception) {
            errorLogger.accept("Could not load wallet for /auction pay: " + exception.getMessage());
            source.send("The wallet could not be loaded. Please contact an admin.", MessageKind.ERROR);
            return;
        }

        switch (auctionService.payOutstanding(wallet.accountId())) {
            case AuctionService.PayLotResult.Paid paid -> {
                long after = paid.transfer().sourceBalanceAfter() == null
                        ? 0L
                        : paid.transfer().sourceBalanceAfter();
                source.send(AuctionMessages.title("Auction paid"));
                source.send(AuctionMessages.labelValue(
                        "Lot", paid.lot().lotNumber() + " — \"" + paid.lot().theme() + "\""));
                source.send(AuctionMessages.labelMoney("Paid", paid.transfer().amount()));
                source.send(AuctionMessages.labelValue("To", paid.payeeUsername()));
                source.send(Component.text()
                        .append(Component.text("Balance: ", NamedTextColor.GRAY))
                        .append(Component.text(MoneyFormats.currency(after), NamedTextColor.GOLD, TextDecoration.BOLD))
                        .build());
                source.send(AuctionMessages.info("Wait for the host to deliver the lot."));
            }
            case AuctionService.PayLotResult.NothingDue ignored ->
                    source.send("You do not owe an auction payment right now.", MessageKind.ERROR);
            case AuctionService.PayLotResult.NotWinner ignored ->
                    source.send("Only the winning bidder can use /auction pay for this lot.", MessageKind.ERROR);
            case AuctionService.PayLotResult.InsufficientFunds funds ->
                    source.send("You need " + MoneyFormats.currency(funds.owed())
                            + " but only have " + MoneyFormats.currency(funds.balance()) + ".",
                            MessageKind.ERROR);
            case AuctionService.PayLotResult.WalletMissing ignored ->
                    source.send("You do not have a registered wallet.", MessageKind.ERROR);
            case AuctionService.PayLotResult.PayeeMissing missing ->
                    source.send("Auction payee '" + missing.username()
                            + "' is not available. Contact an admin.", MessageKind.ERROR);
            case AuctionService.PayLotResult.Failed failed ->
                    source.send("Could not pay: " + failed.message(), MessageKind.ERROR);
        }
    }

    public List<String> tabComplete(String[] arguments) {
        if (arguments.length == 1) {
            String prefix = arguments[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            if ("pay".startsWith(prefix)) {
                matches.add("pay");
            }
            return matches;
        }
        return List.of();
    }
}
