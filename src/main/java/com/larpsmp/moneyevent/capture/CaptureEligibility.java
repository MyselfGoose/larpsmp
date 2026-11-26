package com.larpsmp.moneyevent.capture;

import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Who counts toward capture advantage.
 *
 * <p>Only living, non-teleporting competitors with a team count. Vanilla spectator gamemode never
 * counts. Plugin admin status does not exclude a competitor (e.g. Karim).
 */
public final class CaptureEligibility {

    public static final String TELEPORTING_METADATA = "larpsmp_capture_teleporting";

    private CaptureEligibility() {
    }

    public static boolean counts(
            Player player,
            @Nullable Predicate<UUID> isCompetitor,
            @Nullable Predicate<UUID> hasTeam) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        boolean competitor = isCompetitor == null || isCompetitor.test(player.getUniqueId());
        boolean team = hasTeam == null || hasTeam.test(player.getUniqueId());
        return countsState(
                true,
                player.isDead(),
                player.getGameMode(),
                player.hasMetadata(TELEPORTING_METADATA),
                competitor,
                team);
    }

    /**
     * Pure eligibility decision for tests and scanners.
     */
    public static boolean countsState(
            boolean online,
            boolean dead,
            @Nullable GameMode gameMode,
            boolean teleporting,
            boolean competitor,
            boolean hasTeam) {
        if (!online || dead) {
            return false;
        }
        if (gameMode == GameMode.SPECTATOR) {
            return false;
        }
        if (teleporting) {
            return false;
        }
        return competitor && hasTeam;
    }
}
