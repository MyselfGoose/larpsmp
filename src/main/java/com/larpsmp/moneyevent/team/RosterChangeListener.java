package com.larpsmp.moneyevent.team;

import java.util.Set;
import java.util.UUID;

/**
 * Notified when team roster membership changes so displays can refresh team wealth.
 */
@FunctionalInterface
public interface RosterChangeListener {
    void rosterChanged(Set<UUID> affectedTeamIds);
}
