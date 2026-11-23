package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.auth.AccountIdentity;
import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * Applies team-colored nametags and tab grouping onto each player's owned scoreboard.
 *
 * <p>Does not own or replace scoreboards — coordinates with {@link BukkitBalanceSidebar}.
 */
public final class TeamPresenceDisplay implements OwnedScoreboardListener {
    private final Server server;
    private final TeamService teams;
    private final AccountRepository accounts;
    private final OwnedScoreboardAccess ownedBoards;
    private final Consumer<String> errorLogger;

    public TeamPresenceDisplay(
            Server server,
            TeamService teams,
            AccountRepository accounts,
            OwnedScoreboardAccess ownedBoards,
            Consumer<String> errorLogger
    ) {
        this.server = server;
        this.teams = teams;
        this.accounts = accounts;
        this.ownedBoards = ownedBoards;
        this.errorLogger = errorLogger;
        ownedBoards.addOwnedScoreboardListener(this);
    }

    @Override
    public void onOwnedBoardReady(UUID playerId, Scoreboard board) {
        syncBoard(board);
    }

    @Override
    public void onOwnedBoardRemoved(UUID playerId) {
        // Board is discarded with the sidebar; nothing to clean up.
    }

    public void refreshAll() {
        ownedBoards.forEachOwned((ignoredPlayerId, board) -> syncBoard(board));
    }

    public void syncBoard(Scoreboard board) {
        Map<String, PresenceSlot> desired = buildDesiredSlots();
        Set<String> requiredTeamNames = new HashSet<>(desired.values().stream()
                .map(PresenceSlot::teamName)
                .toList());
        requiredTeamNames.add(TeamPresenceKeys.UNASSIGNED);

        for (String teamName : requiredTeamNames) {
            ensureTeam(board, teamName, colorForTeamName(teamName, desired));
        }

        Set<String> placedEntries = new HashSet<>();
        for (Map.Entry<String, PresenceSlot> entry : desired.entrySet()) {
            String playerName = entry.getKey();
            PresenceSlot slot = entry.getValue();
            moveEntry(board, playerName, slot.teamName());
            placedEntries.add(playerName);
        }

        for (org.bukkit.scoreboard.Team scoreboardTeam : board.getTeams()) {
            if (!TeamPresenceKeys.isPresenceTeam(scoreboardTeam.getName())) {
                continue;
            }
            for (String entry : Set.copyOf(scoreboardTeam.getEntries())) {
                if (!placedEntries.contains(entry)) {
                    scoreboardTeam.removeEntry(entry);
                }
            }
            if (scoreboardTeam.getEntries().isEmpty()
                    && !scoreboardTeam.getName().equals(TeamPresenceKeys.UNASSIGNED)
                    && !requiredTeamNames.contains(scoreboardTeam.getName())) {
                scoreboardTeam.unregister();
            }
        }
    }

    private Map<String, PresenceSlot> buildDesiredSlots() {
        Map<String, PresenceSlot> desired = new HashMap<>();
        for (Player player : server.getOnlinePlayers()) {
            String entryName = player.getName();
            PresenceSlot slot = resolveSlot(player.getUniqueId());
            desired.put(entryName, slot);
        }
        return desired;
    }

    private PresenceSlot resolveSlot(UUID minecraftUuid) {
        try {
            Optional<AccountIdentity> identity = accounts.findIdentityByMinecraftUuid(minecraftUuid);
            if (identity.isEmpty()) {
                return PresenceSlot.unassigned();
            }
            UUID accountId = identity.get().accountId();
            Optional<Team> team = teams.teamOf(accountId);
            if (team.isEmpty()) {
                return PresenceSlot.unassigned();
            }
            PlayerTier tier = teams.tierOf(accountId).orElse(null);
            return new PresenceSlot(
                    TeamPresenceKeys.forMembership(team.get(), tier),
                    team.get().color());
        } catch (SQLException exception) {
            errorLogger.accept("Could not resolve team presence for " + minecraftUuid + ": "
                    + exception.getMessage());
            return PresenceSlot.unassigned();
        }
    }

    private static NamedTextColor colorForTeamName(String teamName, Map<String, PresenceSlot> desired) {
        if (teamName.equals(TeamPresenceKeys.UNASSIGNED)) {
            return NamedTextColor.GRAY;
        }
        for (PresenceSlot slot : desired.values()) {
            if (slot.teamName().equals(teamName) && slot.color() != null) {
                return namedColor(slot.color());
            }
        }
        return NamedTextColor.WHITE;
    }

    static NamedTextColor namedColor(TeamColor color) {
        TextColor exact = TextColor.color(color.rgb());
        return NamedTextColor.nearestTo(exact);
    }

    private static void ensureTeam(Scoreboard board, String name, NamedTextColor color) {
        org.bukkit.scoreboard.Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        team.color(color);
        team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
        team.setCanSeeFriendlyInvisibles(false);
    }

    private static void moveEntry(Scoreboard board, String entry, String targetTeamName) {
        org.bukkit.scoreboard.Team current = board.getEntryTeam(entry);
        if (current != null && current.getName().equals(targetTeamName)) {
            return;
        }
        if (current != null && TeamPresenceKeys.isPresenceTeam(current.getName())) {
            current.removeEntry(entry);
        }
        org.bukkit.scoreboard.Team target = board.getTeam(targetTeamName);
        if (target == null) {
            return;
        }
        target.addEntry(entry);
    }

    private record PresenceSlot(String teamName, @Nullable TeamColor color) {
        static PresenceSlot unassigned() {
            return new PresenceSlot(TeamPresenceKeys.UNASSIGNED, null);
        }
    }
}
