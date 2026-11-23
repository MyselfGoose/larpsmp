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
 * <p>Ranking rows put the name in the scoreboard team {@code prefix} and the amount in the
 * {@code suffix} so values sit on the right edge of the panel.
 */
public final class HudTheme {
    public static final TextColor TEAMS_HEADER = NamedTextColor.AQUA;
    public static final TextColor PLAYERS_HEADER = NamedTextColor.GOLD;
    public static final TextColor TEAM_MONEY = NamedTextColor.AQUA;
    public static final TextColor WALLET = NamedTextColor.GOLD;
    public static final TextColor AMOUNT = NamedTextColor.GOLD;
    public static final TextColor NAME = NamedTextColor.WHITE;
    public static final TextColor DIVIDER = TextColor.color(0x555555);
    public static final TextColor PLACEHOLDER = NamedTextColor.DARK_GRAY;

    public static final TextColor RANK_GOLD = TextColor.color(0xFFD700);
    public static final TextColor RANK_SILVER = TextColor.color(0xC0C0C0);
    public static final TextColor RANK_BRONZE = TextColor.color(0xCD7F32);
    public static final TextColor RANK_DEFAULT = NamedTextColor.GRAY;

    /** Prefix-only name width; amounts live in the suffix so names can be longer. */
    public static final int MAX_NAME_CHARS = 12;

    private HudTheme() {
    }

    public static Component teamsHeader() {
        return Component.text("TOP TEAMS", TEAMS_HEADER, TextDecoration.BOLD);
    }

    public static Component playersHeader() {
        return Component.text("TOP PLAYERS", PLAYERS_HEADER, TextDecoration.BOLD);
    }

    public static Component divider() {
        return Component.text("··········", DIVIDER);
    }

    public static Component empty() {
        return Component.empty();
    }

    public static Component teamMoneyLabel() {
        return Component.text("Team Money", TEAM_MONEY, TextDecoration.BOLD);
    }

    public static Component teamMoneyAmount(@Nullable Long wealth) {
        return Component.text(MoneyFormats.currencyOrDash(wealth), TEAM_MONEY);
    }

    public static Component walletLabel() {
        return Component.text("Wallet", WALLET, TextDecoration.BOLD);
    }

    public static Component walletAmount(long balance) {
        return Component.text(MoneyFormats.currency(balance), WALLET);
    }

    public static TextColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> RANK_GOLD;
            case 2 -> RANK_SILVER;
            case 3 -> RANK_BRONZE;
            default -> RANK_DEFAULT;
        };
    }

    public static Component teamNamePrefix(int rank, @Nullable String name, @Nullable Integer colorRgb) {
        if (name == null) {
            return rankingPrefix(rank, "—", PLACEHOLDER);
        }
        TextColor teamColor = colorRgb == null ? NAME : TextColor.color(colorRgb);
        return rankingPrefix(rank, truncate(name), teamColor);
    }

    public static Component playerNamePrefix(int rank, @Nullable String name) {
        if (name == null) {
            return rankingPrefix(rank, "—", PLACEHOLDER);
        }
        return rankingPrefix(rank, truncate(name), NAME);
    }

    public static Component amountSuffix(@Nullable Long amount) {
        if (amount == null) {
            return Component.empty();
        }
        return Component.text(" " + MoneyFormats.currency(amount), AMOUNT);
    }

    private static Component rankingPrefix(int rank, String name, TextColor nameColor) {
        return Component.text()
                .append(Component.text(rank + ". ", rankColor(rank)))
                .append(Component.text(name, nameColor))
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
