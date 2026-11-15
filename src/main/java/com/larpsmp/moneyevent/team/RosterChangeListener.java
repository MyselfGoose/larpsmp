package com.larpsmp.moneyevent.team;

import java.util.Set;
import java.util.UUID;

/**
 * Notified when team roster membership changes so displays can refresh team wealth.
 *
 * <p>{@code affectedAccountIds} includes players who left a team (they are no longer in
 * {@code membersOf(teamId)} and would otherwise keep a stale Team Money HUD).
 */
@FunctionalInterface
public interface RosterChangeListener {
    void rosterChanged(Set<UUID> affectedTeamIds, Set<UUID> affectedAccountIds);
}
