package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.wallet.MoneyFormats;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Pure description of stacked sidebar lines for one viewer.
 *
 * <p>Personal balances sit on top (classic label / amount blocks). Rankings follow with a
 * divider between team and player boards. Line budget stays ≤15.
 *
 * <pre>
 * Team Money
 * $1,170
 * ··········
 * Wallet
 * $1,170
 * ··········
 * TOP TEAMS
 * 1. Blue          $1,430
 * …
 * ··········
 * TOP PLAYERS
 * 1. alice         $850
 * </pre>
 */
public final class HudBoardModel {
    public static final int TOP_TEAM_SLOTS = 3;
    public static final int TOP_PLAYER_SLOTS = 5;
    public static final int MAX_LINES = 15;

    private final boolean showTeamTotals;
    private final List<TeamSlot> teams;
    private final List<PlayerSlot> players;
    private final @Nullable Long personalTeamWealth;
    private final long personalWallet;

    private HudBoardModel(
            boolean showTeamTotals,
            List<TeamSlot> teams,
            List<PlayerSlot> players,
            @Nullable Long personalTeamWealth,
            long personalWallet
    ) {
        this.showTeamTotals = showTeamTotals;
        this.teams = List.copyOf(teams);
        this.players = List.copyOf(players);
        this.personalTeamWealth = personalTeamWealth;
        this.personalWallet = personalWallet;
    }

    public static HudBoardModel build(
            boolean showTeamTotals,
            List<HudStandingsSnapshot.TopTeamRow> topTeams,
            List<HudStandingsSnapshot.TopPlayerRow> topPlayers,
            @Nullable Long personalTeamWealth,
            long personalWallet
    ) {
        Objects.requireNonNull(topTeams, "topTeams");
        Objects.requireNonNull(topPlayers, "topPlayers");

        List<TeamSlot> teamSlots = new ArrayList<>(TOP_TEAM_SLOTS);
        for (int rank = 1; rank <= TOP_TEAM_SLOTS; rank++) {
            HudStandingsSnapshot.TopTeamRow row = rank <= topTeams.size() ? topTeams.get(rank - 1) : null;
            if (row == null) {
                teamSlots.add(TeamSlot.empty(rank));
            } else {
                teamSlots.add(new TeamSlot(rank, row.displayName(), row.wealth()));
            }
        }

        // Personal + teams + divider between boards leaves room for 3 player rows when visible.
        int playerLimit = showTeamTotals ? 3 : TOP_PLAYER_SLOTS;
        List<PlayerSlot> playerSlots = new ArrayList<>(playerLimit);
        int limit = Math.min(playerLimit, topPlayers.size());
        for (int index = 0; index < limit; index++) {
            HudStandingsSnapshot.TopPlayerRow row = topPlayers.get(index);
            playerSlots.add(new PlayerSlot(row.rank(), row.username(), row.balance()));
        }

        return new HudBoardModel(
                showTeamTotals,
                teamSlots,
                playerSlots,
                showTeamTotals ? personalTeamWealth : null,
                personalWallet);
    }

    public boolean showTeamTotals() {
        return showTeamTotals;
    }

    public List<TeamSlot> teams() {
        return teams;
    }

    public List<PlayerSlot> players() {
        return players;
    }

    public @Nullable Long personalTeamWealth() {
        return personalTeamWealth;
    }

    public long personalWallet() {
        return personalWallet;
    }

    /**
     * Objective title — avoids a blank header row above the board.
     * First personal section heading lives here; its amount is the first score line.
     */
    public Component title() {
        return showTeamTotals ? HudTheme.teamMoneyLabel() : HudTheme.walletLabel();
    }

    /**
     * Ordered visible lines top→bottom (below the objective title).
     * Ranking rows use prefix + suffix for right-aligned money.
     */
    public List<Line> lines() {
        List<Line> result = new ArrayList<>(MAX_LINES);

        if (showTeamTotals) {
            result.add(Line.of("team_money_amount", HudTheme.teamMoneyAmount(personalTeamWealth)));
            result.add(Line.of("div_personal", HudTheme.divider()));
            result.add(Line.of("wallet_label", HudTheme.walletLabel()));
            result.add(Line.of("wallet_amount", HudTheme.walletAmount(personalWallet)));
            result.add(Line.of("div_rankings", HudTheme.divider()));
            result.add(Line.of("teams_header", HudTheme.teamsHeader()));
            for (TeamSlot slot : teams) {
                result.add(Line.ranked(
                        "team_" + slot.rank(),
                        HudTheme.teamNamePrefix(slot.rank(), slot.name()),
                        HudTheme.amountSuffix(slot.wealth())));
            }
            result.add(Line.of("div_boards", HudTheme.divider()));
        } else {
            result.add(Line.of("wallet_amount", HudTheme.walletAmount(personalWallet)));
            result.add(Line.of("div_rankings", HudTheme.divider()));
        }

        result.add(Line.of("players_header", HudTheme.playersHeader()));
        for (PlayerSlot slot : players) {
            result.add(Line.ranked(
                    "player_" + slot.rank(),
                    HudTheme.playerNamePrefix(slot.rank(), slot.name()),
                    HudTheme.amountSuffix(slot.balance())));
        }

        if (result.size() > MAX_LINES) {
            return List.copyOf(result.subList(0, MAX_LINES));
        }
        return List.copyOf(result);
    }

    /** Plain-text lines for tests including the objective title as the first row. */
    public List<String> plainLines() {
        List<String> result = new ArrayList<>();
        if (showTeamTotals) {
            result.add("Team Money");
            result.add(personalTeamWealth == null ? "—" : MoneyFormats.currency(personalTeamWealth));
            result.add("··········");
            result.add("Wallet");
            result.add(MoneyFormats.currency(personalWallet));
            result.add("··········");
            result.add("TOP TEAMS");
            for (TeamSlot slot : teams) {
                result.add(HudTheme.plainTeamRow(slot.rank(), slot.name(), slot.wealth()));
            }
            result.add("··········");
        } else {
            result.add("Wallet");
            result.add(MoneyFormats.currency(personalWallet));
            result.add("··········");
        }
        result.add("TOP PLAYERS");
        if (players.isEmpty()) {
            result.add("(none)");
        } else {
            for (PlayerSlot slot : players) {
                result.add(HudTheme.plainPlayerRow(slot.rank(), slot.name(), slot.balance()));
            }
        }
        return List.copyOf(result);
    }

    public record TeamSlot(int rank, @Nullable String name, @Nullable Long wealth) {
        static TeamSlot empty(int rank) {
            return new TeamSlot(rank, null, null);
        }
    }

    public record PlayerSlot(int rank, @Nullable String name, @Nullable Long balance) {
    }

    public record Line(String id, Component prefix, Component suffix) {
        static Line of(String id, Component prefix) {
            return new Line(id, prefix, Component.empty());
        }

        static Line ranked(String id, Component prefix, Component suffix) {
            return new Line(id, prefix, suffix);
        }
    }
}
