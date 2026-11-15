package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.wallet.MoneyFormats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Chat formatting for {@code /top} standings — distinct colors so titles and ranks
 * do not blend into gold INFO or yellow WARNING lines.
 */
public final class StandingsMessages {

    private static final NamedTextColor TITLE = NamedTextColor.AQUA;
    private static final NamedTextColor RANK = NamedTextColor.GRAY;
    private static final NamedTextColor NAME = NamedTextColor.WHITE;
    private static final NamedTextColor SEPARATOR = NamedTextColor.DARK_GRAY;
    private static final NamedTextColor AMOUNT = NamedTextColor.GOLD;

    private StandingsMessages() {
    }

    public static Component title(String label) {
        return Component.text("— " + label + " —", TITLE, TextDecoration.BOLD);
    }

    public static Component entry(int rank, String name, long dollars) {
        return Component.text()
                .append(Component.text(rank + ". ", RANK))
                .append(Component.text(name, NAME))
                .append(Component.text(" — ", SEPARATOR))
                .append(Component.text(MoneyFormats.currency(dollars), AMOUNT))
                .build();
    }
}
