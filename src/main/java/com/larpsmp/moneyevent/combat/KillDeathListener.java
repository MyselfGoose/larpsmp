package com.larpsmp.moneyevent.combat;

import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Settles attributed deaths. Does not punish combat logging or disconnects.
 */
public final class KillDeathListener implements Listener {

    private final KillAttributionService attribution;
    private final Logger logger;

    public KillDeathListener(KillAttributionService attribution, Logger logger) {
        this.attribution = attribution;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        AttributionCause deathHint = hintFromFinalDamage(victim.getLastDamageCause());
        AttributionOutcome outcome = attribution.attributeDeath(victim.getUniqueId(), deathHint);
        switch (outcome.kind()) {
            case ATTRIBUTED -> {
                var settlement = outcome.settlement();
                logger.info("Kill attributed deathId=" + outcome.deathId()
                        + " outcome=" + (settlement == null ? "?" : settlement.outcome())
                        + " evidence=" + (outcome.killRecord() == null ? "" : outcome.killRecord().evidence()));
            }
            case ENVIRONMENTAL -> logger.fine("Environmental death for " + victim.getUniqueId()
                    + ": " + outcome.detail());
            case SKIPPED, DUPLICATE -> logger.info("Kill attribution skipped for " + victim.getUniqueId()
                    + ": " + outcome.detail());
        }
    }

    private static @Nullable AttributionCause hintFromFinalDamage(
            @Nullable EntityDamageEvent lastDamage
    ) {
        if (lastDamage == null) {
            return null;
        }
        return switch (lastDamage.getCause()) {
            case FALL, FLY_INTO_WALL -> AttributionCause.FALL_AFTER_HIT;
            case LAVA -> AttributionCause.LAVA;
            case FIRE, FIRE_TICK, HOT_FLOOR -> AttributionCause.FIRE;
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> AttributionCause.EXPLOSION;
            case PROJECTILE -> AttributionCause.PROJECTILE;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> AttributionCause.MELEE;
            default -> null;
        };
    }
}
