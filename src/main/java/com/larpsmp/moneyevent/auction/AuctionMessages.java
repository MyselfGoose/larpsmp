package com.larpsmp.moneyevent.auction;

import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.wallet.MoneyFormats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

/**
 * Rich chat formatting for auction admin feedback and public announcements.
 * Matches the {@code /top} / personal-wealth visual language (aqua titles, gold money).
 */
public final class AuctionMessages {

    private static final NamedTextColor TITLE = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor SEPARATOR = NamedTextColor.DARK_GRAY;
    private static final NamedTextColor MONEY = NamedTextColor.GOLD;
    private static final NamedTextColor MUTED = NamedTextColor.DARK_GRAY;
    private static final NamedTextColor EMPHASIS = NamedTextColor.GREEN;
    private static final NamedTextColor TAG = NamedTextColor.GOLD;

    private AuctionMessages() {
    }

    public static Component title(String label) {
        return Component.text("— " + label + " —", TITLE, TextDecoration.BOLD);
    }

    public static Component blank() {
        return Component.empty();
    }

    public static Component labelValue(String label, String value) {
        return Component.text()
                .append(Component.text(label + ": ", LABEL))
                .append(Component.text(value, VALUE))
                .build();
    }

    public static Component labelMoney(String label, long dollars) {
        return Component.text()
                .append(Component.text(label + ": ", LABEL))
                .append(Component.text(MoneyFormats.currency(dollars), MONEY))
                .build();
    }

    public static Component bullet(String text) {
        return Component.text()
                .append(Component.text("• ", MUTED))
                .append(Component.text(text, VALUE))
                .build();
    }

    public static Component repLine(Team team, String username) {
        return Component.text()
                .append(Component.text("• ", MUTED))
                .append(Component.text(team.displayName(), teamColor(team), TextDecoration.BOLD))
                .append(Component.text(" — ", SEPARATOR))
                .append(Component.text(username, VALUE))
                .build();
    }

    public static Component success(String message) {
        return Component.text(message, EMPHASIS);
    }

    public static Component info(String message) {
        return Component.text(message, LABEL);
    }

    public static Component detail(String message) {
        return Component.text(message, VALUE);
    }

    public static Component money(long dollars) {
        return Component.text(MoneyFormats.currency(dollars), MONEY);
    }

    public static Component usage(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    // --- Public broadcast lines (everyone sees these) ---

    public static Component broadcastTag() {
        return Component.text()
                .append(Component.text("[", SEPARATOR))
                .append(Component.text("Auction", TAG, TextDecoration.BOLD))
                .append(Component.text("] ", SEPARATOR))
                .build();
    }

    public static Component broadcastSessionStarted() {
        return tagged(Component.text()
                .append(Component.text("Session started. ", VALUE))
                .append(Component.text("Register one bidder per team.", LABEL))
                .build());
    }

    public static Component broadcastSessionEnded() {
        return tagged(Component.text("Session ended.", VALUE));
    }

    public static Component broadcastRep(Team team, String username) {
        return tagged(Component.text()
                .append(Component.text(team.displayName(), teamColor(team), TextDecoration.BOLD))
                .append(Component.text(" bidder: ", LABEL))
                .append(Component.text(username, VALUE))
                .build());
    }

    public static Component broadcastPayeeChanged(String username, boolean awaitingPayment) {
        Component base = Component.text()
                .append(Component.text("Pay to: ", LABEL))
                .append(Component.text(username, VALUE))
                .build();
        if (!awaitingPayment) {
            return tagged(base);
        }
        return tagged(Component.text()
                .append(base)
                .append(Component.text(" — ", SEPARATOR))
                .append(Component.text("outstanding lot must pay this account.", NamedTextColor.YELLOW))
                .build());
    }

    public static Component broadcastLotOpen(int lotNumber, String theme, long startingBid) {
        return tagged(Component.text()
                .append(Component.text("Lot " + lotNumber, VALUE, TextDecoration.BOLD))
                .append(Component.text(" — ", SEPARATOR))
                .append(Component.text("\"" + theme + "\"", NamedTextColor.AQUA))
                .append(Component.text(" · start ", LABEL))
                .append(Component.text(MoneyFormats.currency(startingBid), MONEY))
                .build());
    }

    public static Component broadcastCountdown() {
        return tagged(Component.text()
                .append(Component.text("Final countdown", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" — no new bids.", LABEL))
                .build());
    }

    public static Component broadcastBid(String teamName, @Nullable TeamColor color, long amount) {
        TextColor teamText = color == null ? VALUE : TextColor.color(color.rgb());
        return tagged(Component.text()
                .append(Component.text(teamName, teamText, TextDecoration.BOLD))
                .append(Component.text(" bids ", LABEL))
                .append(Component.text(MoneyFormats.currency(amount), MONEY))
                .build());
    }

    public static Component broadcastSold(String teamName, @Nullable TeamColor color, long amount, String payee) {
        TextColor teamText = color == null ? VALUE : TextColor.color(color.rgb());
        return tagged(Component.text()
                .append(Component.text("Sold to ", LABEL))
                .append(Component.text(teamName, teamText, TextDecoration.BOLD))
                .append(Component.text(" for ", LABEL))
                .append(Component.text(MoneyFormats.currency(amount), MONEY))
                .append(Component.text(" — pay ", LABEL))
                .append(Component.text(payee, VALUE))
                .build());
    }

    public static Component broadcastUnsold(int lotNumber) {
        return tagged(Component.text()
                .append(Component.text("Lot " + lotNumber, VALUE))
                .append(Component.text(" unsold.", LABEL))
                .build());
    }

    public static Component broadcastPaymentReceived(String teamName, @Nullable TeamColor color, long amount) {
        TextColor teamText = color == null ? VALUE : TextColor.color(color.rgb());
        return tagged(Component.text()
                .append(Component.text("Paid by ", LABEL))
                .append(Component.text(teamName, teamText, TextDecoration.BOLD))
                .append(Component.text(" — ", SEPARATOR))
                .append(Component.text(MoneyFormats.currency(amount), MONEY))
                .append(Component.text(". Deliver the lot manually.", LABEL))
                .build());
    }

    public static Component broadcastPreviousBidder(
            String teamName, @Nullable TeamColor color, long amount, String payee) {
        TextColor teamText = color == null ? VALUE : TextColor.color(color.rgb());
        return tagged(Component.text()
                .append(Component.text("Previous bidder ", LABEL))
                .append(Component.text(teamName, teamText, TextDecoration.BOLD))
                .append(Component.text(" may pay ", LABEL))
                .append(Component.text(MoneyFormats.currency(amount), MONEY))
                .append(Component.text(" to ", LABEL))
                .append(Component.text(payee, VALUE))
                .build());
    }

    public static Component broadcastDelivered(int lotNumber) {
        return tagged(Component.text()
                .append(Component.text("Lot " + lotNumber, VALUE))
                .append(Component.text(" marked delivered.", LABEL))
                .build());
    }

    private static Component tagged(Component body) {
        return Component.text().append(broadcastTag()).append(body).build();
    }

    private static TextColor teamColor(Team team) {
        return TextColor.color(team.color().rgb());
    }
}
