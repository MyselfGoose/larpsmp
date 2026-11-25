package com.larpsmp.moneyevent.acehunt;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Per-team ace island: cuboid, spawns, and Y rules for skybase / underground ban.
 */
public record AceHuntTerritory(
        UUID teamId,
        String teamSlug,
        AceHuntCuboid region,
        AceHuntSpawnPoint aceSpawn,
        AceHuntSpawnPoint hunterSpawn,
        @Nullable Integer surfaceY,
        @Nullable Integer maxLegalY
) {
    public AceHuntTerritory {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(teamSlug, "teamSlug");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(aceSpawn, "aceSpawn");
        Objects.requireNonNull(hunterSpawn, "hunterSpawn");
        teamSlug = teamSlug.trim().toLowerCase(Locale.ROOT);
    }

    public static AceHuntTerritory empty(UUID teamId, String teamSlug) {
        return new AceHuntTerritory(
                teamId,
                teamSlug,
                AceHuntCuboid.undefined(),
                AceHuntSpawnPoint.undefined(),
                AceHuntSpawnPoint.undefined(),
                null,
                null
        );
    }

    public boolean isReadyForPrep() {
        return region.isDefined()
                && aceSpawn.isDefined()
                && surfaceY != null
                && maxLegalY != null;
    }

    public boolean isReadyForHunt() {
        return isReadyForPrep() && hunterSpawn.isDefined();
    }

    public AceHuntTerritory withRegion(AceHuntCuboid updated) {
        return new AceHuntTerritory(teamId, teamSlug, updated, aceSpawn, hunterSpawn, surfaceY, maxLegalY);
    }

    public AceHuntTerritory withAceSpawn(AceHuntSpawnPoint spawn) {
        return new AceHuntTerritory(teamId, teamSlug, region, spawn, hunterSpawn, surfaceY, maxLegalY);
    }

    public AceHuntTerritory withHunterSpawn(AceHuntSpawnPoint spawn) {
        return new AceHuntTerritory(teamId, teamSlug, region, aceSpawn, spawn, surfaceY, maxLegalY);
    }

    public AceHuntTerritory withSurfaceY(int y) {
        return new AceHuntTerritory(teamId, teamSlug, region, aceSpawn, hunterSpawn, y, maxLegalY);
    }

    public AceHuntTerritory withMaxLegalY(int y) {
        return new AceHuntTerritory(teamId, teamSlug, region, aceSpawn, hunterSpawn, surfaceY, y);
    }
}
