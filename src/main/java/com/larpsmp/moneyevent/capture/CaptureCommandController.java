package com.larpsmp.moneyevent.capture;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Admin commands for capture points ({@code /larp capture ...}).
 */
public final class CaptureCommandController {

    private final CaptureService captureService;
    private final Consumer<CaptureConfig> configPersister;
    private final Runnable configReloader;
    private final Object defineLock = new Object();
    private final java.util.Map<String, PendingDefine> pendingDefines = new java.util.HashMap<>();

    public CaptureCommandController(
            CaptureService captureService,
            Consumer<CaptureConfig> configPersister,
            Runnable configReloader) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
        this.configPersister = Objects.requireNonNull(configPersister, "configPersister");
        this.configReloader = Objects.requireNonNull(configReloader, "configReloader");
    }

    public boolean handle(CommandSource source, String[] arguments) {
        if (arguments.length < 1 || !arguments[0].equalsIgnoreCase("capture")) {
            return false;
        }
        if (!source.isConsole() && !source.isOperator()) {
            source.send("You are not authorized to perform that action.", MessageKind.ERROR);
            return true;
        }
        if (arguments.length < 2) {
            source.send(
                    "Usage: /larp capture <start|pause|resume|stop|status|define|reload> ...",
                    MessageKind.ERROR);
            return true;
        }
        String action = arguments[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> start(source, arguments);
            case "pause" -> respondAdmin(source, captureService.pause());
            case "resume" -> respondAdmin(source, captureService.resume());
            case "stop" -> respondAdmin(source, captureService.stop());
            case "status" -> status(source);
            case "define" -> define(source, arguments);
            case "reload" -> reload(source);
            default -> source.send(
                    "Usage: /larp capture <start|pause|resume|stop|status|define|reload> ...",
                    MessageKind.ERROR);
        }
        return true;
    }

    public List<String> tabComplete(CommandSource source, String[] arguments) {
        if (!source.isConsole() && !source.isOperator()) {
            return List.of();
        }
        if (arguments.length == 1) {
            return List.of("capture");
        }
        if (!arguments[0].equalsIgnoreCase("capture")) {
            return List.of();
        }
        if (arguments.length == 2) {
            return filterPrefix(
                    List.of("start", "pause", "resume", "stop", "status", "define", "reload"),
                    arguments[1]);
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("start")) {
            return filterPrefix(pointIds(), arguments[2]);
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("define")) {
            return filterPrefix(pointIds(), arguments[2]);
        }
        if (arguments.length == 4 && arguments[1].equalsIgnoreCase("define")) {
            return filterPrefix(List.of("pos1", "pos2", "here"), arguments[3]);
        }
        return List.of();
    }

    private void start(CommandSource source, String[] arguments) {
        String pointId = arguments.length >= 3 ? arguments[2] : null;
        CaptureService.StartResult result = captureService.start(pointId);
        switch (result) {
            case CaptureService.StartResult.Started started ->
                    source.send("Capture started at " + started.point().displayName()
                            + " (id=" + started.session().captureId() + ").", MessageKind.SUCCESS);
            case CaptureService.StartResult.AlreadyActive already ->
                    source.send("A capture is already active on " + already.session().pointId() + ".",
                            MessageKind.ERROR);
            case CaptureService.StartResult.UnknownPoint unknown ->
                    source.send("Unknown capture point '" + unknown.pointId() + "'.", MessageKind.ERROR);
            case CaptureService.StartResult.RegionUndefined undefined ->
                    source.send("Point '" + undefined.pointId()
                            + "' has no region. Use /larp capture define <id> pos1|pos2.", MessageKind.ERROR);
            case CaptureService.StartResult.NoEligiblePoints none ->
                    source.send("No eligible capture points left for day " + none.dayKey() + ".",
                            MessageKind.ERROR);
            case CaptureService.StartResult.AlreadyCompleted completed ->
                    source.send("Point '" + completed.pointId() + "' was already captured today ("
                            + completed.dayKey() + ").", MessageKind.ERROR);
            case CaptureService.StartResult.Failed failed ->
                    source.send("Could not start capture: " + failed.message(), MessageKind.ERROR);
        }
    }

    private void status(CommandSource source) {
        Optional<CaptureSession> active = captureService.activeSession();
        Set<String> completed = captureService.completedPointsToday();
        if (active.isEmpty()) {
            source.send("No active capture. Completed today: "
                    + (completed.isEmpty() ? "(none)" : String.join(", ", completed)), MessageKind.INFO);
            return;
        }
        CaptureSession session = active.get();
        source.send("Active: " + session.pointId()
                + " status=" + session.status()
                + " progress=" + session.progress().progressPercent() + "%"
                + " captureId=" + session.captureId(), MessageKind.INFO);
        source.send("Completed today: "
                + (completed.isEmpty() ? "(none)" : String.join(", ", completed)), MessageKind.INFO);
    }

    private void define(CommandSource source, String[] arguments) {
        if (arguments.length < 4) {
            source.send("Usage: /larp capture define <pointId> <pos1|pos2|here>", MessageKind.ERROR);
            return;
        }
        Optional<CapturePointDefinition> point = captureService.config().point(arguments[2]);
        if (point.isEmpty()) {
            source.send("Unknown capture point '" + arguments[2] + "'.", MessageKind.ERROR);
            return;
        }
        Location location = resolvePlayerLocation(source);
        if (location == null || location.getWorld() == null) {
            source.send("Define requires an in-game player location.", MessageKind.ERROR);
            return;
        }
        String mode = arguments[3].toLowerCase(Locale.ROOT);
        String pointId = point.get().id();
        synchronized (defineLock) {
            PendingDefine pending = pendingDefines.computeIfAbsent(pointId, ignored -> new PendingDefine());
            switch (mode) {
                case "pos1" -> {
                    pending.pos1 = location.clone();
                    source.send("Set pos1 for " + pointId + " to "
                            + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                case "pos2" -> {
                    pending.pos2 = location.clone();
                    source.send("Set pos2 for " + pointId + " to "
                            + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                case "here" -> {
                    pending.pos1 = location.clone();
                    pending.pos2 = location.clone();
                    source.send("Set single-block region for " + pointId + " at "
                            + formatLoc(location) + ".", MessageKind.SUCCESS);
                }
                default -> {
                    source.send("Usage: /larp capture define <pointId> <pos1|pos2|here>", MessageKind.ERROR);
                    return;
                }
            }
            if (pending.pos1 != null && pending.pos2 != null) {
                applyDefine(source, point.get(), pending.pos1, pending.pos2);
                pendingDefines.remove(pointId);
            }
        }
    }

    private void applyDefine(
            CommandSource source,
            CapturePointDefinition current,
            Location pos1,
            Location pos2) {
        if (pos1.getWorld() == null || pos2.getWorld() == null
                || !pos1.getWorld().equals(pos2.getWorld())) {
            source.send("Both corners must be in the same world.", MessageKind.ERROR);
            return;
        }
        CapturePointDefinition updated = current.withCorners(
                pos1.getWorld().getName(),
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
        List<CapturePointDefinition> points = new ArrayList<>();
        for (CapturePointDefinition point : captureService.config().points()) {
            if (point.id().equals(updated.id())) {
                points.add(updated);
            } else {
                points.add(point);
            }
        }
        CaptureConfig newConfig = captureService.config().withPoints(points);
        captureService.replaceConfig(newConfig);
        configPersister.accept(newConfig);
        source.send("Defined region for " + updated.displayName() + ": "
                + updated.world() + " ("
                + updated.minX() + "," + updated.minY() + "," + updated.minZ()
                + ") → ("
                + updated.maxX() + "," + updated.maxY() + "," + updated.maxZ()
                + ").", MessageKind.SUCCESS);
    }

    private void reload(CommandSource source) {
        configReloader.run();
        source.send("Capture config reloaded from config.yml ("
                + captureService.config().points().size() + " points).", MessageKind.SUCCESS);
    }

    private void respondAdmin(CommandSource source, CaptureService.AdminResult result) {
        switch (result) {
            case CaptureService.AdminResult.Ok ok ->
                    source.send("Capture " + ok.action() + " (" + ok.session().pointId() + ").",
                            MessageKind.SUCCESS);
            case CaptureService.AdminResult.NoActiveSession ignored ->
                    source.send("No active capture session.", MessageKind.ERROR);
            case CaptureService.AdminResult.Failed failed ->
                    source.send("Capture admin action failed: " + failed.message(), MessageKind.ERROR);
        }
    }

    private List<String> pointIds() {
        return captureService.config().points().stream().map(CapturePointDefinition::id).toList();
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
