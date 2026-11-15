package com.larpsmp.moneyevent.combat;

import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.wallet.MoneyFormats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Styled chat lines shown to a killer when death money is credited.
 */
public final class KillCreditMessages {

    private static final NamedTextColor TITLE = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor MONEY = NamedTextColor.GOLD;
    private static final NamedTextColor EARNED = NamedTextColor.GREEN;
    private static final NamedTextColor SEPARATOR = NamedTextColor.DARK_GRAY;

    private KillCreditMessages() {
    }

    /**
     * Multi-line kill credit card for the killer.
     *
     * @param victimUsername LarpSMP username of the victim
     * @param victimTier     victim competitor tier (sets death worth)
     * @param worthAmount    full death price for that tier
     * @param earnedAmount   amount actually credited to the killer
     * @param balanceBefore  killer wallet before credit
     * @param balanceAfter   killer wallet after credit
     */
    public static Component[] creditCard(
            String victimUsername,
            PlayerTier victimTier,
            long worthAmount,
            long earnedAmount,
            long balanceBefore,
            long balanceAfter
    ) {
        return new Component[] {
                Component.text("— Kill Credit —", TITLE, TextDecoration.BOLD),
                line("Killed", Component.text(victimUsername, VALUE)),
                line("Tier", Component.text()
                        .append(Component.text(victimTier.displayName(), VALUE))
                        .append(Component.text(" · worth ", SEPARATOR))
                        .append(Component.text(MoneyFormats.currency(worthAmount), MONEY))
                        .build()),
                line("Earned", Component.text("+" + MoneyFormats.currency(earnedAmount), EARNED)),
                line("Wallet", Component.text()
                        .append(Component.text(MoneyFormats.currency(balanceBefore), MONEY))
                        .append(Component.text(" → ", SEPARATOR))
                        .append(Component.text(MoneyFormats.currency(balanceAfter), MONEY))
                        .build())
        };
    }

    private static Component line(String label, Component value) {
        return Component.text()
                .append(Component.text(label + ": ", LABEL))
                .append(value)
                .build();
    }
}
