package com.larpsmp.moneyevent.auction;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import com.larpsmp.moneyevent.wallet.MoneyFormats;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Player command {@code /bid <amount>} for auction representatives.
 */
public final class BidCommandController {

    private final AuctionService auctionService;
    private final MoneyService money;
    private final Consumer<String> errorLogger;

    public BidCommandController(
            AuctionService auctionService,
            MoneyService money,
            Consumer<String> errorLogger) {
        this.auctionService = Objects.requireNonNull(auctionService, "auctionService");
        this.money = Objects.requireNonNull(money, "money");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void bid(CommandSource source, String[] arguments) {
        if (source.playerId().isEmpty()) {
            source.send("Only players can use /bid.", MessageKind.ERROR);
            return;
        }
        if (arguments.length != 1) {
            source.send("Usage: /bid <amount>", MessageKind.ERROR);
            return;
        }
        Long amount = parsePositiveAmount(arguments[0]);
        if (amount == null) {
            source.send("Amounts must be positive whole dollars.", MessageKind.ERROR);
            return;
        }
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
            errorLogger.accept("Could not load wallet for bid: " + exception.getMessage());
            source.send("The wallet could not be loaded. Please contact an admin.", MessageKind.ERROR);
            return;
        }

        switch (auctionService.bid(wallet.accountId(), amount)) {
            case AuctionService.BidResult.Accepted accepted ->
                    source.send("Bid placed: " + MoneyFormats.currency(accepted.bid().amount()) + ".",
                            MessageKind.SUCCESS);
            case AuctionService.BidResult.NoSession ignored ->
                    source.send("No auction session is active.", MessageKind.ERROR);
            case AuctionService.BidResult.NoLot ignored ->
                    source.send("No lot is open for bidding.", MessageKind.ERROR);
            case AuctionService.BidResult.LotNotOpen ignored ->
                    source.send("Bidding is not open on the current lot.", MessageKind.ERROR);
            case AuctionService.BidResult.CountdownLocked ignored ->
                    source.send("Final countdown — no new bids.", MessageKind.ERROR);
            case AuctionService.BidResult.NotRepresentative ignored ->
                    source.send("Only registered auction representatives can bid.", MessageKind.ERROR);
            case AuctionService.BidResult.InvalidAmount ignored ->
                    source.send("Amounts must be positive whole dollars.", MessageKind.ERROR);
            case AuctionService.BidResult.TooLow tooLow ->
                    source.send("Bid must be at least " + MoneyFormats.currency(tooLow.minimum()) + ".",
                            MessageKind.ERROR);
            case AuctionService.BidResult.InsufficientFunds funds ->
                    source.send("You only have " + MoneyFormats.currency(funds.balance()) + ".",
                            MessageKind.ERROR);
            case AuctionService.BidResult.WalletMissing ignored ->
                    source.send("You do not have a registered wallet.", MessageKind.ERROR);
            case AuctionService.BidResult.Failed failed ->
                    source.send("Could not place bid: " + failed.message(), MessageKind.ERROR);
        }
    }

    public List<String> tabComplete(String[] arguments) {
        return List.of();
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
}
