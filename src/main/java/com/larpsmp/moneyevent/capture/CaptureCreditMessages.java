package com.larpsmp.moneyevent.capture;

import com.larpsmp.moneyevent.wallet.MoneyFormats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Styled chat lines shown when a capture reward is credited, matching kill-credit card style.
 */
public final class CaptureCreditMessages {

    private static final NamedTextColor TITLE = NamedTextColor.AQUA;
    private static final NamedTextColor LABEL = NamedTextColor.GRAY;
    private static final NamedTextColor VALUE = NamedTextColor.WHITE;
    private static final NamedTextColor MONEY = NamedTextColor.GOLD;
    private static final NamedTextColor EARNED = NamedTextColor.GREEN;
    private static final NamedTextColor SEPARATOR = NamedTextColor.DARK_GRAY;

    private CaptureCreditMessages() {
    }

    /**
     * Personal capture reward card for one roster member's share.
     *
     * @param pointName     capture location display name
     * @param teamName      winning team display name
     * @param poolAmount    total capture reward pool (usually $50)
     * @param shareAmount   this member's credited share
     * @param balanceBefore wallet before credit
     * @param balanceAfter  wallet after credit
     */
    public static Component[] creditCard(
            String pointName,
            String teamName,
            long poolAmount,
            long shareAmount,
            long balanceBefore,
            long balanceAfter
    ) {
        return new Component[] {
                Component.text("— Capture Reward —", TITLE, TextDecoration.BOLD),
                line("Point", Component.text(pointName, VALUE)),
                line("Team", Component.text(teamName, VALUE)),
                line("Pool", Component.text()
                        .append(Component.text(MoneyFormats.currency(poolAmount), MONEY))
                        .append(Component.text(" split among roster", SEPARATOR))
                        .build()),
                line("Your share", Component.text("+" + MoneyFormats.currency(shareAmount), EARNED)),
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
