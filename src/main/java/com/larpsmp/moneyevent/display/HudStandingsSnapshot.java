package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Shared, cacheable top-team / top-player standings for every viewer’s sidebar.
 */
public final class HudStandingsSnapshot {
    private final MoneyService money;
    private final TeamService teams;
    private final Consumer<String> errorLogger;
    private final AtomicLong generation = new AtomicLong();
    private volatile @Nullable Cached cached;

    public HudStandingsSnapshot(MoneyService money, TeamService teams, Consumer<String> errorLogger) {
        this.money = Objects.requireNonNull(money, "money");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void invalidate() {
        generation.incrementAndGet();
        cached = null;
    }

    public View current() {
        long gen = generation.get();
        Cached hit = cached;
        if (hit != null && hit.generation == gen) {
            return hit.view;
        }
        try {
            View view = compute();
            cached = new Cached(gen, view);
            return view;
        } catch (IOException exception) {
            errorLogger.accept("Could not build HUD standings: " + exception.getMessage());
            View empty = View.empty();
            cached = new Cached(gen, empty);
            return empty;
        }
    }

    private View compute() throws IOException {
        boolean showTeamTotals = money.teamTotalsVisible();

        List<TopTeamRow> topTeams = List.of();
        if (showTeamTotals) {
            List<TeamStanding> teamStandings = new ArrayList<>();
            for (Team team : teams.listTeams()) {
                teamStandings.add(new TeamStanding(
                        team.id(),
                        team.displayName(),
                        team.color().rgb(),
                        money.teamWealth(team.id())));
            }
            teamStandings.sort(Comparator.comparingLong(TeamStanding::wealth).reversed()
                    .thenComparing(TeamStanding::displayName, String.CASE_INSENSITIVE_ORDER));

            List<TopTeamRow> ranked = new ArrayList<>(HudBoardModel.TOP_TEAM_SLOTS);
            int teamRank = 1;
            for (TeamStanding standing : teamStandings) {
                if (teamRank > HudBoardModel.TOP_TEAM_SLOTS) {
                    break;
                }
                ranked.add(new TopTeamRow(
                        teamRank,
                        standing.displayName(),
                        standing.colorRgb(),
                        standing.wealth()));
                teamRank++;
            }
            topTeams = List.copyOf(ranked);
        }

        List<WalletAccount> playerStandings = money.competitorStandings();
        List<TopPlayerRow> topPlayers = new ArrayList<>(HudBoardModel.TOP_PLAYER_SLOTS);
        int playerRank = 1;
        for (WalletAccount wallet : playerStandings) {
            if (playerRank > HudBoardModel.TOP_PLAYER_SLOTS) {
                break;
            }
            topPlayers.add(new TopPlayerRow(playerRank, wallet.lastKnownUsername(), wallet.balance()));
            playerRank++;
        }

        return new View(showTeamTotals, topTeams, List.copyOf(topPlayers));
    }

    public record TopTeamRow(int rank, String displayName, int colorRgb, long wealth) {
    }

    public record TopPlayerRow(int rank, String username, long balance) {
    }

    public record View(
            boolean showTeamTotals,
            List<TopTeamRow> topTeams,
            List<TopPlayerRow> topPlayers
    ) {
        static View empty() {
            return new View(true, List.of(), List.of());
        }
    }

    private record TeamStanding(UUID id, String displayName, int colorRgb, long wealth) {
    }

    private record Cached(long generation, View view) {
    }
}
