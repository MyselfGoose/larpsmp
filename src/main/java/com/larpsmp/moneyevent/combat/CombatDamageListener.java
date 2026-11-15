package com.larpsmp.moneyevent.combat;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.Nullable;

/**
 * Records last-attacker credits from direct and indirect damage.
 */
public final class CombatDamageListener implements Listener {

    private final AttackMemory attackMemory;
    private final BlockOwnershipTracker ownership;

    public CombatDamageListener(AttackMemory attackMemory, BlockOwnershipTracker ownership) {
        this.attackMemory = attackMemory;
        this.ownership = ownership;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        ResolvedAttacker resolved = resolveEntityAttacker(event.getDamager());
        if (resolved == null || resolved.attackerUuid().equals(victim.getUniqueId())) {
            return;
        }
        attackMemory.record(victim.getUniqueId(), resolved.attackerUuid(), resolved.cause(), resolved.detail());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByBlock(EntityDamageByBlockEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Block block = event.getDamager();
        if (block == null) {
            tryOwnedHazardFromSource(victim, event);
            return;
        }
        creditOwnedBlock(victim, block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || event instanceof EntityDamageByBlockEvent) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.CONTACT) {
            tryOwnedHazardFromSource(victim, event);
        }
        // FALL and similar do not refresh attacker; a prior hit within 60s still credits on death.
    }

    private void tryOwnedHazardFromSource(Player victim, EntityDamageEvent event) {
        DamageSource source = event.getDamageSource();
        Entity causing = source.getCausingEntity() != null
                ? source.getCausingEntity()
                : source.getDirectEntity();
        if (causing != null) {
            ResolvedAttacker resolved = resolveEntityAttacker(causing);
            if (resolved != null && !resolved.attackerUuid().equals(victim.getUniqueId())) {
                attackMemory.record(
                        victim.getUniqueId(), resolved.attackerUuid(), resolved.cause(), resolved.detail());
                return;
            }
        }

        Block locBlock = victim.getLocation().getBlock();
        if (creditOwnedBlock(victim, locBlock)) {
            return;
        }
        creditOwnedBlock(victim, locBlock.getRelative(0, -1, 0));
    }

    private boolean creditOwnedBlock(Player victim, Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        Optional<UUID> owner = ownership.ownerOf(BlockOwnershipListener.key(block));
        if (owner.isEmpty()) {
            for (Block neighbor : new Block[] {
                    block.getRelative(1, 0, 0),
                    block.getRelative(-1, 0, 0),
                    block.getRelative(0, 0, 1),
                    block.getRelative(0, 0, -1),
                    block.getRelative(0, 1, 0),
                    block.getRelative(0, -1, 0)
            }) {
                if (!OwnedHazardMaterials.isTracked(neighbor.getType())) {
                    continue;
                }
                Optional<UUID> neighborOwner = ownership.ownerOf(BlockOwnershipListener.key(neighbor));
                if (neighborOwner.isPresent()) {
                    owner = neighborOwner;
                    type = neighbor.getType();
                    break;
                }
            }
        } else if (!OwnedHazardMaterials.isTracked(type)
                && type != Material.LAVA
                && type != Material.FIRE
                && type != Material.SOUL_FIRE) {
            // Owned but unexpected type (e.g. replaced) — still credit tracked cause as OTHER.
            type = Material.AIR;
        }
        if (owner.isEmpty() || owner.get().equals(victim.getUniqueId())) {
            return false;
        }
        AttributionCause cause = OwnedHazardMaterials.isTracked(type)
                ? OwnedHazardMaterials.causeFor(type)
                : AttributionCause.TRAP;
        attackMemory.record(victim.getUniqueId(), owner.get(), cause, type == Material.AIR ? null : type.name());
        return true;
    }

    private static @Nullable ResolvedAttacker resolveEntityAttacker(Entity damager) {
        if (damager == null) {
            return null;
        }
        if (damager instanceof Player player) {
            return new ResolvedAttacker(player.getUniqueId(), AttributionCause.MELEE, null);
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return new ResolvedAttacker(
                        player.getUniqueId(), AttributionCause.PROJECTILE, projectile.getType().name());
            }
            if (shooter instanceof Tameable tameable) {
                return fromTameable(tameable);
            }
            return null;
        }
        if (damager instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return new ResolvedAttacker(player.getUniqueId(), AttributionCause.EXPLOSION, "TNT");
            }
            if (source instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
                return new ResolvedAttacker(player.getUniqueId(), AttributionCause.EXPLOSION, "TNT");
            }
            return null;
        }
        if (damager instanceof Tameable tameable) {
            return fromTameable(tameable);
        }
        return null;
    }

    private static @Nullable ResolvedAttacker fromTameable(Tameable tameable) {
        if (!tameable.isTamed()) {
            return null;
        }
        UUID ownerUuid = tameable.getOwnerUniqueId();
        if (ownerUuid == null) {
            var owner = tameable.getOwner();
            if (owner == null) {
                return null;
            }
            ownerUuid = owner.getUniqueId();
        }
        return new ResolvedAttacker(ownerUuid, AttributionCause.PET, tameable.getType().name());
    }

    private record ResolvedAttacker(UUID attackerUuid, AttributionCause cause, @Nullable String detail) {
    }
}
