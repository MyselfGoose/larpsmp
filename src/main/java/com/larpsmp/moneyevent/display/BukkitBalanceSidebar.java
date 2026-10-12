package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.auth.AccountIdentity;
import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamMember;
import com.larpsmp.moneyevent.team.TeamService;
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
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * Always-on money HUD: Team wealth (aqua) above personal Wallet (gold).
 * Score numbers are blanked; unique scoreboard entries avoid amount collisions.
 */
public final class BukkitBalanceSidebar implements BalanceDisplayControl {
    private static final String OBJECTIVE_NAME = "larpsmp_wallet";
    private static final String TEAM_ENTRY = "§a§r";
    private static final String WALLET_ENTRY = "§b§r";
    private static final String SCORE_TEAM_TEAM = "me_team";
    private static final String SCORE_TEAM_WALLET = "me_wallet";

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
                Objective objective = owned.registerNewObjective(
                        OBJECTIVE_NAME,
                        Criteria.DUMMY,
                        Component.text("Wallet", NamedTextColor.GOLD, TextDecoration.BOLD));
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.numberFormat(NumberFormat.blank());
                ensureDisplayTeams(owned);
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
            objective.numberFormat(NumberFormat.blank());
            ensureDisplayTeams(sidebar.owned());

            long personal = account.orElseThrow().balance();
            @Nullable Long teamWealth = resolveTeamWealth(account.orElseThrow().accountId());

            org.bukkit.scoreboard.Team teamLine = sidebar.owned().getTeam(SCORE_TEAM_TEAM);
            org.bukkit.scoreboard.Team walletLine = sidebar.owned().getTeam(SCORE_TEAM_WALLET);
            if (teamLine != null) {
                teamLine.prefix(Component.text(
                        "Team " + (teamWealth == null ? "—" : "$" + teamWealth),
                        NamedTextColor.AQUA));
                if (!teamLine.hasEntry(TEAM_ENTRY)) {
                    teamLine.addEntry(TEAM_ENTRY);
                }
            }
            if (walletLine != null) {
                walletLine.prefix(Component.text("Wallet $" + personal, NamedTextColor.GOLD));
                if (!walletLine.hasEntry(WALLET_ENTRY)) {
                    walletLine.addEntry(WALLET_ENTRY);
                }
            }
            objective.getScore(TEAM_ENTRY).setScore(2);
            objective.getScore(WALLET_ENTRY).setScore(1);
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

    private static void ensureDisplayTeams(Scoreboard scoreboard) {
        if (scoreboard.getTeam(SCORE_TEAM_TEAM) == null) {
            scoreboard.registerNewTeam(SCORE_TEAM_TEAM);
        }
        if (scoreboard.getTeam(SCORE_TEAM_WALLET) == null) {
            scoreboard.registerNewTeam(SCORE_TEAM_WALLET);
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
