package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.wallet.MoneyFormats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

/**
 * Adventure styling for the stacked money / standings sidebar.
 *
 * <p>Markdown-like hierarchy (same palette everywhere):
 * <ul>
 *   <li>Headings — white bold</li>
 *   <li>Body (names / values) — gray</li>
 *   <li>Money amounts — gold</li>
 *   <li>Rank numbers / dividers — dark gray</li>
 * </ul>
 *
 * <p>Ranking rows put the name in the scoreboard team {@code prefix} and the amount in the
 * {@code suffix} so values sit on the right edge of the panel.
 */
public final class HudTheme {
    /** Section titles: Team Money, Wallet, TOP TEAMS, TOP PLAYERS. */
    public static final TextColor HEADING = NamedTextColor.WHITE;
    /** Names and secondary body text. */
    public static final TextColor BODY = NamedTextColor.GRAY;
    /** Dollar amounts. */
    public static final TextColor MONEY = NamedTextColor.GOLD;
    /** Rank indices and dividers. */
    public static final TextColor META = NamedTextColor.DARK_GRAY;

    /** Prefix-only name width; amounts live in the suffix so names can be longer. */
    public static final int MAX_NAME_CHARS = 12;

    private HudTheme() {
    }

    public static Component heading(String text) {
        return Component.text(text, HEADING, TextDecoration.BOLD);
    }

    public static Component teamsHeader() {
        return heading("TOP TEAMS");
    }

    public static Component playersHeader() {
        return heading("TOP PLAYERS");
    }

    public static Component teamMoneyLabel() {
        return heading("Team Money");
    }

    public static Component walletLabel() {
        return heading("Wallet");
    }

    public static Component divider() {
        return Component.text("··········", META);
    }

    public static Component empty() {
        return Component.empty();
    }

    public static Component teamMoneyAmount(@Nullable Long wealth) {
        return Component.text(MoneyFormats.currencyOrDash(wealth), MONEY);
    }

    public static Component walletAmount(long balance) {
        return Component.text(MoneyFormats.currency(balance), MONEY);
    }

    public static Component teamNamePrefix(int rank, @Nullable String name) {
        return rankingPrefix(rank, name == null ? "—" : truncate(name));
    }

    public static Component playerNamePrefix(int rank, @Nullable String name) {
        return rankingPrefix(rank, name == null ? "—" : truncate(name));
    }

    public static Component amountSuffix(@Nullable Long amount) {
        if (amount == null) {
            return Component.empty();
        }
        return Component.text(" " + MoneyFormats.currency(amount), MONEY);
    }

    private static Component rankingPrefix(int rank, String name) {
        return Component.text()
                .append(Component.text(rank + ". ", META))
                .append(Component.text(name, BODY))
                .build();
    }

    public static String truncate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        String trimmed = raw.trim();
        if (trimmed.length() <= MAX_NAME_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(1, MAX_NAME_CHARS - 1)) + "…";
    }

    public static String plainTeamRow(int rank, @Nullable String name, @Nullable Long wealth) {
        if (name == null || wealth == null) {
            return rank + ". —";
        }
        return rank + ". " + truncate(name) + " " + MoneyFormats.currency(wealth);
    }

    public static String plainPlayerRow(int rank, @Nullable String name, @Nullable Long balance) {
        if (name == null || balance == null) {
            return rank + ". —";
        }
        return rank + ". " + truncate(name) + " " + MoneyFormats.currency(balance);
    }
}
