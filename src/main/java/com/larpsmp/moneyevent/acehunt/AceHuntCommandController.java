package com.larpsmp.moneyevent.acehunt;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import com.larpsmp.moneyevent.command.PlayerLookup;
import com.larpsmp.moneyevent.command.PlayerLookupResult;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Admin commands for Ace Hunt ({@code /larp acehunt ...}).
 */
public final class AceHuntCommandController {

    private final AceHuntService service;
    private final TeamService teams;
    private final MoneyService money;
    private final PlayerLookup lookup;
    private final Consumer<AceHuntConfig> configPersister;
    private final Runnable configReloader;
    private final Object defineLock = new Object();
    private final Map<String, PendingCorners> pendingCorners = new HashMap<>();

    public AceHuntCommandController(
            AceHuntService service,
            TeamService teams,
            MoneyService money,
            PlayerLookup lookup,
            Consumer<AceHuntConfig> configPersister,
            Runnable configReloader
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.money = Objects.requireNonNull(money, "money");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.configPersister = Objects.requireNonNull(configPersister, "configPersister");
        this.configReloader = Objects.requireNonNull(configReloader, "configReloader");
    }

    public boolean handle(CommandSource source, String[] arguments) {
        if (arguments.length < 1 || !arguments[0].equalsIgnoreCase("acehunt")) {
            return false;
        }
        if (!source.isConsole() && !source.isOperator()) {
            source.send("You are not authorized to perform that action.", MessageKind.ERROR);
            return true;
        }
        if (arguments.length < 2) {
            usage(source);
            return true;
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "ace" -> ace(source, arguments);
            case "hunt" -> hunt(source, arguments);
            case "cycle" -> respond(source, service.cycleAssignments());
            case "define" -> define(source, arguments);
            case "spawn" -> spawn(source, arguments);
            case "y" -> setY(source, arguments);
            case "lockup" -> lockup(source, arguments);
            case "prep" -> respond(source, service.startPrep());
            case "begin" -> respond(source, service.beginHunt());
            case "stop" -> respond(source, service.stop());
            case "status" -> status(source);
            case "reload" -> reload(source);
            default -> usage(source);
        }
        return true;
    }

    public List<String> tabComplete(CommandSource source, String[] arguments) {
        if (!source.isConsole() && !source.isOperator()) {
            return List.of();
        }
        if (arguments.length == 1) {
            return filterPrefix(List.of("acehunt"), arguments[0]);
        }
        if (!arguments[0].equalsIgnoreCase("acehunt")) {
            return List.of();
        }
        if (arguments.length == 2) {
            return filterPrefix(List.of(
                    "ace", "hunt", "cycle", "define", "spawn", "y", "lockup",
                    "prep", "begin", "stop", "status", "reload"), arguments[1]);
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        if (arguments.length == 3) {
            return switch (action) {
                case "ace", "define", "spawn", "y", "hunt" -> filterPrefix(teamTokens(), arguments[2]);
                case "lockup" -> filterPrefix(List.of("pos1", "pos2", "spawn"), arguments[2]);
                default -> List.of();
            };
        }
        if (arguments.length == 4) {
            return switch (action) {
                case "ace" -> arguments[2].equalsIgnoreCase("clear")
                        ? filterPrefix(teamTokens(), arguments[3])
                        : List.of();
                case "define" -> filterPrefix(List.of("pos1", "pos2"), arguments[3]);
                case "spawn" -> filterPrefix(List.of("ace", "hunter"), arguments[3]);
                case "y" -> filterPrefix(List.of("surface", "ceiling"), arguments[3]);
                case "hunt" -> filterPrefix(teamTokens(), arguments[3]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private void ace(CommandSource source, String[] arguments) {
        if (arguments.length >= 3 && arguments[2].equalsIgnoreCase("clear")) {
            if (arguments.length < 4) {
                source.send("Usage: /larp acehunt ace clear <team>", MessageKind.ERROR);
                return;
            }
            Optional<Team> team = teams.findTeamByNameOrSlug(arguments[3]);
            if (team.isEmpty()) {
                source.send("Unknown team '" + arguments[3] + "'.", MessageKind.ERROR);
                return;
            }
            respond(source, service.clearAce(team.get().id()));
            return;
        }
        if (arguments.length < 4) {
            source.send("Usage: /larp acehunt ace <team> <player>", MessageKind.ERROR);
            return;
        }
        Optional<Team> team = teams.findTeamByNameOrSlug(arguments[2]);
        if (team.isEmpty()) {
            source.send("Unknown team '" + arguments[2] + "'.", MessageKind.ERROR);
            return;
        }
        PlayerLookupResult resolved = lookup.resolve(arguments[3]);
        if (resolved.status() != PlayerLookupResult.Status.FOUND || resolved.player() == null) {
            source.send("Unknown player '" + arguments[3] + "'.", MessageKind.ERROR);
            return;
        }
        try {
            Optional<WalletAccount> wallet = money.account(resolved.player().playerId());
            if (wallet.isEmpty()) {
                source.send("That player does not have a registered wallet.", MessageKind.ERROR);
                return;
            }
            respond(source, service.nominateAce(team.get().id(), wallet.get().accountId()));
        } catch (IOException exception) {
            source.send("Could not resolve player wallet.", MessageKind.ERROR);
        }
    }

    private void hunt(CommandSource source, String[] arguments) {
        if (arguments.length < 4) {
            source.send("Usage: /larp acehunt hunt <hunterTeam> <targetTeam>", MessageKind.ERROR);
            return;
        }
        Optional<Team> hunter = teams.findTeamByNameOrSlug(arguments[2]);
        Optional<Team> target = teams.findTeamByNameOrSlug(arguments[3]);
        if (hunter.isEmpty() || target.isEmpty()) {
            source.send("Unknown hunter or target team.", MessageKind.ERROR);
            return;
        }
        respond(source, service.setHuntAssignment(hunter.get().id(), target.get().id()));
    }

    private void define(CommandSource source, String[] arguments) {
        if (arguments.length < 4) {
            source.send("Usage: /larp acehunt define <team> <pos1|pos2>", MessageKind.ERROR);
            return;
        }
        Optional<Team> team = teams.findTeamByNameOrSlug(arguments[2]);
        if (team.isEmpty()) {
            source.send("Unknown team '" + arguments[2] + "'.", MessageKind.ERROR);
            return;
        }
        Location location = resolvePlayerLocation(source);
        if (location == null || location.getWorld() == null) {
            source.send("Define requires an in-game player location.", MessageKind.ERROR);
            return;
        }
        String mode = arguments[3].toLowerCase(Locale.ROOT);
        String key = "territory:" + team.get().id();
        synchronized (defineLock) {
            PendingCorners pending = pendingCorners.computeIfAbsent(key, ignored -> new PendingCorners());
            switch (mode) {
                case "pos1" -> {
                    pending.pos1 = location.clone();
                    source.send("Set territory pos1 for " + team.get().displayName() + " to "
                            + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                case "pos2" -> {
                    pending.pos2 = location.clone();
                    source.send("Set territory pos2 for " + team.get().displayName() + " to "
                            + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                default -> {
                    source.send("Usage: /larp acehunt define <team> <pos1|pos2>", MessageKind.ERROR);
                    return;
                }
            }
            if (pending.pos1 != null && pending.pos2 != null) {
                AceHuntCuboid cuboid = AceHuntCuboid.undefined().withCorners(
                        pending.pos1.getWorld().getName(),
                        pending.pos1.getBlockX(), pending.pos1.getBlockY(), pending.pos1.getBlockZ(),
                        pending.pos2.getBlockX(), pending.pos2.getBlockY(), pending.pos2.getBlockZ());
                AceHuntTerritory territory = ensureTerritory(team.get()).withRegion(cuboid);
                persistTerritory(territory);
                pendingCorners.remove(key);
                source.send("Territory region saved for " + team.get().displayName() + ".", MessageKind.SUCCESS);
            }
        }
    }

    private void spawn(CommandSource source, String[] arguments) {
        if (arguments.length < 4) {
            source.send("Usage: /larp acehunt spawn <team> <ace|hunter>", MessageKind.ERROR);
            return;
        }
        Optional<Team> team = teams.findTeamByNameOrSlug(arguments[2]);
        if (team.isEmpty()) {
            source.send("Unknown team '" + arguments[2] + "'.", MessageKind.ERROR);
            return;
        }
        Location location = resolvePlayerLocation(source);
        if (location == null || location.getWorld() == null) {
            source.send("Spawn requires an in-game player location.", MessageKind.ERROR);
            return;
        }
        AceHuntSpawnPoint spawn = AceHuntSpawnPoint.undefined().at(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
        AceHuntTerritory territory = ensureTerritory(team.get());
        String kind = arguments[3].toLowerCase(Locale.ROOT);
        AceHuntTerritory updated = switch (kind) {
            case "ace" -> territory.withAceSpawn(spawn);
            case "hunter" -> territory.withHunterSpawn(spawn);
            default -> null;
        };
        if (updated == null) {
            source.send("Usage: /larp acehunt spawn <team> <ace|hunter>", MessageKind.ERROR);
            return;
        }
        persistTerritory(updated);
        source.send("Saved " + kind + " spawn for " + team.get().displayName() + ".", MessageKind.SUCCESS);
    }

    private void setY(CommandSource source, String[] arguments) {
        if (arguments.length < 4) {
            source.send("Usage: /larp acehunt y <team> <surface|ceiling> [value]", MessageKind.ERROR);
            return;
        }
        Optional<Team> team = teams.findTeamByNameOrSlug(arguments[2]);
        if (team.isEmpty()) {
            source.send("Unknown team '" + arguments[2] + "'.", MessageKind.ERROR);
            return;
        }
        Location location = resolvePlayerLocation(source);
        String kind = arguments[3].toLowerCase(Locale.ROOT);
        int value;
        if (arguments.length >= 5) {
            try {
                value = Integer.parseInt(arguments[4]);
            } catch (NumberFormatException exception) {
                source.send("Y value must be an integer.", MessageKind.ERROR);
                return;
            }
        } else if (location != null) {
            value = location.getBlockY();
        } else {
            source.send("Provide a Y value or stand in-game.", MessageKind.ERROR);
            return;
        }
        AceHuntTerritory territory = ensureTerritory(team.get());
        AceHuntTerritory updated = switch (kind) {
            case "surface" -> territory.withSurfaceY(value);
            case "ceiling" -> territory.withMaxLegalY(value);
            default -> null;
        };
        if (updated == null) {
            source.send("Usage: /larp acehunt y <team> <surface|ceiling> [value]", MessageKind.ERROR);
            return;
        }
        persistTerritory(updated);
        source.send("Set " + kind + " Y=" + value + " for " + team.get().displayName() + ".",
                MessageKind.SUCCESS);
    }

    private void lockup(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp acehunt lockup <pos1|pos2|spawn>", MessageKind.ERROR);
            return;
        }
        Location location = resolvePlayerLocation(source);
        if (location == null || location.getWorld() == null) {
            source.send("Lockup requires an in-game player location.", MessageKind.ERROR);
            return;
        }
        String mode = arguments[2].toLowerCase(Locale.ROOT);
        if (mode.equals("spawn")) {
            AceHuntSpawnPoint spawn = AceHuntSpawnPoint.undefined().at(
                    location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
            AceHuntConfig updated = service.config().withLockup(service.config().lockup(), spawn);
            service.replaceConfig(updated);
            configPersister.accept(updated);
            source.send("Lockup spawn saved at " + formatLoc(location) + ".", MessageKind.SUCCESS);
            return;
        }
        String key = "lockup";
        synchronized (defineLock) {
            PendingCorners pending = pendingCorners.computeIfAbsent(key, ignored -> new PendingCorners());
            switch (mode) {
                case "pos1" -> {
                    pending.pos1 = location.clone();
                    source.send("Set lockup pos1 to " + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                case "pos2" -> {
                    pending.pos2 = location.clone();
                    source.send("Set lockup pos2 to " + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                default -> {
                    source.send("Usage: /larp acehunt lockup <pos1|pos2|spawn>", MessageKind.ERROR);
                    return;
                }
            }
            if (pending.pos1 != null && pending.pos2 != null) {
                AceHuntCuboid cuboid = AceHuntCuboid.undefined().withCorners(
                        pending.pos1.getWorld().getName(),
                        pending.pos1.getBlockX(), pending.pos1.getBlockY(), pending.pos1.getBlockZ(),
                        pending.pos2.getBlockX(), pending.pos2.getBlockY(), pending.pos2.getBlockZ());
                AceHuntConfig updated = service.config().withLockup(cuboid, service.config().lockupSpawn());
                service.replaceConfig(updated);
                configPersister.accept(updated);
                pendingCorners.remove(key);
                source.send("Lockup region saved.", MessageKind.SUCCESS);
            }
        }
    }

    private void status(CommandSource source) {
        Optional<AceHuntSession> active = service.activeSession();
        source.send("Pending aces: " + service.pendingAces().size()
                + " | assignments: " + service.pendingAssignments().size(), MessageKind.INFO);
        if (active.isEmpty()) {
            source.send("No active Ace Hunt session.", MessageKind.INFO);
            return;
        }
        AceHuntSession session = active.get();
        AceHuntScoreboard board = AceHuntScoreboard.from(session);
        source.send("Active phase=" + session.phase()
                + " session=" + session.sessionId()
                + " livingAces=" + session.livingAceTeams().size(), MessageKind.INFO);
        for (Map.Entry<UUID, Integer> entry : board.totals().entrySet()) {
            String name = teams.getTeam(entry.getKey()).map(Team::displayName).orElse("?");
            source.send("  " + name + ": total=" + entry.getValue()
                    + " hunt=" + board.huntPoints().getOrDefault(entry.getKey(), 0)
                    + " survival=" + board.survivalPoints().getOrDefault(entry.getKey(), 0),
                    MessageKind.INFO);
        }
    }

    private void reload(CommandSource source) {
        configReloader.run();
        // Rebind territory team IDs from current roster.
        Map<String, UUID> slugToId = new HashMap<>();
        for (Team team : teams.listTeams()) {
            slugToId.put(team.slug(), team.id());
        }
        AceHuntConfig rebound = service.config().rebindTeamIds(slugToId);
        service.replaceConfig(rebound);
        configPersister.accept(rebound);
        source.send("Ace Hunt config reloaded.", MessageKind.SUCCESS);
    }

    private AceHuntTerritory ensureTerritory(Team team) {
        return service.config().territoryByTeamId(team.id())
                .or(() -> service.config().territoryBySlug(team.slug()))
                .orElseGet(() -> AceHuntTerritory.empty(team.id(), team.slug()));
    }

    private void persistTerritory(AceHuntTerritory territory) {
        List<AceHuntTerritory> list = new ArrayList<>();
        boolean replaced = false;
        for (AceHuntTerritory existing : service.config().territories()) {
            if (existing.teamId().equals(territory.teamId())
                    || existing.teamSlug().equals(territory.teamSlug())) {
                list.add(territory);
                replaced = true;
            } else {
                list.add(existing);
            }
        }
        if (!replaced) {
            list.add(territory);
        }
        AceHuntConfig updated = service.config().withTerritories(list);
        service.replaceConfig(updated);
        configPersister.accept(updated);
    }

    private void respond(CommandSource source, AceHuntService.AdminResult result) {
        source.send(result.message(), result.success() ? MessageKind.SUCCESS : MessageKind.ERROR);
    }

    private void usage(CommandSource source) {
        source.send(
                "Usage: /larp acehunt <ace|hunt|cycle|define|spawn|y|lockup|prep|begin|stop|status|reload> ...",
                MessageKind.ERROR);
    }

    private List<String> teamTokens() {
        List<String> tokens = new ArrayList<>();
        tokens.add("clear");
        for (Team team : teams.listTeams()) {
            tokens.add(team.slug());
            tokens.add(team.displayName());
        }
        return tokens;
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }

    private static @Nullable Location resolvePlayerLocation(CommandSource source) {
        return source.playerId()
                .map(org.bukkit.Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .map(Player::getLocation)
                .orElse(null);
    }

    private static String formatLoc(Location location) {
        return location.getWorld().getName()
                + " " + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }

    private static final class PendingCorners {
        @Nullable Location pos1;
        @Nullable Location pos2;
    }
}
