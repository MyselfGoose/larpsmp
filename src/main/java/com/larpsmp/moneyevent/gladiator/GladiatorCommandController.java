package com.larpsmp.moneyevent.gladiator;

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
 * Admin commands for Gladiators ({@code /larp gladiators ...}).
 */
public final class GladiatorCommandController {

    private final GladiatorService service;
    private final TeamService teams;
    private final MoneyService money;
    private final PlayerLookup lookup;
    private final Consumer<GladiatorConfig> configPersister;
    private final Runnable configReloader;
    private final Object defineLock = new Object();
    private final Map<String, PendingCorners> pendingCorners = new HashMap<>();

    public GladiatorCommandController(
            GladiatorService service,
            TeamService teams,
            MoneyService money,
            PlayerLookup lookup,
            Consumer<GladiatorConfig> configPersister,
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
        if (arguments.length < 1 || !arguments[0].equalsIgnoreCase("gladiators")) {
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
            case "arena" -> arena(source, arguments);
            case "spawn" -> spawn(source, arguments);
            case "barriers" -> barriers(source, arguments);
            case "kit" -> kit(source, arguments);
            case "open" -> respond(source, service.open());
            case "stop" -> respond(source, service.stop());
            case "schedule" -> schedule(source);
            case "lineup" -> lineup(source, arguments);
            case "fight" -> fight(source, arguments);
            case "winner" -> winner(source, arguments);
            case "duel" -> duel(source, arguments);
            case "ban" -> ban(source, arguments);
            case "quota" -> quota(source, arguments);
            case "points" -> points(source);
            case "payout" -> respond(source, service.payout());
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
            return filterPrefix(List.of("gladiators"), arguments[0]);
        }
        if (!arguments[0].equalsIgnoreCase("gladiators")) {
            return List.of();
        }
        if (arguments.length == 2) {
            return filterPrefix(List.of(
                    "arena", "spawn", "barriers", "kit", "open", "stop", "schedule", "lineup",
                    "fight", "winner", "duel", "ban", "quota", "points", "payout", "status", "reload"
            ), arguments[1]);
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        if (arguments.length == 3) {
            return switch (action) {
                case "arena" -> filterPrefix(List.of("define"), arguments[2]);
                case "spawn" -> filterPrefix(combine(teamSlugs(), List.of("duel", "seats")), arguments[2]);
                case "barriers" -> filterPrefix(List.of("fill", "clear", "status"), arguments[2]);
                case "kit" -> filterPrefix(List.of("save", "clear", "list"), arguments[2]);
                case "lineup" -> filterPrefix(combine(teamSlugs(), List.of("clear")), arguments[2]);
                case "fight" -> filterPrefix(List.of("start", "skip", "cancel"), arguments[2]);
                case "winner" -> filterPrefix(teamSlugs(), arguments[2]);
                case "duel" -> filterPrefix(combine(List.of("cancel"), playerNames()), arguments[2]);
                case "ban" -> filterPrefix(playerNames(), arguments[2]);
                case "quota" -> filterPrefix(combine(List.of("set"), playerNames()), arguments[2]);
                default -> List.of();
            };
        }
        if (arguments.length == 4 && action.equals("arena") && arguments[2].equalsIgnoreCase("define")) {
            return filterPrefix(List.of("arena", "seats", "barriers"), arguments[3]);
        }
        if (arguments.length == 5 && action.equals("arena") && arguments[2].equalsIgnoreCase("define")) {
            return filterPrefix(List.of("pos1", "pos2"), arguments[4]);
        }
        if (arguments.length == 4 && action.equals("spawn") && !arguments[2].equalsIgnoreCase("duel")
                && !arguments[2].equalsIgnoreCase("seats")) {
            return filterPrefix(List.of("1", "2", "3"), arguments[3]);
        }
        if (arguments.length == 4 && action.equals("spawn") && arguments[2].equalsIgnoreCase("duel")) {
            return filterPrefix(List.of("a", "b"), arguments[3]);
        }
        if (arguments.length == 4 && action.equals("kit")) {
            return filterPrefix(List.of("team", "1", "2", "3", "4", "5"), arguments[3]);
        }
        return List.of();
    }

    private void arena(CommandSource source, String[] arguments) {
        if (arguments.length < 5 || !arguments[2].equalsIgnoreCase("define")) {
            source.send("Usage: /larp gladiators arena define <arena|seats|barriers> <pos1|pos2>",
                    MessageKind.ERROR);
            return;
        }
        Location location = resolvePlayerLocation(source);
        if (location == null || location.getWorld() == null) {
            source.send("Define requires an in-game player location.", MessageKind.ERROR);
            return;
        }
        String region = arguments[3].toLowerCase(Locale.ROOT);
        String mode = arguments[4].toLowerCase(Locale.ROOT);
        if (!List.of("arena", "seats", "barriers").contains(region)) {
            source.send("Region must be arena, seats, or barriers.", MessageKind.ERROR);
            return;
        }
        synchronized (defineLock) {
            PendingCorners pending = pendingCorners.computeIfAbsent(region, ignored -> new PendingCorners());
            switch (mode) {
                case "pos1" -> {
                    pending.pos1 = location.clone();
                    source.send("Set " + region + " pos1 to " + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                case "pos2" -> {
                    pending.pos2 = location.clone();
                    source.send("Set " + region + " pos2 to " + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                default -> {
                    source.send("Usage: /larp gladiators arena define <arena|seats|barriers> <pos1|pos2>",
                            MessageKind.ERROR);
                    return;
                }
            }
            if (pending.pos1 != null && pending.pos2 != null) {
                GladiatorCuboid cuboid = GladiatorCuboid.undefined().withCorners(
                        pending.pos1.getWorld().getName(),
                        pending.pos1.getBlockX(), pending.pos1.getBlockY(), pending.pos1.getBlockZ(),
                        pending.pos2.getBlockX(), pending.pos2.getBlockY(), pending.pos2.getBlockZ());
                GladiatorConfig updated = switch (region) {
                    case "arena" -> service.config().withArena(cuboid);
                    case "seats" -> service.config().withSeats(cuboid);
                    case "barriers" -> service.config().withBarriers(cuboid);
                    default -> service.config();
                };
                service.replaceConfig(updated);
                configPersister.accept(updated);
                pendingCorners.remove(region);
                source.send(region + " region saved.", MessageKind.SUCCESS);
            }
        }
    }

    private void spawn(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp gladiators spawn <team> <1|2|3> | duel <a|b> | seats",
                    MessageKind.ERROR);
            return;
        }
        Location location = resolvePlayerLocation(source);
        if (location == null || location.getWorld() == null) {
            source.send("Spawn requires an in-game player location.", MessageKind.ERROR);
            return;
        }
        GladiatorSpawnPoint spawn = GladiatorSpawnPoint.undefined().at(
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
        String kind = arguments[2].toLowerCase(Locale.ROOT);
        if (kind.equals("seats")) {
            GladiatorConfig updated = service.config().withSeatsSpawn(spawn);
            service.replaceConfig(updated);
            configPersister.accept(updated);
            source.send("Seats spawn saved at " + formatLoc(location) + ".", MessageKind.SUCCESS);
            return;
        }
        if (kind.equals("duel")) {
            if (arguments.length < 4) {
                source.send("Usage: /larp gladiators spawn duel <a|b>", MessageKind.ERROR);
                return;
            }
            String side = arguments[3].toLowerCase(Locale.ROOT);
            GladiatorConfig current = service.config();
            GladiatorConfig updated = switch (side) {
                case "a" -> current.withDuelSpawns(spawn, current.duelSpawnB());
                case "b" -> current.withDuelSpawns(current.duelSpawnA(), spawn);
                default -> null;
            };
            if (updated == null) {
                source.send("Usage: /larp gladiators spawn duel <a|b>", MessageKind.ERROR);
                return;
            }
            service.replaceConfig(updated);
            configPersister.accept(updated);
            source.send("Duel spawn " + side + " saved.", MessageKind.SUCCESS);
            return;
        }
        Optional<Team> team = teams.findTeamByNameOrSlug(arguments[2]);
        if (team.isEmpty()) {
            source.send("Unknown team.", MessageKind.ERROR);
            return;
        }
        if (arguments.length < 4) {
            source.send("Usage: /larp gladiators spawn <team> <1|2|3>", MessageKind.ERROR);
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(arguments[3]);
        } catch (NumberFormatException exception) {
            source.send("Slot must be 1, 2, or 3.", MessageKind.ERROR);
            return;
        }
        try {
            GladiatorConfig updated = service.config().withTeamSpawn(team.get().slug(), slot, spawn);
            service.replaceConfig(updated);
            configPersister.accept(updated);
            source.send("Team spawn " + team.get().displayName() + " #" + slot + " saved.", MessageKind.SUCCESS);
        } catch (IllegalArgumentException exception) {
            source.send(exception.getMessage(), MessageKind.ERROR);
        }
    }

    private void barriers(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp gladiators barriers <fill|clear|status>", MessageKind.ERROR);
            return;
        }
        switch (arguments[2].toLowerCase(Locale.ROOT)) {
            case "fill" -> respond(source, service.fillBarriers());
            case "clear" -> respond(source, service.clearBarriers());
            case "status" -> {
                GladiatorCuboid region = service.config().barriers();
                if (!region.isDefined()) {
                    source.send("Barriers region undefined.", MessageKind.INFO);
                } else {
                    source.send("Barriers region " + region.world()
                            + " volume=" + region.blockVolume()
                            + " (max " + GladiatorRules.MAX_BARRIER_BLOCKS + ").", MessageKind.INFO);
                }
            }
            default -> source.send("Usage: /larp gladiators barriers <fill|clear|status>", MessageKind.ERROR);
        }
    }

    private void kit(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp gladiators kit <save|clear|list> [team|1..5]", MessageKind.ERROR);
            return;
        }
        String op = arguments[2].toLowerCase(Locale.ROOT);
        if (op.equals("list")) {
            List<String> kits = service.listKitIds();
            source.send("Saved kits: " + (kits.isEmpty() ? "(none)" : String.join(", ", kits)),
                    MessageKind.INFO);
            return;
        }
        if (arguments.length < 4) {
            source.send("Usage: /larp gladiators kit <save|clear> <team|1..5>", MessageKind.ERROR);
            return;
        }
        String kitId = arguments[3];
        if (op.equals("clear")) {
            respond(source, service.clearKit(kitId));
            return;
        }
        if (op.equals("save")) {
            UUID resolved = resolveSourceAccountId(source);
            if (resolved == null) {
                source.send("Kit save requires an in-game authenticated player.", MessageKind.ERROR);
                return;
            }
            respond(source, service.saveKitFromPlayer(resolved, kitId));
            return;
        }
        source.send("Usage: /larp gladiators kit <save|clear|list> [team|1..5]", MessageKind.ERROR);
    }

    private void schedule(CommandSource source) {
        Optional<GladiatorSession> session = service.activeSession();
        if (session.isEmpty()) {
            source.send("No Gladiators session.", MessageKind.INFO);
            return;
        }
        for (GladiatorScheduledEntry entry : session.get().schedule()) {
            String a = teams.getTeam(entry.teamAId()).map(Team::displayName).orElse("?");
            String b = teams.getTeam(entry.teamBId()).map(Team::displayName).orElse("?");
            source.send("#" + (entry.boutIndex() + 1) + " " + a + " vs " + b + " [" + entry.status() + "]",
                    MessageKind.INFO);
        }
    }

    private void lineup(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp gladiators lineup <team> <p1> <p2> <p3> | clear <team>",
                    MessageKind.ERROR);
            return;
        }
        if (arguments[2].equalsIgnoreCase("clear")) {
            if (arguments.length < 4) {
                source.send("Usage: /larp gladiators lineup clear <team>", MessageKind.ERROR);
                return;
            }
            Optional<Team> team = teams.findTeamByNameOrSlug(arguments[3]);
            if (team.isEmpty()) {
                source.send("Unknown team.", MessageKind.ERROR);
                return;
            }
            respond(source, service.clearLineup(team.get().id()));
            return;
        }
        if (arguments.length < 6) {
            source.send("Usage: /larp gladiators lineup <team> <p1> <p2> <p3>", MessageKind.ERROR);
            return;
        }
        Optional<Team> team = teams.findTeamByNameOrSlug(arguments[2]);
        if (team.isEmpty()) {
            source.send("Unknown team.", MessageKind.ERROR);
            return;
        }
        List<UUID> accounts = new ArrayList<>();
        for (int i = 3; i <= 5; i++) {
            Optional<UUID> accountId = resolveAccount(arguments[i]);
            if (accountId.isEmpty()) {
                source.send("Unknown player: " + arguments[i], MessageKind.ERROR);
                return;
            }
            accounts.add(accountId.get());
        }
        respond(source, service.setLineup(team.get().id(), accounts));
    }

    private void fight(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp gladiators fight <start|skip|cancel>", MessageKind.ERROR);
            return;
        }
        switch (arguments[2].toLowerCase(Locale.ROOT)) {
            case "start" -> respond(source, service.startFight());
            case "skip" -> respond(source, service.skipFight());
            case "cancel" -> respond(source, service.cancelFight());
            default -> source.send("Usage: /larp gladiators fight <start|skip|cancel>", MessageKind.ERROR);
        }
    }

    private void winner(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp gladiators winner <team>", MessageKind.ERROR);
            return;
        }
        Optional<Team> team = teams.findTeamByNameOrSlug(arguments[2]);
        if (team.isEmpty()) {
            source.send("Unknown team.", MessageKind.ERROR);
            return;
        }
        respond(source, service.forceWinner(team.get().id()));
    }

    private void duel(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp gladiators duel <playerA> <playerB> | cancel", MessageKind.ERROR);
            return;
        }
        if (arguments[2].equalsIgnoreCase("cancel")) {
            respond(source, service.cancelDuel());
            return;
        }
        if (arguments.length < 4) {
            source.send("Usage: /larp gladiators duel <playerA> <playerB>", MessageKind.ERROR);
            return;
        }
        Optional<UUID> a = resolveAccount(arguments[2]);
        Optional<UUID> b = resolveAccount(arguments[3]);
        if (a.isEmpty() || b.isEmpty()) {
            source.send("Unknown player.", MessageKind.ERROR);
            return;
        }
        respond(source, service.nominateDuel(a.get(), b.get()));
    }

    private void ban(CommandSource source, String[] arguments) {
        if (arguments.length < 4) {
            source.send("Usage: /larp gladiators ban <player> <kit>", MessageKind.ERROR);
            return;
        }
        Optional<UUID> accountId = resolveAccount(arguments[2]);
        if (accountId.isEmpty()) {
            source.send("Unknown player.", MessageKind.ERROR);
            return;
        }
        respond(source, service.banKit(accountId.get(), arguments[3]));
    }

    private void quota(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp gladiators quota [set] <player> [n]", MessageKind.ERROR);
            return;
        }
        int offset = arguments[2].equalsIgnoreCase("set") ? 3 : 2;
        if (arguments.length < offset + 1) {
            source.send("Usage: /larp gladiators quota [set] <player> [n]", MessageKind.ERROR);
            return;
        }
        Optional<UUID> accountId = resolveAccount(arguments[offset]);
        if (accountId.isEmpty()) {
            source.send("Unknown player.", MessageKind.ERROR);
            return;
        }
        Optional<GladiatorSession> session = service.activeSession();
        if (arguments.length < offset + 2) {
            int used = session.map(s -> s.fightsUsed(accountId.get())).orElse(0);
            source.send(arguments[offset] + " fights used: " + used + "/" + GladiatorRules.MAX_FIGHTS,
                    MessageKind.INFO);
            return;
        }
        int value;
        try {
            value = Integer.parseInt(arguments[offset + 1]);
        } catch (NumberFormatException exception) {
            source.send("Quota must be an integer.", MessageKind.ERROR);
            return;
        }
        respond(source, service.setQuota(accountId.get(), value));
    }

    private void points(CommandSource source) {
        Optional<GladiatorSession> session = service.activeSession();
        if (session.isEmpty()) {
            source.send("No Gladiators session.", MessageKind.INFO);
            return;
        }
        Map<UUID, Integer> points = session.get().pointsByTeam();
        Map<UUID, Integer> places = GladiatorPlacements.placesFromPoints(points);
        for (Map.Entry<UUID, Integer> entry : points.entrySet()) {
            String name = teams.getTeam(entry.getKey()).map(Team::displayName).orElse("?");
            int place = places.getOrDefault(entry.getKey(), 0) + 1;
            source.send(name + ": " + entry.getValue() + " pts (place " + place + ")", MessageKind.INFO);
        }
    }

    private void status(CommandSource source) {
        Optional<GladiatorSession> session = service.activeSession();
        GladiatorConfig config = service.config();
        source.send("Arena defined=" + config.arena().isDefined()
                + " seats=" + config.seats().isDefined()
                + " barriers=" + config.barriers().isDefined()
                + " seatsSpawn=" + config.seatsSpawn().isDefined(), MessageKind.INFO);
        source.send("Saved kits: " + String.join(", ", service.listKitIds()), MessageKind.INFO);
        if (session.isEmpty()) {
            source.send("No Gladiators session.", MessageKind.INFO);
            return;
        }
        GladiatorSession active = session.get();
        source.send("Session phase=" + active.phase()
                + " id=" + active.sessionId()
                + " payout=" + active.payoutCompleted(), MessageKind.INFO);
        active.currentPendingBout().ifPresent(bout -> {
            String a = teams.getTeam(bout.teamAId()).map(Team::displayName).orElse("?");
            String b = teams.getTeam(bout.teamBId()).map(Team::displayName).orElse("?");
            source.send("Next bout #" + (bout.boutIndex() + 1) + ": " + a + " vs " + b, MessageKind.INFO);
        });
    }

    private void reload(CommandSource source) {
        configReloader.run();
        source.send("Gladiators config reloaded.", MessageKind.SUCCESS);
    }

    private void respond(CommandSource source, GladiatorService.AdminResult result) {
        source.send(result.message(), result.success() ? MessageKind.SUCCESS : MessageKind.ERROR);
    }

    private void usage(CommandSource source) {
        source.send(
                "Usage: /larp gladiators <arena|spawn|barriers|kit|open|stop|schedule|lineup|fight|"
                        + "winner|duel|ban|quota|points|payout|status|reload> ...",
                MessageKind.ERROR);
    }

    private Optional<UUID> resolveAccount(String token) {
        PlayerLookupResult resolved = lookup.resolve(token);
        if (resolved.status() != PlayerLookupResult.Status.FOUND || resolved.player() == null) {
            return Optional.empty();
        }
        try {
            return money.account(resolved.player().playerId()).map(WalletAccount::accountId);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private @Nullable UUID resolveSourceAccountId(CommandSource source) {
        Optional<UUID> minecraftUuid = source.playerId();
        if (minecraftUuid.isEmpty()) {
            return null;
        }
        try {
            return money.account(minecraftUuid.get()).map(WalletAccount::accountId).orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private List<String> teamSlugs() {
        List<String> tokens = new ArrayList<>();
        for (Team team : teams.listTeams()) {
            tokens.add(team.slug());
        }
        return tokens;
    }

    private List<String> playerNames() {
        return lookup.suggestions();
    }

    private static List<String> combine(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
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
