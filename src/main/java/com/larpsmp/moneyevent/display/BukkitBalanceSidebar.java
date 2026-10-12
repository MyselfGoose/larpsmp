package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.auth.AccountIdentity;
import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamMember;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyFormats;
import com.larpsmp.moneyevent.wallet.MoneyService;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * Always-on money HUD as two stacked sections inside the sidebar panel.
 *
 * <p>Minecraft only allows one sidebar panel per player, so Team Money and Wallet are
 * rendered as two visually separated blocks (label above, amount below) with a divider —
 * not one combined {@code Team $N / Wallet $N} row.
 *
 * <pre>
 * Team Money
 * $2,601
 * ··········
 * Wallet
 * $250
 * </pre>
 */
public final class BukkitBalanceSidebar implements BalanceDisplayControl {
    private static final String OBJECTIVE_NAME = "larpsmp_money";

    /** Stable unique scoreboard entries (never shown; prefixes carry the visible text). */
    private static final String ENTRY_TEAM_LABEL = "§0§r";
    private static final String ENTRY_TEAM_AMOUNT = "§1§r";
    private static final String ENTRY_DIVIDER = "§2§r";
    private static final String ENTRY_WALLET_LABEL = "§3§r";
    private static final String ENTRY_WALLET_AMOUNT = "§4§r";

    private static final String TEAM_LABEL = "sb_tm_lbl";
    private static final String TEAM_AMOUNT = "sb_tm_amt";
    private static final String DIVIDER = "sb_div";
    private static final String WALLET_LABEL = "sb_wl_lbl";
    private static final String WALLET_AMOUNT = "sb_wl_amt";

    private static final TextColor TEAM_COLOR = NamedTextColor.AQUA;
    private static final TextColor WALLET_COLOR = NamedTextColor.GOLD;
    private static final TextColor DIVIDER_COLOR = TextColor.color(0x555555);

    private final Server server;
    private final MoneyService money;
    private final TeamService teams;
    private final AccountRepository accounts;
    private final Consumer<String> errorLogger;
    private final Map<UUID, OwnedSidebar> sidebars = new HashMap<>();

    public BukkitBalanceSidebar(
            Server server,
            MoneyService money,
            TeamService teams,
            AccountRepository accounts,
            Consumer<String> errorLogger
    ) {
        this.server = server;
        this.money = money;
        this.teams = teams;
        this.accounts = accounts;
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
                // Empty title so the panel is not branded as a single "Wallet" box —
                // each section carries its own label line instead.
                Objective objective = owned.registerNewObjective(
                        OBJECTIVE_NAME,
                        Criteria.DUMMY,
                        Component.empty());
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.numberFormat(NumberFormat.blank());
                ensureLines(owned);
                sidebar = new OwnedSidebar(previous, owned);
                sidebars.put(playerId, sidebar);
                player.setScoreboard(owned);
            }
            Objective objective = sidebar.owned().getObjective(OBJECTIVE_NAME);
            if (objective == null) {
                remove(playerId);
                refresh(playerId);
                return;
            }
            // Migrate older objective titles if the player still has a prior board.
            objective.displayName(Component.empty());
            objective.numberFormat(NumberFormat.blank());
            ensureLines(sidebar.owned());

            long personal = account.orElseThrow().balance();
            @Nullable Long teamWealth = resolveTeamWealth(account.orElseThrow().accountId());

            setLine(sidebar.owned(), TEAM_LABEL, ENTRY_TEAM_LABEL,
                    Component.text("Team Money", TEAM_COLOR, TextDecoration.BOLD));
            setLine(sidebar.owned(), TEAM_AMOUNT, ENTRY_TEAM_AMOUNT,
                    Component.text(MoneyFormats.currencyOrDash(teamWealth), TEAM_COLOR));
            setLine(sidebar.owned(), DIVIDER, ENTRY_DIVIDER,
                    Component.text("··········", DIVIDER_COLOR));
            setLine(sidebar.owned(), WALLET_LABEL, ENTRY_WALLET_LABEL,
                    Component.text("Wallet", WALLET_COLOR, TextDecoration.BOLD));
            setLine(sidebar.owned(), WALLET_AMOUNT, ENTRY_WALLET_AMOUNT,
                    Component.text(MoneyFormats.currency(personal), WALLET_COLOR));

            // Higher score = higher on screen. Two blocks separated by the divider.
            objective.getScore(ENTRY_TEAM_LABEL).setScore(5);
            objective.getScore(ENTRY_TEAM_AMOUNT).setScore(4);
            objective.getScore(ENTRY_DIVIDER).setScore(3);
            objective.getScore(ENTRY_WALLET_LABEL).setScore(2);
            objective.getScore(ENTRY_WALLET_AMOUNT).setScore(1);
        } catch (IOException exception) {
            errorLogger.accept("Could not refresh wallet display for " + playerId + ": " + exception.getMessage());
        }
    }

    @Override
    public void refreshTeam(UUID teamId) {
        for (TeamMember member : teams.membersOf(teamId)) {
            Optional<UUID> minecraftUuid = minecraftUuidOf(member.accountId());
            minecraftUuid.ifPresent(this::refresh);
        }
    }

    @Override
    public void refreshPlayerAndTeammates(UUID playerId) {
        refresh(playerId);
        try {
            var account = money.account(playerId);
            if (account.isEmpty()) {
                return;
            }
            Optional<Team> team = teams.teamOf(account.get().accountId());
            if (team.isEmpty()) {
                return;
            }
            for (TeamMember member : teams.membersOf(team.get().id())) {
                Optional<UUID> teammateId = minecraftUuidOf(member.accountId());
                if (teammateId.isPresent() && !teammateId.get().equals(playerId)) {
                    refresh(teammateId.get());
                }
            }
        } catch (IOException exception) {
            errorLogger.accept("Could not refresh teammates for " + playerId + ": " + exception.getMessage());
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

    private @Nullable Long resolveTeamWealth(UUID accountId) throws IOException {
        Optional<Team> team = teams.teamOf(accountId);
        if (team.isEmpty()) {
            return null;
        }
        return money.teamWealth(team.get().id());
    }

    private Optional<UUID> minecraftUuidOf(UUID accountId) {
        try {
            return accounts.findIdentityByAccountId(accountId).map(AccountIdentity::minecraftUuid);
        } catch (SQLException exception) {
            errorLogger.accept("Could not resolve Minecraft UUID for account " + accountId + ": "
                    + exception.getMessage());
            return Optional.empty();
        }
    }

    private static void ensureLines(Scoreboard scoreboard) {
        ensureTeam(scoreboard, TEAM_LABEL);
        ensureTeam(scoreboard, TEAM_AMOUNT);
        ensureTeam(scoreboard, DIVIDER);
        ensureTeam(scoreboard, WALLET_LABEL);
        ensureTeam(scoreboard, WALLET_AMOUNT);
    }

    private static void ensureTeam(Scoreboard scoreboard, String name) {
        if (scoreboard.getTeam(name) == null) {
            scoreboard.registerNewTeam(name);
        }
    }

    private static void setLine(Scoreboard scoreboard, String teamName, String entry, Component text) {
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            return;
        }
        team.prefix(text);
        team.suffix(Component.empty());
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
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

    private record OwnedSidebar(Scoreboard previous, Scoreboard owned) {
    }
}
