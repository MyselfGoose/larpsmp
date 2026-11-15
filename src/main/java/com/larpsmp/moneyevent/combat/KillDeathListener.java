package com.larpsmp.moneyevent.combat;

import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.command.OnlinePlayerAccess;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.wallet.DeathPaymentResult;
import com.larpsmp.moneyevent.wallet.SettlementContribution;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Settles attributed deaths and notifies the killer with a styled credit card.
 * Does not punish combat logging or disconnects.
 */
public final class KillDeathListener implements Listener {

    private final KillAttributionService attribution;
    private final AccountRepository accounts;
    private final OnlinePlayerAccess onlinePlayers;
    private final Logger logger;

    public KillDeathListener(
            KillAttributionService attribution,
            AccountRepository accounts,
            OnlinePlayerAccess onlinePlayers,
            Logger logger
    ) {
        this.attribution = attribution;
        this.accounts = accounts;
        this.onlinePlayers = onlinePlayers;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        AttributionCause deathHint = hintFromFinalDamage(victim.getLastDamageCause());
        AttributionOutcome outcome = attribution.attributeDeath(victim.getUniqueId(), deathHint);
        switch (outcome.kind()) {
            case ATTRIBUTED -> {
                DeathPaymentResult settlement = outcome.settlement();
                logger.info("Kill attributed deathId=" + outcome.deathId()
                        + " outcome=" + (settlement == null ? "?" : settlement.outcome())
                        + " evidence=" + (outcome.killRecord() == null ? "" : outcome.killRecord().evidence()));
                if (settlement != null && settlement.applied()) {
                    notifyKillerCredit(outcome, settlement);
                }
            }
            case ENVIRONMENTAL -> logger.fine("Environmental death for " + victim.getUniqueId()
                    + ": " + outcome.detail());
            case SKIPPED, DUPLICATE -> logger.info("Kill attribution skipped for " + victim.getUniqueId()
                    + ": " + outcome.detail());
        }
    }

    private void notifyKillerCredit(AttributionOutcome outcome, DeathPaymentResult settlement) {
        CombatKillRecord killRecord = outcome.killRecord();
        PlayerTier victimTier = outcome.victimTier();
        String victimUsername = outcome.victimUsername();
        if (killRecord == null || victimTier == null || victimUsername == null) {
            return;
        }

        Optional<SettlementContribution> killerCredit = settlement.contributions().stream()
                .filter(SettlementContribution::credit)
                .filter(contribution -> contribution.accountId().equals(killRecord.killerAccountId()))
                .findFirst();
        if (killerCredit.isEmpty() || killerCredit.get().amount() <= 0) {
            return;
        }

        SettlementContribution credit = killerCredit.get();
        Component[] lines = KillCreditMessages.creditCard(
                victimUsername,
                victimTier,
                settlement.owedAmount(),
                credit.amount(),
                credit.balanceBefore(),
                credit.balanceAfter()
        );

        try {
            accounts.findIdentityByAccountId(killRecord.killerAccountId()).ifPresent(identity -> {
                for (Component line : lines) {
                    onlinePlayers.send(identity.minecraftUuid(), line);
                }
            });
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Could not notify killer of death credit for "
                    + killRecord.deathId(), exception);
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
