package com.larpsmp.moneyevent.team;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Chat/console administration for teams and captains. UI open is player-only.
 */
public final class TeamCommandController {

    private final TeamService teams;
    private final TeamAdminAuthorizer authorizer;
    private final @Nullable Consumer<Player> openHub;

    public TeamCommandController(
            TeamService teams,
            TeamAdminAuthorizer authorizer,
            @Nullable Consumer<Player> openHub
    ) {
        this.teams = teams;
        this.authorizer = authorizer;
        this.openHub = openHub;
    }

    /**
     * @return true if the first argument was a team-related subcommand and was handled
     */
    public boolean handle(CommandSource source, String[] arguments) {
        if (arguments.length == 0) {
            return false;
        }
        String root = arguments[0].toLowerCase(Locale.ROOT);
        return switch (root) {
            case "teams" -> {
                handleOpenUi(source);
                yield true;
            }
            case "team" -> {
                handleTeam(source, arguments);
                yield true;
            }
            case "captain" -> {
                handleCaptain(source, arguments);
                yield true;
            }
            case "tier" -> {
                handleTier(source, arguments);
                yield true;
            }
            default -> false;
        };
    }

    public List<String> tabComplete(CommandSource source, String[] arguments) {
        if (arguments.length == 1) {
            List<String> roots = new ArrayList<>();
            if (authorizer.isAllowed(source, TeamAdminAction.TEAM_OPEN_UI)
                    || authorizer.isAllowed(source, TeamAdminAction.TEAM_VIEW)
                    || authorizer.isAllowed(source, TeamAdminAction.TIER_SET)) {
                roots.add("teams");
                roots.add("team");
                roots.add("captain");
                roots.add("tier");
            }
            return filter(roots, arguments[0]);
        }
        String root = arguments[0].toLowerCase(Locale.ROOT);
        if (root.equals("team")) {
            return tabTeam(source, arguments);
        }
        if (root.equals("captain")) {
            return tabCaptain(source, arguments);
        }
        if (root.equals("tier")) {
            return tabTier(source, arguments);
        }
        return List.of();
    }

    private void handleOpenUi(CommandSource source) {
        if (!allowed(source, TeamAdminAction.TEAM_OPEN_UI)) {
            return;
        }
        Optional<UUID> playerId = source.playerId();
        if (playerId.isEmpty()) {
            source.send("Console cannot open the Teams Admin UI. Use /larp team list.", MessageKind.ERROR);
            return;
        }
        Player player = Bukkit.getPlayer(playerId.get());
        if (player == null) {
            source.send("Player is not online.", MessageKind.ERROR);
            return;
        }
        if (openHub == null) {
            source.send("Teams Admin UI is unavailable.", MessageKind.ERROR);
            return;
        }
        openHub.accept(player);
    }

    private void handleTeam(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.send(teamUsage(), MessageKind.ERROR);
            return;
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> listTeams(source);
            case "create" -> createTeam(source, arguments);
            case "rename" -> renameTeam(source, arguments);
            case "color" -> recolorTeam(source, arguments);
            case "delete" -> deleteTeam(source, arguments);
            case "set" -> assignMember(source, arguments);
            case "unset" -> unsetMember(source, arguments);
            default -> source.send(teamUsage(), MessageKind.ERROR);
        }
    }

    private void handleCaptain(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.send("Usage: /larp captain <set|clear> ...", MessageKind.ERROR);
            return;
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> setCaptain(source, arguments);
            case "clear" -> clearCaptain(source, arguments);
            default -> source.send("Usage: /larp captain <set|clear> ...", MessageKind.ERROR);
        }
    }

    private void handleTier(CommandSource source, String[] arguments) {
        if (arguments.length < 2) {
            source.send("Usage: /larp tier <set|clear|list> ...", MessageKind.ERROR);
            return;
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> setTier(source, arguments);
            case "clear" -> clearTier(source, arguments);
            case "list" -> listTiers(source);
            default -> source.send("Usage: /larp tier <set|clear|list> ...", MessageKind.ERROR);
        }
    }

    private void setTier(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.TIER_SET)) {
            return;
        }
        if (arguments.length != 4) {
            source.send("Usage: /larp tier set <player> <1-4>", MessageKind.ERROR);
            return;
        }
        Account account = requireAccount(source, arguments[2]);
        if (account == null) {
            return;
        }
        Optional<PlayerTier> tier = PlayerTier.fromToken(arguments[3]);
        if (tier.isEmpty()) {
            source.send("Tier must be 1, 2, 3, or 4.", MessageKind.ERROR);
            return;
        }
        sendResult(source, teams.setTier(account.id(), tier.get()));
    }

    private void clearTier(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.TIER_SET)) {
            return;
        }
        if (arguments.length != 3) {
            source.send("Usage: /larp tier clear <player>", MessageKind.ERROR);
            return;
        }
        Account account = requireAccount(source, arguments[2]);
        if (account == null) {
            return;
        }
        sendResult(source, teams.clearTier(account.id()));
    }

    private void listTiers(CommandSource source) {
        if (!authorizer.isAllowed(source, TeamAdminAction.TIER_SET)
                && !authorizer.isAllowed(source, TeamAdminAction.TEAM_VIEW)) {
            source.send("You are not authorized to perform that action.", MessageKind.ERROR);
            return;
        }
        List<JdbcTeamRepository.RosteredCompetitor> competitors = teams.listRosteredCompetitors();
        if (competitors.isEmpty()) {
            source.send("No rostered competitors.", MessageKind.INFO);
            return;
        }
        for (JdbcTeamRepository.RosteredCompetitor competitor : competitors) {
            String tierLabel = competitor.playerTier()
                    .map(PlayerTier::displayName)
                    .orElse("unset");
            source.send(competitor.username() + " · " + competitor.teamDisplayName()
                    + " · " + tierLabel, MessageKind.INFO);
        }
        for (String warning : teams.rosterWarnings()) {
            if (warning.toLowerCase(Locale.ROOT).contains("tier")) {
                source.send("Warn: " + warning, MessageKind.WARNING);
            }
        }
    }

    private void listTeams(CommandSource source) {
        if (!allowed(source, TeamAdminAction.TEAM_VIEW)) {
            return;
        }
        List<Team> teamList = teams.listTeams();
        if (teamList.isEmpty()) {
            source.send("No teams exist.", MessageKind.INFO);
            return;
        }
        for (Team team : teamList) {
            List<TeamMember> members = teams.membersOf(team.id());
            String captain = teams.captainUsername(team).orElse("none");
            String memberNames = members.isEmpty()
                    ? "(empty)"
                    : members.stream().map(TeamMember::username).reduce((a, b) -> a + ", " + b).orElse("");
            source.send(team.displayName() + " [" + team.color().key() + "] captain=" + captain
                    + " members(" + members.size() + "): " + memberNames, MessageKind.INFO);
        }
        for (String warning : teams.rosterWarnings()) {
            source.send("Warn: " + warning, MessageKind.WARNING);
        }
    }

    private void createTeam(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.TEAM_CREATE)) {
            return;
        }
        if (arguments.length < 3) {
            source.send("Usage: /larp team create <name> [color]", MessageKind.ERROR);
            return;
        }
        TeamColor color = TeamColor.BLUE;
        String name;
        if (arguments.length >= 4) {
            Optional<TeamColor> parsed = TeamColor.fromKey(arguments[arguments.length - 1]);
            if (parsed.isPresent()) {
                color = parsed.get();
                name = joinName(arguments, 2, arguments.length - 1);
            } else {
                name = joinName(arguments, 2, arguments.length);
            }
        } else {
            name = arguments[2];
        }
        sendResult(source, teams.createTeam(name, color));
    }

    private void renameTeam(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.TEAM_EDIT)) {
            return;
        }
        if (arguments.length < 4) {
            source.send("Usage: /larp team rename <team> <name>", MessageKind.ERROR);
            return;
        }
        Team team = requireTeam(source, arguments[2]);
        if (team == null) {
            return;
        }
        String name = joinName(arguments, 3, arguments.length);
        sendResult(source, teams.updateTeam(team.id(), name, null));
    }

    private void recolorTeam(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.TEAM_EDIT)) {
            return;
        }
        if (arguments.length != 4) {
            source.send("Usage: /larp team color <team> <color>", MessageKind.ERROR);
            return;
        }
        Team team = requireTeam(source, arguments[2]);
        if (team == null) {
            return;
        }
        Optional<TeamColor> color = TeamColor.fromKey(arguments[3]);
        if (color.isEmpty()) {
            source.send("Unknown color. Try: blue, red, yellow, green, aqua, gold, light_purple, white, gray.",
                    MessageKind.ERROR);
            return;
        }
        sendResult(source, teams.updateTeam(team.id(), null, color.get()));
    }

    private void deleteTeam(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.TEAM_DELETE)) {
            return;
        }
        if (arguments.length != 3) {
            source.send("Usage: /larp team delete <team>", MessageKind.ERROR);
            return;
        }
        Team team = requireTeam(source, arguments[2]);
        if (team == null) {
            return;
        }
        sendResult(source, teams.deleteTeam(team.id()));
    }

    private void assignMember(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.TEAM_ASSIGN)) {
            return;
        }
        if (arguments.length != 4) {
            source.send("Usage: /larp team set <player> <team>", MessageKind.ERROR);
            return;
        }
        Account account = requireAccount(source, arguments[2]);
        if (account == null) {
            return;
        }
        Team team = requireTeam(source, arguments[3]);
        if (team == null) {
            return;
        }
        sendResult(source, teams.assignMember(account.id(), team.id()));
    }

    private void unsetMember(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.TEAM_UNASSIGN)) {
            return;
        }
        if (arguments.length != 3) {
            source.send("Usage: /larp team unset <player>", MessageKind.ERROR);
            return;
        }
        Account account = requireAccount(source, arguments[2]);
        if (account == null) {
            return;
        }
        sendResult(source, teams.removeMember(account.id()));
    }

    private void setCaptain(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.CAPTAIN_SET)) {
            return;
        }
        if (arguments.length != 4) {
            source.send("Usage: /larp captain set <team> <player>", MessageKind.ERROR);
            return;
        }
        Team team = requireTeam(source, arguments[2]);
        if (team == null) {
            return;
        }
        Account account = requireAccount(source, arguments[3]);
        if (account == null) {
            return;
        }
        sendResult(source, teams.setCaptain(team.id(), account.id()));
    }

    private void clearCaptain(CommandSource source, String[] arguments) {
        if (!allowed(source, TeamAdminAction.CAPTAIN_CLEAR)) {
            return;
        }
        if (arguments.length != 3) {
            source.send("Usage: /larp captain clear <team>", MessageKind.ERROR);
            return;
        }
        Team team = requireTeam(source, arguments[2]);
        if (team == null) {
            return;
        }
        sendResult(source, teams.clearCaptain(team.id()));
    }

    private List<String> tabTeam(CommandSource source, String[] arguments) {
        if (arguments.length == 2) {
            List<String> actions = new ArrayList<>();
            if (authorizer.isAllowed(source, TeamAdminAction.TEAM_VIEW)) {
                actions.add("list");
            }
            if (authorizer.isAllowed(source, TeamAdminAction.TEAM_CREATE)) {
                actions.add("create");
            }
            if (authorizer.isAllowed(source, TeamAdminAction.TEAM_EDIT)) {
                actions.add("rename");
                actions.add("color");
            }
            if (authorizer.isAllowed(source, TeamAdminAction.TEAM_DELETE)) {
                actions.add("delete");
            }
            if (authorizer.isAllowed(source, TeamAdminAction.TEAM_ASSIGN)) {
                actions.add("set");
            }
            if (authorizer.isAllowed(source, TeamAdminAction.TEAM_UNASSIGN)) {
                actions.add("unset");
            }
            return filter(actions, arguments[1]);
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        if (arguments.length == 3) {
            return switch (action) {
                case "rename", "color", "delete" -> filter(teamNames(), arguments[2]);
                case "set", "unset" -> filter(accountUsernames(), arguments[2]);
                case "create" -> List.of();
                default -> List.of();
            };
        }
        if (arguments.length == 4) {
            return switch (action) {
                case "set" -> filter(teamNames(), arguments[3]);
                case "color" -> filter(colorKeys(), arguments[3]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> tabCaptain(CommandSource source, String[] arguments) {
        if (arguments.length == 2) {
            List<String> actions = new ArrayList<>();
            if (authorizer.isAllowed(source, TeamAdminAction.CAPTAIN_SET)) {
                actions.add("set");
            }
            if (authorizer.isAllowed(source, TeamAdminAction.CAPTAIN_CLEAR)) {
                actions.add("clear");
            }
            return filter(actions, arguments[1]);
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        if (arguments.length == 3 && (action.equals("set") || action.equals("clear"))) {
            return filter(teamNames(), arguments[2]);
        }
        if (arguments.length == 4 && action.equals("set")) {
            Team team = teams.findTeamByNameOrSlug(arguments[2]).orElse(null);
            if (team == null) {
                return filter(accountUsernames(), arguments[3]);
            }
            List<String> members = teams.membersOf(team.id()).stream().map(TeamMember::username).toList();
            return filter(members, arguments[3]);
        }
        return List.of();
    }

    private List<String> tabTier(CommandSource source, String[] arguments) {
        if (arguments.length == 2) {
            List<String> actions = new ArrayList<>();
            if (authorizer.isAllowed(source, TeamAdminAction.TIER_SET)
                    || authorizer.isAllowed(source, TeamAdminAction.TEAM_VIEW)) {
                actions.add("list");
            }
            if (authorizer.isAllowed(source, TeamAdminAction.TIER_SET)) {
                actions.add("set");
                actions.add("clear");
            }
            return filter(actions, arguments[1]);
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        if (arguments.length == 3 && (action.equals("set") || action.equals("clear"))) {
            return filter(accountUsernames(), arguments[2]);
        }
        if (arguments.length == 4 && action.equals("set")) {
            return filter(List.of("1", "2", "3", "4"), arguments[3]);
        }
        return List.of();
    }

    private @Nullable Team requireTeam(CommandSource source, String token) {
        Optional<Team> team = teams.findTeamByNameOrSlug(token);
        if (team.isEmpty()) {
            source.send("Unknown team '" + token + "'.", MessageKind.ERROR);
            return null;
        }
        return team.get();
    }

    private @Nullable Account requireAccount(CommandSource source, String username) {
        Optional<Account> account = teams.findAccountByUsername(username);
        if (account.isEmpty()) {
            source.send("No registered account named '" + username + "'.", MessageKind.ERROR);
            return null;
        }
        return account.get();
    }

    private boolean allowed(CommandSource source, TeamAdminAction action) {
        if (authorizer.isAllowed(source, action)) {
            return true;
        }
        source.send("You are not authorized to perform that action.", MessageKind.ERROR);
        return false;
    }

    private static void sendResult(CommandSource source, TeamMutationResult result) {
        if (!result.success()) {
            source.send(result.error() == null ? "Team operation failed." : result.error(), MessageKind.ERROR);
            return;
        }
        for (String message : result.messages()) {
            source.send(message, MessageKind.SUCCESS);
        }
        for (String warning : result.warnings()) {
            source.send("Warn: " + warning, MessageKind.WARNING);
        }
    }

    private List<String> teamNames() {
        return teams.listTeams().stream().map(Team::displayName).toList();
    }

    private List<String> accountUsernames() {
        return teams.listUsernames();
    }

    private static List<String> colorKeys() {
        List<String> keys = new ArrayList<>();
        for (TeamColor color : TeamColor.values()) {
            keys.add(color.key());
        }
        return keys;
    }

    private static List<String> filter(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static String joinName(String[] arguments, int from, int toExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < toExclusive; i++) {
            if (i > from) {
                builder.append(' ');
            }
            builder.append(arguments[i]);
        }
        return builder.toString();
    }

    private static String teamUsage() {
        return "Usage: /larp team <list|create|rename|color|delete|set|unset> ...";
    }
}
