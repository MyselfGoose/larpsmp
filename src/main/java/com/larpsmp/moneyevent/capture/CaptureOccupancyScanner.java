package com.larpsmp.moneyevent.capture;

import com.larpsmp.moneyevent.auth.AccountIdentity;
import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Counts rostered, eligible players inside the active capture volume.
 */
public final class CaptureOccupancyScanner {

    private final AccountSessionManager sessions;
    private final AccountRepository accounts;
    private final TeamService teams;
    private final Predicate<UUID> isPluginAdmin;
    private final Consumer<String> errorLogger;

    public CaptureOccupancyScanner(
            AccountSessionManager sessions,
            AccountRepository accounts,
            TeamService teams,
            Predicate<UUID> isPluginAdmin,
            Consumer<String> errorLogger) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.isPluginAdmin = Objects.requireNonNull(isPluginAdmin, "isPluginAdmin");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public CaptureAdvantage scan(CapturePointDefinition point, int heightBlocks) {
        Objects.requireNonNull(point, "point");
        if (!point.isRegionDefined()) {
            return CaptureAdvantage.fromHeadcounts(Map.of());
        }
        World world = Bukkit.getWorld(Objects.requireNonNull(point.world()));
        if (world == null) {
            return CaptureAdvantage.fromHeadcounts(Map.of());
        }

        Map<UUID, Integer> headcounts = new HashMap<>();
        for (Player player : world.getPlayers()) {
            if (!CaptureEligibility.counts(player, isPluginAdmin, this::hasTeam)) {
                continue;
            }
            Location location = player.getLocation();
            if (!point.containsOccupant(location.getX(), location.getY(), location.getZ(), heightBlocks)) {
                continue;
            }
            Optional<UUID> teamId = resolveTeamId(player.getUniqueId());
            if (teamId.isEmpty()) {
                continue;
            }
            headcounts.merge(teamId.get(), 1, Integer::sum);
        }
        return CaptureAdvantage.fromHeadcounts(headcounts);
    }

    private boolean hasTeam(UUID minecraftUuid) {
        return resolveTeamId(minecraftUuid).isPresent();
    }

    private Optional<UUID> resolveTeamId(UUID minecraftUuid) {
        Optional<UUID> accountId = resolveAccountId(minecraftUuid);
        if (accountId.isEmpty()) {
            return Optional.empty();
        }
        return teams.teamOf(accountId.get()).map(Team::id);
    }

    private Optional<UUID> resolveAccountId(UUID minecraftUuid) {
        Optional<AccountSession> session = sessions.findByMinecraftUuid(minecraftUuid);
        if (session.isPresent()) {
            return Optional.of(session.get().accountId());
        }
        try {
            return accounts.findIdentityByMinecraftUuid(minecraftUuid).map(AccountIdentity::accountId);
        } catch (SQLException exception) {
            errorLogger.accept("Capture occupancy account lookup failed for " + minecraftUuid + ": "
                    + exception.getMessage());
            return Optional.empty();
        }
    }
}
