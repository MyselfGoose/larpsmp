package com.larpsmp.moneyevent.capture;

import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Who counts toward capture advantage.
 */
public final class CaptureEligibility {

    public static final String TELEPORTING_METADATA = "larpsmp_capture_teleporting";

    private CaptureEligibility() {
    }

    public static boolean counts(
            Player player,
            Predicate<UUID> isPluginAdmin,
            @Nullable Predicate<UUID> hasTeam) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (player.isDead()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (isPluginAdmin != null && isPluginAdmin.test(player.getUniqueId())) {
            return false;
        }
        if (player.hasMetadata(TELEPORTING_METADATA)) {
            return false;
        }
        if (hasTeam != null && !hasTeam.test(player.getUniqueId())) {
            return false;
        }
        return true;
    }
}
