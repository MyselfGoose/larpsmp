package com.larpsmp.moneyevent.acehunt;

import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Enforces Ace Hunt keep-inventory, confinement, skybase, lockup, and spectator rules.
 */
public final class AceHuntListener implements Listener {

    private final AceHuntService service;
    private final AccountSessionManager sessions;
    private final Plugin plugin;
    private final Map<UUID, PendingRespawn> pendingRespawns = new ConcurrentHashMap<>();

    public AceHuntListener(AceHuntService service, AccountSessionManager sessions, Plugin plugin) {
        this.service = Objects.requireNonNull(service, "service");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Called after kill attribution (or when attribution finds no killer) for scoring.
     */
    public void onCombatDeathOutcome(
            UUID victimMinecraftUuid,
            @Nullable String deathId,
            @Nullable UUID killerAccountId
    ) {
        Optional<AccountSession> victim = sessions.findByMinecraftUuid(victimMinecraftUuid);
        if (victim.isEmpty()) {
            return;
        }
        AceHuntSession hunt = service.activeSession().orElse(null);
        if (hunt == null || !hunt.phase().isScoring()) {
            return;
        }
        if (!hunt.isAce(victim.get().accountId())) {
            return;
        }
        AceHuntService.DeathHandlingResult result = service.handleDeath(
                victim.get().accountId(), deathId, killerAccountId);
        applyDeathResult(victimMinecraftUuid, result);
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

        AceHuntSession hunt = service.activeSession().orElse(null);
        if (hunt == null || !hunt.phase().isLive()) {
            return;
        }

        // Prep ace deaths: no scoring; respawn in territory.
        if (hunt.phase() == AceHuntPhase.PREP && hunt.isAce(accountId)) {
            AceHuntService.DeathHandlingResult result = service.handleDeath(accountId, null, null);
            applyDeathResult(player.getUniqueId(), result);
            return;
        }

        // Non-ace hunter deaths during scoring → lockup (does not need attribution).
        if (hunt.phase().isScoring()
                && !hunt.isAce(accountId)
                && (hunt.snapshots().containsKey(accountId) || hunt.isParticipant(accountId))) {
            AceHuntService.DeathHandlingResult result = service.handleDeath(accountId, null, null);
            applyDeathResult(player.getUniqueId(), result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        PendingRespawn pending = pendingRespawns.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            Optional<AccountSession> session = sessions.findByMinecraftUuid(event.getPlayer().getUniqueId());
            if (session.isPresent() && service.isInLockup(session.get().accountId())) {
                Location location = toLocation(service.config().lockupSpawn());
                if (location != null) {
                    event.setRespawnLocation(location);
                }
            }
            return;
        }
        if (pending.location() != null) {
            event.setRespawnLocation(pending.location());
        }
        if (pending.spectator()) {
            Player player = event.getPlayer();
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
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
        if (service.isSpectator(accountId)) {
            return;
        }
        if (service.isInLockup(accountId)) {
            AceHuntCuboid lockup = service.config().lockup();
            if (lockup.isDefined() && event.getTo().getWorld() != null
                    && !lockup.containsLocation(
                    event.getTo().getWorld().getName(),
                    event.getTo().getX(),
                    event.getTo().getY(),
                    event.getTo().getZ())) {
                Location clamped = clampToCuboid(lockup, event.getTo());
                if (clamped != null) {
                    event.setTo(clamped);
                }
            }
            return;
        }
        Optional<AceHuntTerritory> territory = service.confinementTerritory(accountId);
        if (territory.isEmpty() || !territory.get().region().isDefined()) {
            return;
        }
        AceHuntCuboid region = territory.get().region();
        Location to = event.getTo();
        if (to.getWorld() == null) {
            return;
        }
        if (!region.containsLocation(to.getWorld().getName(), to.getX(), to.getY(), to.getZ())) {
            Location clamped = clampToCuboid(region, to);
            if (clamped != null) {
                event.setTo(clamped);
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
        if (service.isSpectator(accountId) || service.isInLockup(accountId)) {
            return;
        }
        // Allow plugin-initiated teleports (spawns / restore).
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                || event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        Optional<AceHuntTerritory> territory = service.confinementTerritory(accountId);
        if (territory.isEmpty() || !territory.get().region().isDefined()) {
            return;
        }
        AceHuntCuboid region = territory.get().region();
        Location to = event.getTo();
        if (to.getWorld() == null) {
            return;
        }
        if (!region.containsLocation(to.getWorld().getName(), to.getX(), to.getY(), to.getZ())) {
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
        if (service.isSpectator(accountId) || service.isInLockup(accountId)) {
            event.setCancelled(true);
            return;
        }
        Optional<AceHuntTerritory> territory = service.confinementTerritory(accountId);
        if (territory.isEmpty()) {
            return;
        }
        Integer maxY = territory.get().maxLegalY();
        if (maxY != null && event.getBlockPlaced().getY() > maxY) {
            event.setCancelled(true);
            return;
        }
        Location location = event.getBlockPlaced().getLocation();
        if (location.getWorld() != null
                && service.isUndergroundBanned(accountId, location.getWorld().getName(), location.getY())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<AccountSession> session = sessions.findByMinecraftUuid(event.getPlayer().getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        UUID accountId = session.get().accountId();
        if (service.isSpectator(accountId) || service.isInLockup(accountId)) {
            event.setCancelled(true);
            return;
        }
        Location location = event.getBlock().getLocation();
        if (location.getWorld() != null
                && service.isUndergroundBanned(accountId, location.getWorld().getName(), location.getY())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Optional<AccountSession> session = sessions.findByMinecraftUuid(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        if (service.isInLockup(session.get().accountId())) {
            event.setCancelled(true);
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

    private void applyDeathResult(UUID minecraftUuid, AceHuntService.DeathHandlingResult result) {
        switch (result) {
            case AceHuntService.DeathHandlingResult.AcePrepRespawn prep -> {
                Location location = toLocation(prep.territory().aceSpawn());
                pendingRespawns.put(minecraftUuid, new PendingRespawn(location, false));
            }
            case AceHuntService.DeathHandlingResult.Lockup lockup -> {
                Location location = toLocation(lockup.spawn());
                pendingRespawns.put(minecraftUuid, new PendingRespawn(location, false));
            }
            case AceHuntService.DeathHandlingResult.Spectator ignored ->
                    pendingRespawns.put(minecraftUuid, new PendingRespawn(null, true));
            default -> {
            }
        }
    }

    private static @Nullable Location toLocation(AceHuntSpawnPoint spawn) {
        if (spawn == null || !spawn.isDefined()) {
            return null;
        }
        World world = org.bukkit.Bukkit.getWorld(spawn.world());
        if (world == null) {
            return null;
        }
        return new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    private static @Nullable Location clampToCuboid(AceHuntCuboid cuboid, Location to) {
        World world = org.bukkit.Bukkit.getWorld(cuboid.world());
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

    private static boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && Objects.equals(from.getWorld(), to.getWorld());
    }

    private record PendingRespawn(@Nullable Location location, boolean spectator) {
    }
}
