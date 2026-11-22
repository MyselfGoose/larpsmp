package com.larpsmp.moneyevent.spawn;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.Nullable;

/**
 * Grants respawn-only protection and cancels player-caused damage against protected players.
 */
public final class SpawnProtectionListener implements Listener {

    private final SpawnService spawnService;

    public SpawnProtectionListener(SpawnService spawnService) {
        this.spawnService = Objects.requireNonNull(spawnService, "spawnService");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Location location = event.getRespawnLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        SpawnRegion region = spawnService.region();
        if (!region.isDefined()) {
            return;
        }
        if (!region.containsLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ())) {
            return;
        }
        spawnService.tracker().grant(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        UUID attackerUuid = resolvePlayerAttacker(event.getDamager());
        if (attackerUuid == null || attackerUuid.equals(victim.getUniqueId())) {
            return;
        }

        SpawnProtectionTracker tracker = spawnService.tracker();
        if (tracker.isProtected(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Protected attackers lose immunity when they deal player-caused damage.
        if (tracker.isProtected(attackerUuid)) {
            tracker.clear(attackerUuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        spawnService.tracker().clear(event.getPlayer().getUniqueId());
    }

    private static @Nullable UUID resolvePlayerAttacker(Entity damager) {
        if (damager == null) {
            return null;
        }
        if (damager instanceof Player player) {
            return player.getUniqueId();
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player.getUniqueId();
            }
            if (shooter instanceof Tameable tameable) {
                return ownerUuid(tameable);
            }
            return null;
        }
        if (damager instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return player.getUniqueId();
            }
            if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
                return player.getUniqueId();
            }
            return null;
        }
        if (damager instanceof Tameable tameable) {
            return ownerUuid(tameable);
        }
        return null;
    }

    private static @Nullable UUID ownerUuid(Tameable tameable) {
        if (!tameable.isTamed()) {
            return null;
        }
        UUID ownerUuid = tameable.getOwnerUniqueId();
        if (ownerUuid != null) {
            return ownerUuid;
        }
        var owner = tameable.getOwner();
        return owner == null ? null : owner.getUniqueId();
    }
}
