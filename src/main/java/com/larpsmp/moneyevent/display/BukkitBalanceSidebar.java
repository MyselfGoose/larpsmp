package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.wallet.MoneyService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class BukkitBalanceSidebar implements BalanceDisplayControl {
    private final Server server;
    private final MoneyService money;
    private final DisplaySettings settings;
    private final Consumer<String> errorLogger;
    private final Map<UUID, OwnedSidebar> sidebars = new HashMap<>();

    public BukkitBalanceSidebar(
            Server server, MoneyService money, DisplaySettings settings, Consumer<String> errorLogger) {
        this.server = server;
        this.money = money;
        this.settings = settings;
        this.errorLogger = errorLogger;
    }

    @Override
    public boolean isEnabled() {
        return settings.enabled();
    }

    @Override
    public boolean setEnabled(boolean enabled) throws IOException {
        if (settings.enabled() == enabled) {
            return false;
        }
        settings.setEnabled(enabled);
        if (enabled) {
            for (Player player : server.getOnlinePlayers()) {
                refresh(player.getUniqueId());
            }
        } else {
            for (UUID playerId : sidebars.keySet().toArray(UUID[]::new)) {
                remove(playerId);
            }
        }
        return true;
    }

    @Override
    public void refresh(UUID playerId) {
        if (!settings.enabled()) {
            return;
        }
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
                        "larpsmp_balance", Criteria.DUMMY, Component.text("LarpSMP", NamedTextColor.GOLD));
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.getScore("Balance").setScore(2);
                sidebar = new OwnedSidebar(previous, owned, null);
                sidebars.put(playerId, sidebar);
                player.setScoreboard(owned);
            }
            String amount = "$" + account.orElseThrow().balance();
            if (sidebar.amountEntry() != null && !sidebar.amountEntry().equals(amount)) {
                sidebar.owned().resetScores(sidebar.amountEntry());
            }
            sidebar.owned().getObjective("larpsmp_balance").getScore(amount).setScore(1);
            sidebars.put(playerId, new OwnedSidebar(sidebar.previous(), sidebar.owned(), amount));
        } catch (IOException exception) {
            errorLogger.accept("Could not refresh balance display for " + playerId + ": " + exception.getMessage());
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
    public void close() throws IOException {
        for (UUID playerId : sidebars.keySet().toArray(UUID[]::new)) {
            remove(playerId);
        }
        settings.save();
    }

    private record OwnedSidebar(Scoreboard previous, Scoreboard owned, String amountEntry) {
    }
}
