package com.larpsmp.moneyevent.combat;

import java.util.UUID;

/**
 * Marks whether an attributed kill should be excluded from the normal kill leaderboard.
 * Event systems (Gladiators, Ace Hunt) will register participants later.
 */
@FunctionalInterface
public interface EventCombatScope {

    EventCombatScope NEVER = (victimAccountId, killerAccountId) -> false;

    /**
     * @return true when this kill must not count on the normal {@code /top kills} leaderboard
     */
    boolean isEventCombat(UUID victimAccountId, UUID killerAccountId);
}
