package com.larpsmp.moneyevent.spawn;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Admin commands for spawn protection ({@code /larp spawn ...}).
 */
public final class SpawnCommandController {

    private final SpawnService spawnService;
    private final Consumer<SpawnConfig> configPersister;
    private final Runnable configReloader;
    private final Object defineLock = new Object();
    private final PendingDefine pendingDefine = new PendingDefine();

    public SpawnCommandController(
            SpawnService spawnService,
            Consumer<SpawnConfig> configPersister,
            Runnable configReloader) {
        this.spawnService = Objects.requireNonNull(spawnService, "spawnService");
        this.configPersister = Objects.requireNonNull(configPersister, "configPersister");
        this.configReloader = Objects.requireNonNull(configReloader, "configReloader");
    }

    public boolean handle(CommandSource source, String[] arguments) {
        if (arguments.length < 1 || !arguments[0].equalsIgnoreCase("spawn")) {
            return false;
        }
        if (!source.isConsole() && !source.isOperator()) {
            source.send("You are not authorized to perform that action.", MessageKind.ERROR);
            return true;
        }
        if (arguments.length < 2) {
            source.send("Usage: /larp spawn <define|status|reload> ...", MessageKind.ERROR);
            return true;
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "define" -> define(source, arguments);
            case "status" -> status(source);
            case "reload" -> reload(source);
            default -> source.send("Usage: /larp spawn <define|status|reload> ...", MessageKind.ERROR);
        }
        return true;
    }

    public List<String> tabComplete(CommandSource source, String[] arguments) {
        if (!source.isConsole() && !source.isOperator()) {
            return List.of();
        }
        if (arguments.length == 1) {
            return List.of("spawn");
        }
        if (!arguments[0].equalsIgnoreCase("spawn")) {
            return List.of();
        }
        if (arguments.length == 2) {
            return filterPrefix(List.of("define", "status", "reload"), arguments[1]);
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("define")) {
            return filterPrefix(List.of("pos1", "pos2", "here"), arguments[2]);
        }
        return List.of();
    }

    private void define(CommandSource source, String[] arguments) {
        if (arguments.length < 3) {
            source.send("Usage: /larp spawn define <pos1|pos2|here>", MessageKind.ERROR);
            return;
        }
        Location location = resolvePlayerLocation(source);
        if (location == null || location.getWorld() == null) {
            source.send("Define requires an in-game player location.", MessageKind.ERROR);
            return;
        }
        String mode = arguments[2].toLowerCase(Locale.ROOT);
        synchronized (defineLock) {
            switch (mode) {
                case "pos1" -> {
                    pendingDefine.pos1 = location.clone();
                    source.send("Set spawn pos1 to " + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                case "pos2" -> {
                    pendingDefine.pos2 = location.clone();
                    source.send("Set spawn pos2 to " + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                case "here" -> {
                    pendingDefine.pos1 = location.clone();
                    pendingDefine.pos2 = location.clone();
                    source.send("Set single-block spawn region at " + formatLoc(location) + ".",
                            MessageKind.SUCCESS);
                }
                default -> {
                    source.send("Usage: /larp spawn define <pos1|pos2|here>", MessageKind.ERROR);
                    return;
                }
            }
            if (pendingDefine.pos1 != null && pendingDefine.pos2 != null) {
                applyDefine(source, pendingDefine.pos1, pendingDefine.pos2);
                pendingDefine.pos1 = null;
                pendingDefine.pos2 = null;
            }
        }
    }

    private void applyDefine(CommandSource source, Location pos1, Location pos2) {
        if (pos1.getWorld() == null || pos2.getWorld() == null
                || !pos1.getWorld().equals(pos2.getWorld())) {
            source.send("Both corners must be in the same world.", MessageKind.ERROR);
            return;
        }
        SpawnRegion region = SpawnRegion.undefined().withCorners(
                pos1.getWorld().getName(),
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
        spawnService.replaceRegion(region);
        configPersister.accept(spawnService.config());
        source.send("Defined spawn region: "
                + region.world() + " ("
                + region.minX() + "," + region.minY() + "," + region.minZ()
                + ") → ("
                + region.maxX() + "," + region.maxY() + "," + region.maxZ()
                + "). Protection: " + spawnService.config().protectionSeconds() + "s after respawn inside.",
                MessageKind.SUCCESS);
    }

    private void status(CommandSource source) {
        SpawnConfig config = spawnService.config();
        SpawnRegion region = config.region();
        source.send("Spawn protection duration: " + config.protectionSeconds() + "s.", MessageKind.INFO);
        if (!region.isDefined()) {
            source.send("Spawn region: undefined. Use /larp spawn define pos1|pos2.", MessageKind.INFO);
            return;
        }
        source.send("Spawn region: " + region.world()
                + " (" + region.minX() + "," + region.minY() + "," + region.minZ()
                + ") → (" + region.maxX() + "," + region.maxY() + "," + region.maxZ() + ").",
                MessageKind.INFO);
    }

    private void reload(CommandSource source) {
        configReloader.run();
        SpawnConfig config = spawnService.config();
        source.send("Spawn config reloaded (protection=" + config.protectionSeconds()
                + "s, region=" + (config.region().isDefined() ? "defined" : "undefined") + ").",
                MessageKind.SUCCESS);
    }

    private static List<String> filterPrefix(List<String> values, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }

    private static String formatLoc(Location location) {
        return location.getWorld().getName() + " "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private static @Nullable Location resolvePlayerLocation(CommandSource source) {
        return source.playerId()
                .map(org.bukkit.Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .map(Player::getLocation)
                .orElse(null);
    }

    private static final class PendingDefine {
        @Nullable Location pos1;
        @Nullable Location pos2;
    }
}
