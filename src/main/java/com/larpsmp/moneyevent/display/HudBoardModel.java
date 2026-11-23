package com.larpsmp.moneyevent.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Pure description of stacked sidebar lines for one viewer.
 *
 * <p>Line budget stays ≤14 so the board fits Minecraft’s 15-entry sidebar limit.
 */
public final class HudBoardModel {
    public static final int TOP_TEAM_SLOTS = 3;
    public static final int TOP_PLAYER_SLOTS = 5;

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
                teamSlots.add(new TeamSlot(rank, row.displayName(), row.colorRgb(), row.wealth()));
            }
        }

        List<PlayerSlot> playerSlots = new ArrayList<>(TOP_PLAYER_SLOTS);
        for (int rank = 1; rank <= TOP_PLAYER_SLOTS; rank++) {
            HudStandingsSnapshot.TopPlayerRow row = rank <= topPlayers.size() ? topPlayers.get(rank - 1) : null;
            if (row == null) {
                playerSlots.add(PlayerSlot.empty(rank));
            } else {
                playerSlots.add(new PlayerSlot(rank, row.username(), row.balance()));
            }
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
     * Ordered visible lines for the sidebar (highest score index first conceptually).
     * Empty {@link Line#component()} means the slot should be cleared / score 0.
     */
    public List<Line> lines() {
        List<Line> result = new ArrayList<>(14);
        if (showTeamTotals) {
            result.add(Line.of("teams_header", HudTheme.teamsHeader()));
            for (TeamSlot slot : teams) {
                result.add(Line.of("team_" + slot.rank(),
                        HudTheme.teamRow(slot.rank(), slot.name(), slot.colorRgb(), slot.wealth())));
            }
            result.add(Line.of("div_teams", HudTheme.divider()));
        }
        result.add(Line.of("players_header", HudTheme.playersHeader()));
        for (PlayerSlot slot : players) {
            result.add(Line.of("player_" + slot.rank(),
                    HudTheme.playerRow(slot.rank(), slot.name(), slot.balance())));
        }
        result.add(Line.of("div_players", HudTheme.divider()));
        if (showTeamTotals) {
            result.add(Line.of("team_money", HudTheme.teamMoneyLine(personalTeamWealth)));
        }
        result.add(Line.of("wallet", HudTheme.walletLine(personalWallet)));
        return List.copyOf(result);
    }

    /** Plain-text lines for tests (no Adventure). */
    public List<String> plainLines() {
        List<String> result = new ArrayList<>();
        if (showTeamTotals) {
            result.add("TOP TEAMS");
            for (TeamSlot slot : teams) {
                result.add(HudTheme.plainTeamRow(slot.rank(), slot.name(), slot.wealth()));
            }
            result.add("··········");
        }
        result.add("TOP PLAYERS");
        for (PlayerSlot slot : players) {
            result.add(HudTheme.plainPlayerRow(slot.rank(), slot.name(), slot.balance()));
        }
        result.add("··········");
        if (showTeamTotals) {
            result.add("Team Money " + (personalTeamWealth == null
                    ? "—"
                    : com.larpsmp.moneyevent.wallet.MoneyFormats.currency(personalTeamWealth)));
        }
        result.add("Wallet " + com.larpsmp.moneyevent.wallet.MoneyFormats.currency(personalWallet));
        return List.copyOf(result);
    }

    public record TeamSlot(int rank, @Nullable String name, @Nullable Integer colorRgb, @Nullable Long wealth) {
        static TeamSlot empty(int rank) {
            return new TeamSlot(rank, null, null, null);
        }
    }

    public record PlayerSlot(int rank, @Nullable String name, @Nullable Long balance) {
        static PlayerSlot empty(int rank) {
            return new PlayerSlot(rank, null, null);
        }
    }

    public record Line(String id, Component component) {
        static Line of(String id, Component component) {
            return new Line(id, component);
        }
    }
}
