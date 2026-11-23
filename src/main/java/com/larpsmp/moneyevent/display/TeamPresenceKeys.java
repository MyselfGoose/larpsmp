package com.larpsmp.moneyevent.display;

import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import org.jetbrains.annotations.Nullable;

/**
 * Stable scoreboard team names for tab grouping (team → tier → unassigned).
 *
 * <p>Names stay short (Minecraft team-name limit) and sort lexicographically so the player
 * list orders by competition {@link Team#sortOrder()}, then tier 1–4, then unassigned.
 */
public final class TeamPresenceKeys {
    public static final String UNASSIGNED = "z_none";
    public static final String PRESENCE_PREFIX = "t";

    private TeamPresenceKeys() {
    }

    public static String forMembership(Team team, @Nullable PlayerTier tier) {
        int sort = Math.max(0, Math.min(99, team.sortOrder()));
        int tierDigit = tier == null ? 0 : Math.max(1, Math.min(4, tier.value()));
        return String.format("%s%02d_%d", PRESENCE_PREFIX, sort, tierDigit);
    }

    public static boolean isPresenceTeam(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return false;
        }
        if (teamName.equals(UNASSIGNED)) {
            return true;
        }
        // t{sort:02}_{tier} — must not match sidebar slots like sb_tm_lbl.
        return teamName.length() == 5
                && teamName.charAt(0) == 't'
                && Character.isDigit(teamName.charAt(1))
                && Character.isDigit(teamName.charAt(2))
                && teamName.charAt(3) == '_'
                && Character.isDigit(teamName.charAt(4));
    }
}
