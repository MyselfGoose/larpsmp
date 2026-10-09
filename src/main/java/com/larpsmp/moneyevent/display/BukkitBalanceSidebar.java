package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.wallet.MoneyService;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Always-on personal Wallet HUD rendered as a compact scoreboard sidebar.
 * Score numbers are blanked so players only see the title and balance text.
 */
public final class BukkitBalanceSidebar implements BalanceDisplayControl {
    private static final String OBJECTIVE_NAME = "larpsmp_wallet";

    private final Server server;
    private final MoneyService money;
    private final Consumer<String> errorLogger;
    private final Map<UUID, OwnedSidebar> sidebars = new HashMap<>();

    public BukkitBalanceSidebar(Server server, MoneyService money, Consumer<String> errorLogger) {
        this.server = server;
        this.money = money;
        this.errorLogger = errorLogger;
    }

    @Override
    public void refresh(UUID playerId) {
        Player player = server.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            var account = money.account(playerId);
            if (account.isEmpty()) {
                remove(playerId);
                return;
            }
            OwnedSidebar sidebar = sidebars.get(playerId);
            if (sidebar != null && player.getScoreboard() != sidebar.owned()) {
                // Another system replaced our sidebar; retain its newer scoreboard and stop writing.
                return;
            }
            if (sidebar == null) {
                Scoreboard previous = player.getScoreboard();
                Scoreboard owned = server.getScoreboardManager().getNewScoreboard();
                Objective objective = owned.registerNewObjective(
                        OBJECTIVE_NAME,
                        Criteria.DUMMY,
                        Component.text("Wallet", NamedTextColor.GOLD, TextDecoration.BOLD));
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.numberFormat(NumberFormat.blank());
                sidebar = new OwnedSidebar(previous, owned, null);
                sidebars.put(playerId, sidebar);
                player.setScoreboard(owned);
            }
            Objective objective = sidebar.owned().getObjective(OBJECTIVE_NAME);
            if (objective == null) {
                remove(playerId);
                refresh(playerId);
                return;
            }
            objective.numberFormat(NumberFormat.blank());
            String amount = "$" + account.orElseThrow().balance();
            if (sidebar.amountEntry() != null && !sidebar.amountEntry().equals(amount)) {
                sidebar.owned().resetScores(sidebar.amountEntry());
            }
            objective.getScore(amount).setScore(1);
            sidebars.put(playerId, new OwnedSidebar(sidebar.previous(), sidebar.owned(), amount));
        } catch (IOException exception) {
            errorLogger.accept("Could not refresh wallet display for " + playerId + ": " + exception.getMessage());
        }
    }

    @Override
    public void playerJoined(UUID playerId) {
        refresh(playerId);
    }

    @Override
    public void playerQuit(UUID playerId) {
        remove(playerId);
    }

    private void remove(UUID playerId) {
        OwnedSidebar sidebar = sidebars.remove(playerId);
        Player player = server.getPlayer(playerId);
        if (sidebar != null && player != null && player.getScoreboard() == sidebar.owned()) {
            player.setScoreboard(sidebar.previous());
        }
    }

    @Override
    public void close() {
        for (UUID playerId : sidebars.keySet().toArray(UUID[]::new)) {
            remove(playerId);
        }
    }

    private record OwnedSidebar(Scoreboard previous, Scoreboard owned, String amountEntry) {
    }
}
