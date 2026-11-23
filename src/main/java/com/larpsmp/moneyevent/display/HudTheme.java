package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.wallet.MoneyFormats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

/**
 * Adventure styling for the stacked money / standings sidebar.
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

    /** Sidebar-safe name width (keeps prefixes readable). */
    public static final int MAX_NAME_CHARS = 10;

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

    public static TextColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> RANK_GOLD;
            case 2 -> RANK_SILVER;
            case 3 -> RANK_BRONZE;
            default -> RANK_DEFAULT;
        };
    }

    public static Component teamRow(int rank, @Nullable String name, @Nullable Integer colorRgb, @Nullable Long wealth) {
        if (name == null || wealth == null) {
            return placeholderRow(rank);
        }
        TextColor teamColor = colorRgb == null ? NAME : TextColor.color(colorRgb);
        return Component.text()
                .append(Component.text(rank + ". ", rankColor(rank)))
                .append(Component.text(truncate(name), teamColor))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(Component.text(MoneyFormats.currency(wealth), AMOUNT))
                .build();
    }

    public static Component playerRow(int rank, @Nullable String name, @Nullable Long balance) {
        if (name == null || balance == null) {
            return placeholderRow(rank);
        }
        return Component.text()
                .append(Component.text(rank + ". ", rankColor(rank)))
                .append(Component.text(truncate(name), NAME))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(Component.text(MoneyFormats.currency(balance), AMOUNT))
                .build();
    }

    public static Component teamMoneyLine(@Nullable Long wealth) {
        return Component.text()
                .append(Component.text("Team Money ", TEAM_MONEY, TextDecoration.BOLD))
                .append(Component.text(MoneyFormats.currencyOrDash(wealth), TEAM_MONEY))
                .build();
    }

    public static Component walletLine(long balance) {
        return Component.text()
                .append(Component.text("Wallet ", WALLET, TextDecoration.BOLD))
                .append(Component.text(MoneyFormats.currency(balance), WALLET))
                .build();
    }

    public static Component placeholderRow(int rank) {
        return Component.text()
                .append(Component.text(rank + ". ", rankColor(rank)))
                .append(Component.text("—", PLACEHOLDER))
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

    /** Plain-text shape used by unit tests. */
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
