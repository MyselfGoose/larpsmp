package com.larpsmp.moneyevent.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Short LarpSMP player command list for {@code /help}.
 */
public final class PlayerHelpCommandController {

    public void help(CommandSource source) {
        source.send(Component.text("— LarpSMP commands —", NamedTextColor.AQUA, TextDecoration.BOLD));
        source.send(line("/balance [player]", "Show wallet balances"));
        source.send(line("/pay <player> <amount>", "Send money"));
        source.send(line("/top <teams|money|kills>", "Public standings"));
        source.send(line("/team", "Your roster and Team Money"));
        source.send(line("/team balance", "Your team wealth"));
        source.send(line("/help", "This list"));
        source.send(Component.text()
                .append(Component.text("Capture: ", NamedTextColor.GRAY))
                .append(Component.text(
                        "watch the boss bar and chat milestones while a point is active.",
                        NamedTextColor.WHITE))
                .build());
    }

    private static Component line(String command, String description) {
        return Component.text()
                .append(Component.text(command, NamedTextColor.GOLD))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(description, NamedTextColor.WHITE))
                .build();
    }
}
