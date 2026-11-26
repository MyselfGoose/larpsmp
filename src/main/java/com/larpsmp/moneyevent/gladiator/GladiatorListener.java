package com.larpsmp.moneyevent.gladiator;

import com.larpsmp.moneyevent.auth.AccountAdminService;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Enforces Gladiators keep-inventory, arena/seat confinement, and barrier protection.
 */
public final class GladiatorListener implements Listener {

    private final GladiatorService service;
    private final AccountSessionManager sessions;
    private final AccountAdminService accountAdmins;
    private final Plugin plugin;

    public GladiatorListener(
            GladiatorService service,
            AccountSessionManager sessions,
            AccountAdminService accountAdmins,
            Plugin plugin
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.accountAdmins = Objects.requireNonNull(accountAdmins, "accountAdmins");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Optional<AccountSession> session = sessions.findByMinecraftUuid(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        UUID accountId = session.get().accountId();
        if (service.shouldKeepInventory(accountId)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
        GladiatorService.DeathHandlingResult result = service.handleDeath(accountId);
        if (result instanceof GladiatorService.DeathHandlingResult.Seat) {
            Location seats = seatsLocation();
            if (seats != null) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.teleport(seats);
                        player.setGameMode(GameMode.ADVENTURE);
                        player.getInventory().clear();
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Optional<AccountSession> session = sessions.findByMinecraftUuid(event.getPlayer().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        UUID accountId = session.get().accountId();
        if (service.shouldConfineToSeats(accountId) || service.shouldConfineToArena(accountId)) {
            Location seats = seatsLocation();
            if (seats != null) {
                event.setRespawnLocation(seats);
            }
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = event.getPlayer();
                if (player.isOnline() && !service.shouldConfineToArena(accountId)) {
                    player.setGameMode(GameMode.ADVENTURE);
                    player.getInventory().clear();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        Optional<AccountSession> session = sessions.findByMinecraftUuid(event.getPlayer().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        UUID accountId = session.get().accountId();
        if (isExemptAdmin(event.getPlayer(), accountId)) {
            return;
        }
        Location to = event.getTo();
        if (to.getWorld() == null) {
            return;
        }
        if (service.shouldConfineToArena(accountId)) {
            GladiatorCuboid arena = service.config().arena();
            if (arena.isDefined() && !arena.containsLocation(
                    to.getWorld().getName(), to.getX(), to.getY(), to.getZ())) {
                Location clamped = clamp(arena, to);
                if (clamped != null) {
                    event.setTo(clamped);
                }
            }
            return;
        }
        if (service.shouldConfineToSeats(accountId)) {
            GladiatorCuboid seats = service.config().seats();
            GladiatorCuboid arena = service.config().arena();
            if (arena.isDefined() && arena.containsLocation(
                    to.getWorld().getName(), to.getX(), to.getY(), to.getZ())) {
                Location seatLoc = seatsLocation();
                if (seatLoc != null) {
                    event.setTo(seatLoc);
                }
                return;
            }
            if (seats.isDefined() && !seats.containsLocation(
                    to.getWorld().getName(), to.getX(), to.getY(), to.getZ())) {
                Location clamped = clamp(seats, to);
                if (clamped != null) {
                    event.setTo(clamped);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Optional<AccountSession> session = sessions.findByMinecraftUuid(event.getPlayer().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        UUID accountId = session.get().accountId();
        if (isExemptAdmin(event.getPlayer(), accountId)) {
            return;
        }
        Location to = event.getTo();
        if (to.getWorld() == null) {
            return;
        }
        if (service.shouldConfineToArena(accountId)) {
            GladiatorCuboid arena = service.config().arena();
            if (arena.isDefined() && !arena.containsLocation(
                    to.getWorld().getName(), to.getX(), to.getY(), to.getZ())) {
                event.setCancelled(true);
            }
            return;
        }
        if (service.shouldConfineToSeats(accountId)) {
            GladiatorCuboid arena = service.config().arena();
            if (arena.isDefined() && arena.containsLocation(
                    to.getWorld().getName(), to.getX(), to.getY(), to.getZ())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!service.isBarrierProtected()) {
            return;
        }
        GladiatorCuboid barriers = service.config().barriers();
        if (!barriers.isDefined()) {
            return;
        }
        Location loc = event.getBlock().getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        if (barriers.containsBlock(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                && event.getBlock().getType() == Material.BARRIER) {
            Optional<AccountSession> session = sessions.findByMinecraftUuid(event.getPlayer().getUniqueId());
            if (session.isPresent() && isExemptAdmin(event.getPlayer(), session.get().accountId())) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<AccountSession> session = sessions.findByMinecraftUuid(event.getPlayer().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        UUID accountId = session.get().accountId();
        if (isExemptAdmin(event.getPlayer(), accountId)) {
            return;
        }
        if (service.shouldConfineToSeats(accountId) || service.shouldConfineToArena(accountId)) {
            // Sitters in adventure; fighters can place within arena during fight if needed —
            // keep place allowed for fighters, cancel for seat-confined.
            if (service.shouldConfineToSeats(accountId)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Optional<AccountSession> session = sessions.findByMinecraftUuid(event.getPlayer().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> service.onPlayerJoin(session.get().accountId()));
    }

    private boolean isExemptAdmin(Player player, UUID accountId) {
        if (service.shouldConfineToArena(accountId) && service.activeSession()
                .map(s -> s.isAliveFighter(accountId)).orElse(false)) {
            return false;
        }
        return player.isOp() || accountAdmins.isAdminMinecraftUuid(player.getUniqueId());
    }

    private @Nullable Location seatsLocation() {
        GladiatorSpawnPoint spawn = service.config().seatsSpawn();
        if (!spawn.isDefined()) {
            return null;
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(spawn.world());
        if (world == null) {
            return null;
        }
        return new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    private static @Nullable Location clamp(GladiatorCuboid cuboid, Location to) {
        if (!cuboid.isDefined() || to.getWorld() == null) {
            return null;
        }
        if (!to.getWorld().getName().equalsIgnoreCase(cuboid.world())) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(cuboid.world());
            if (world == null) {
                return null;
            }
            return new Location(
                    world,
                    cuboid.clampX(to.getX()),
                    cuboid.clampY(to.getY()),
                    cuboid.clampZ(to.getZ()),
                    to.getYaw(),
                    to.getPitch());
        }
        return new Location(
                to.getWorld(),
                cuboid.clampX(to.getX()),
                cuboid.clampY(to.getY()),
                cuboid.clampZ(to.getZ()),
                to.getYaw(),
                to.getPitch());
    }

    private static boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && Objects.equals(from.getWorld(), to.getWorld());
    }
}
