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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * Always-on money + standings HUD as stacked sections inside the single sidebar panel.
 *
 * <p>Minecraft only allows one sidebar per player. This board renders up to three visual
 * panels (Top Teams, Top Players, personal Team Money / Wallet) with dividers, staying
 * within the 15-line client limit via compact single-line rows.
 *
 * <pre>
 * Team Money
 * $12,400
 * ··········
 * Wallet
 * $250
 * ··········
 * TOP TEAMS
 * 1. Blue          $12,400
 * …
 * TOP PLAYERS
 * 1. alice         $1,240
 * </pre>
 */
public final class BukkitBalanceSidebar implements BalanceDisplayControl, OwnedScoreboardAccess {
    private static final String OBJECTIVE_NAME = "larpsmp_money";

    /**
     * Fixed slot table (15 max). Scoreboard team names stay under the {@code sb_} prefix so they
     * never collide with {@link TeamPresenceKeys} nametag teams.
     */
    private static final List<Slot> SLOTS = List.of(
            slot("sb_tm_lbl", "§0§r"),
            slot("sb_tm_amt", "§1§r"),
            slot("sb_d_pers", "§2§r"),
            slot("sb_wl_lbl", "§3§r"),
            slot("sb_wl_amt", "§4§r"),
            slot("sb_d_rank", "§5§r"),
            slot("sb_t_hdr", "§6§r"),
            slot("sb_t_1", "§7§r"),
            slot("sb_t_2", "§8§r"),
            slot("sb_t_3", "§9§r"),
            slot("sb_p_hdr", "§a§r"),
            slot("sb_p_1", "§b§r"),
            slot("sb_p_2", "§c§r"),
            slot("sb_p_3", "§d§r"),
            slot("sb_p_4", "§e§r")
    );

    private final Server server;
    private final MoneyService money;
    private final TeamService teams;
    private final AccountRepository accounts;
    private final HudStandingsSnapshot standings;
    private final Consumer<String> errorLogger;
    private final Map<UUID, OwnedSidebar> sidebars = new HashMap<>();
    private final List<OwnedScoreboardListener> ownedListeners = new CopyOnWriteArrayList<>();

    public BukkitBalanceSidebar(
            Server server,
            MoneyService money,
            TeamService teams,
            AccountRepository accounts,
            Consumer<String> errorLogger
    ) {
        this(server, money, teams, accounts, new HudStandingsSnapshot(money, teams, errorLogger), errorLogger);
    }

    public BukkitBalanceSidebar(
            Server server,
            MoneyService money,
            TeamService teams,
            AccountRepository accounts,
            HudStandingsSnapshot standings,
            Consumer<String> errorLogger
    ) {
        this.server = server;
        this.money = money;
        this.teams = teams;
        this.accounts = accounts;
        this.standings = standings;
        this.errorLogger = errorLogger;
    }

    public HudStandingsSnapshot standings() {
        return standings;
    }

    @Override
    public void forEachOwned(BiConsumer<UUID, Scoreboard> action) {
        for (Map.Entry<UUID, OwnedSidebar> entry : new ArrayList<>(sidebars.entrySet())) {
            action.accept(entry.getKey(), entry.getValue().owned());
        }
    }

    @Override
    public void addOwnedScoreboardListener(OwnedScoreboardListener listener) {
        ownedListeners.add(listener);
        for (Map.Entry<UUID, OwnedSidebar> entry : new ArrayList<>(sidebars.entrySet())) {
            listener.onOwnedBoardReady(entry.getKey(), entry.getValue().owned());
        }
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
                        Component.empty());
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.numberFormat(NumberFormat.blank());
                ensureSlots(owned);
                sidebar = new OwnedSidebar(previous, owned);
                sidebars.put(playerId, sidebar);
                player.setScoreboard(owned);
                for (OwnedScoreboardListener listener : ownedListeners) {
                    listener.onOwnedBoardReady(playerId, owned);
                }
            }
            Objective objective = sidebar.owned().getObjective(OBJECTIVE_NAME);
            if (objective == null) {
                remove(playerId);
                refresh(playerId);
                return;
            }
            objective.displayName(Component.empty());
            objective.numberFormat(NumberFormat.blank());
            ensureSlots(sidebar.owned());

            HudStandingsSnapshot.View view = standings.current();
            @Nullable Long teamWealth = view.showTeamTotals()
                    ? resolveTeamWealth(account.orElseThrow().accountId())
                    : null;
            HudBoardModel model = HudBoardModel.build(
                    view.showTeamTotals(),
                    view.topTeams(),
                    view.topPlayers(),
                    teamWealth,
                    account.orElseThrow().balance());
            paint(sidebar.owned(), objective, model);
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
    public void refreshAccounts(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return;
        }
        for (UUID accountId : accountIds) {
            minecraftUuidOf(accountId).ifPresent(this::refresh);
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

    @Override
    public void refreshAll() {
        standings.invalidate();
        for (UUID playerId : sidebars.keySet().toArray(UUID[]::new)) {
            refresh(playerId);
        }
    }

    /** Invalidate shared rankings and repaint every online sidebar. */
    public void refreshStandings() {
        refreshAll();
    }

    private void paint(Scoreboard scoreboard, Objective objective, HudBoardModel model) {
        List<HudBoardModel.Line> lines = model.lines();
        // Clear all slots first so hidden / shorter layouts do not leave stale rows.
        for (Slot slot : SLOTS) {
            setLine(scoreboard, slot.teamName(), slot.entry(), Component.empty(), Component.empty());
            objective.getScore(slot.entry()).setScore(0);
        }
        // Higher score = higher on screen. Reserve score 15..1 so empty trailing slots stay hidden.
        int score = Math.min(lines.size(), SLOTS.size());
        for (int index = 0; index < lines.size() && index < SLOTS.size(); index++) {
            Slot slot = SLOTS.get(index);
            HudBoardModel.Line line = lines.get(index);
            setLine(scoreboard, slot.teamName(), slot.entry(), line.prefix(), line.suffix());
            objective.getScore(slot.entry()).setScore(score);
            score--;
        }
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

    private static void ensureSlots(Scoreboard scoreboard) {
        for (Slot slot : SLOTS) {
            if (scoreboard.getTeam(slot.teamName()) == null) {
                scoreboard.registerNewTeam(slot.teamName());
            }
        }
    }

    private static void setLine(
            Scoreboard scoreboard,
            String teamName,
            String entry,
            Component prefix,
            Component suffix
    ) {
        org.bukkit.scoreboard.Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            return;
        }
        team.prefix(prefix);
        team.suffix(suffix);
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    private void remove(UUID playerId) {
        OwnedSidebar sidebar = sidebars.remove(playerId);
        if (sidebar != null) {
            for (OwnedScoreboardListener listener : ownedListeners) {
                listener.onOwnedBoardRemoved(playerId);
            }
        }
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

    private static Slot slot(String teamName, String entry) {
        return new Slot(teamName, entry);
    }

    private record Slot(String teamName, String entry) {
    }

    private record OwnedSidebar(Scoreboard previous, Scoreboard owned) {
    }
}
