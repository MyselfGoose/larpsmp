package com.larpsmp.moneyevent.acehunt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Typed Ace Hunt settings from {@code config.yml}.
 */
public final class AceHuntConfig {

    private final Duration huntDuration;
    private final Duration surfaceWarning;
    private final Duration respawnLock;
    private final double undergroundDamagePerSecond;
    private final int[] huntPlacementPoints;
    private final int[] survivalPlacementPoints;
    private final Map<String, AceHuntTerritory> territoriesBySlug;
    private final AceHuntCuboid lockup;
    private final AceHuntSpawnPoint lockupSpawn;
    private final Messages messages;

    public AceHuntConfig(
            Duration huntDuration,
            Duration surfaceWarning,
            Duration respawnLock,
            double undergroundDamagePerSecond,
            int[] huntPlacementPoints,
            int[] survivalPlacementPoints,
            List<AceHuntTerritory> territories,
            AceHuntCuboid lockup,
            AceHuntSpawnPoint lockupSpawn,
            Messages messages
    ) {
        this.huntDuration = huntDuration == null || huntDuration.isNegative() || huntDuration.isZero()
                ? AceHuntRules.DEFAULT_HUNT_DURATION
                : huntDuration;
        this.surfaceWarning = surfaceWarning == null || surfaceWarning.isNegative() || surfaceWarning.isZero()
                ? AceHuntRules.DEFAULT_SURFACE_WARNING
                : surfaceWarning;
        this.respawnLock = respawnLock == null || respawnLock.isNegative() || respawnLock.isZero()
                ? AceHuntRules.DEFAULT_RESPAWN_LOCK
                : respawnLock;
        this.undergroundDamagePerSecond = undergroundDamagePerSecond > 0
                ? undergroundDamagePerSecond
                : AceHuntRules.DEFAULT_UNDERGROUND_DAMAGE;
        this.huntPlacementPoints = normalizePoints(huntPlacementPoints);
        this.survivalPlacementPoints = normalizePoints(survivalPlacementPoints);
        Map<String, AceHuntTerritory> bySlug = new LinkedHashMap<>();
        for (AceHuntTerritory territory : territories) {
            bySlug.put(territory.teamSlug(), territory);
        }
        this.territoriesBySlug = Map.copyOf(bySlug);
        this.lockup = Objects.requireNonNullElse(lockup, AceHuntCuboid.undefined());
        this.lockupSpawn = Objects.requireNonNullElse(lockupSpawn, AceHuntSpawnPoint.undefined());
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public Duration huntDuration() {
        return huntDuration;
    }

    public Duration surfaceWarning() {
        return surfaceWarning;
    }

    public Duration respawnLock() {
        return respawnLock;
    }

    public double undergroundDamagePerSecond() {
        return undergroundDamagePerSecond;
    }

    public int huntPointsForPlace(int zeroBased) {
        if (zeroBased < 0 || zeroBased >= huntPlacementPoints.length) {
            return 0;
        }
        return huntPlacementPoints[zeroBased];
    }

    public int survivalPointsForPlace(int zeroBased) {
        if (zeroBased < 0 || zeroBased >= survivalPlacementPoints.length) {
            return 0;
        }
        return survivalPlacementPoints[zeroBased];
    }

    public int[] huntPlacementPoints() {
        return huntPlacementPoints.clone();
    }

    public int[] survivalPlacementPoints() {
        return survivalPlacementPoints.clone();
    }

    public List<AceHuntTerritory> territories() {
        return List.copyOf(territoriesBySlug.values());
    }

    public Optional<AceHuntTerritory> territoryBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(territoriesBySlug.get(slug.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<AceHuntTerritory> territoryByTeamId(UUID teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return territoriesBySlug.values().stream()
                .filter(territory -> territory.teamId().equals(teamId))
                .findFirst();
    }

    public AceHuntCuboid lockup() {
        return lockup;
    }

    public AceHuntSpawnPoint lockupSpawn() {
        return lockupSpawn;
    }

    public Messages messages() {
        return messages;
    }

    public AceHuntConfig withTerritories(List<AceHuntTerritory> updated) {
        return new AceHuntConfig(
                huntDuration,
                surfaceWarning,
                respawnLock,
                undergroundDamagePerSecond,
                huntPlacementPoints,
                survivalPlacementPoints,
                updated,
                lockup,
                lockupSpawn,
                messages
        );
    }

    public AceHuntConfig withLockup(AceHuntCuboid cuboid, AceHuntSpawnPoint spawn) {
        return new AceHuntConfig(
                huntDuration,
                surfaceWarning,
                respawnLock,
                undergroundDamagePerSecond,
                huntPlacementPoints,
                survivalPlacementPoints,
                territories(),
                cuboid,
                spawn,
                messages
        );
    }

    /**
     * Rebinds territory team IDs from current roster by slug. Keeps regions/spawns.
     */
    public AceHuntConfig rebindTeamIds(Map<String, UUID> slugToTeamId) {
        List<AceHuntTerritory> rebound = new ArrayList<>();
        for (AceHuntTerritory territory : territories()) {
            UUID teamId = slugToTeamId.getOrDefault(territory.teamSlug(), territory.teamId());
            rebound.add(new AceHuntTerritory(
                    teamId,
                    territory.teamSlug(),
                    territory.region(),
                    territory.aceSpawn(),
                    territory.hunterSpawn(),
                    territory.surfaceY(),
                    territory.maxLegalY()
            ));
        }
        for (Map.Entry<String, UUID> entry : slugToTeamId.entrySet()) {
            boolean present = rebound.stream().anyMatch(t -> t.teamSlug().equals(entry.getKey()));
            if (!present) {
                rebound.add(AceHuntTerritory.empty(entry.getValue(), entry.getKey()));
            }
        }
        return withTerritories(rebound);
    }

    public static AceHuntConfig defaults() {
        return new AceHuntConfig(
                AceHuntRules.DEFAULT_HUNT_DURATION,
                AceHuntRules.DEFAULT_SURFACE_WARNING,
                AceHuntRules.DEFAULT_RESPAWN_LOCK,
                AceHuntRules.DEFAULT_UNDERGROUND_DAMAGE,
                new int[] {AceHuntRules.POINTS_FIRST, AceHuntRules.POINTS_SECOND, AceHuntRules.POINTS_THIRD},
                new int[] {AceHuntRules.POINTS_FIRST, AceHuntRules.POINTS_SECOND, AceHuntRules.POINTS_THIRD},
                List.of(),
                AceHuntCuboid.undefined(),
                AceHuntSpawnPoint.undefined(),
                Messages.defaults()
        );
    }

    public static AceHuntConfig from(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("ace-hunt");
        if (root == null) {
            return defaults();
        }
        Duration hunt = Duration.ofSeconds(Math.max(1, root.getInt(
                "hunt-seconds", (int) AceHuntRules.DEFAULT_HUNT_DURATION.toSeconds())));
        Duration surface = Duration.ofSeconds(Math.max(1, root.getInt(
                "surface-warning-seconds", (int) AceHuntRules.DEFAULT_SURFACE_WARNING.toSeconds())));
        Duration lock = Duration.ofSeconds(Math.max(1, root.getInt(
                "respawn-lock-seconds", (int) AceHuntRules.DEFAULT_RESPAWN_LOCK.toSeconds())));
        double damage = root.getDouble("underground-damage-per-second", AceHuntRules.DEFAULT_UNDERGROUND_DAMAGE);

        int[] huntPoints = readPoints(root.getConfigurationSection("points.hunt"),
                new int[] {2, 1, 0});
        int[] survivalPoints = readPoints(root.getConfigurationSection("points.survival"),
                new int[] {2, 1, 0});

        List<AceHuntTerritory> territories = new ArrayList<>();
        List<Map<?, ?>> rows = root.getMapList("territories");
        for (Map<?, ?> row : rows) {
            AceHuntTerritory territory = readTerritory(row);
            if (territory != null) {
                territories.add(territory);
            }
        }

        AceHuntCuboid lockup = readCuboid(root.getConfigurationSection("lockup"));
        AceHuntSpawnPoint lockupSpawn = readSpawn(root.getConfigurationSection("lockup.spawn"));
        Messages messages = Messages.from(root.getConfigurationSection("messages"));

        return new AceHuntConfig(
                hunt, surface, lock, damage, huntPoints, survivalPoints,
                territories, lockup, lockupSpawn, messages
        );
    }

    private static int[] normalizePoints(int[] points) {
        if (points == null || points.length == 0) {
            return new int[] {AceHuntRules.POINTS_FIRST, AceHuntRules.POINTS_SECOND, AceHuntRules.POINTS_THIRD};
        }
        int[] copy = new int[Math.max(3, points.length)];
        System.arraycopy(points, 0, copy, 0, points.length);
        return copy;
    }

    private static int[] readPoints(@Nullable ConfigurationSection section, int[] fallback) {
        if (section == null) {
            return fallback.clone();
        }
        return new int[] {
                section.getInt("first", fallback[0]),
                section.getInt("second", fallback[1]),
                section.getInt("third", fallback[2])
        };
    }

    private static @Nullable AceHuntTerritory readTerritory(Map<?, ?> row) {
        Object slugObj = row.get("team");
        if (slugObj == null) {
            slugObj = row.get("slug");
        }
        if (slugObj == null) {
            return null;
        }
        String slug = String.valueOf(slugObj).trim().toLowerCase(Locale.ROOT);
        if (slug.isBlank()) {
            return null;
        }
        UUID teamId = UUID.nameUUIDFromBytes(("ace-hunt-territory:" + slug).getBytes());
        Object idObj = row.get("team-id");
        if (idObj != null && !String.valueOf(idObj).isBlank()) {
            try {
                teamId = UUID.fromString(String.valueOf(idObj).trim());
            } catch (IllegalArgumentException ignored) {
                // keep deterministic placeholder; rebind on enable
            }
        }
        AceHuntCuboid region = readCuboidMap(row);
        AceHuntSpawnPoint aceSpawn = readSpawnMap(asMap(row.get("ace-spawn")));
        AceHuntSpawnPoint hunterSpawn = readSpawnMap(asMap(row.get("hunter-spawn")));
        Integer surfaceY = intOrNull(row.get("surface-y"));
        Integer maxLegalY = intOrNull(row.get("max-legal-y"));
        return new AceHuntTerritory(teamId, slug, region, aceSpawn, hunterSpawn, surfaceY, maxLegalY);
    }

    private static AceHuntCuboid readCuboid(@Nullable ConfigurationSection section) {
        if (section == null) {
            return AceHuntCuboid.undefined();
        }
        String world = blankToNull(section.getString("world"));
        if (world == null || !section.contains("min-x")) {
            return AceHuntCuboid.undefined();
        }
        return new AceHuntCuboid(
                world,
                section.getInt("min-x"),
                section.getInt("min-y"),
                section.getInt("min-z"),
                section.getInt("max-x"),
                section.getInt("max-y"),
                section.getInt("max-z")
        );
    }

    private static AceHuntCuboid readCuboidMap(Map<?, ?> row) {
        String world = blankToNull(stringOrNull(row.get("world")));
        if (world == null || row.get("min-x") == null) {
            return AceHuntCuboid.undefined();
        }
        return new AceHuntCuboid(
                world,
                intOrNull(row.get("min-x")),
                intOrNull(row.get("min-y")),
                intOrNull(row.get("min-z")),
                intOrNull(row.get("max-x")),
                intOrNull(row.get("max-y")),
                intOrNull(row.get("max-z"))
        );
    }

    private static AceHuntSpawnPoint readSpawn(@Nullable ConfigurationSection section) {
        if (section == null) {
            return AceHuntSpawnPoint.undefined();
        }
        String world = blankToNull(section.getString("world"));
        if (world == null || !section.contains("x")) {
            return AceHuntSpawnPoint.undefined();
        }
        return new AceHuntSpawnPoint(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
    }

    private static AceHuntSpawnPoint readSpawnMap(@Nullable Map<?, ?> row) {
        if (row == null) {
            return AceHuntSpawnPoint.undefined();
        }
        String world = blankToNull(stringOrNull(row.get("world")));
        if (world == null || row.get("x") == null) {
            return AceHuntSpawnPoint.undefined();
        }
        return new AceHuntSpawnPoint(
                world,
                doubleOrNull(row.get("x")),
                doubleOrNull(row.get("y")),
                doubleOrNull(row.get("z")),
                floatOrZero(row.get("yaw")),
                floatOrZero(row.get("pitch"))
        );
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<?, ?> asMap(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return null;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static @Nullable String stringOrNull(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static @Nullable Integer intOrNull(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static @Nullable Double doubleOrNull(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static float floatOrZero(@Nullable Object value) {
        Double parsed = doubleOrNull(value);
        return parsed == null ? 0.0f : parsed.floatValue();
    }

    public record Messages(
            String prepStarted,
            String huntStarted,
            String surfaceWarning,
            String undergroundBan,
            String aceDied,
            String huntScored,
            String huntMissed,
            String eventEnded,
            String lockupEnter,
            String lockupRelease
    ) {
        public static Messages defaults() {
            return new Messages(
                    "[LarpSMP] Ace Hunt prep: aces teleported to their territories.",
                    "[LarpSMP] Ace Hunt has begun! Hunt your assigned ace.",
                    "[LarpSMP] Surface now — underground will be banned in {seconds}s.",
                    "[LarpSMP] Underground is banned. Get to the surface!",
                    "[LarpSMP] {ace} ({team}) has fallen!",
                    "[LarpSMP] {hunter} scored the hunt on {ace}!",
                    "[LarpSMP] {ace} died, but not to their assigned hunters.",
                    "[LarpSMP] Ace Hunt ended. Final points are on the boss bar.",
                    "[LarpSMP] You are locked for {seconds}s.",
                    "[LarpSMP] Respawn lock ended. Hunt continues!"
            );
        }

        public static Messages from(@Nullable ConfigurationSection section) {
            Messages defaults = defaults();
            if (section == null) {
                return defaults;
            }
            return new Messages(
                    section.getString("prep-started", defaults.prepStarted()),
                    section.getString("hunt-started", defaults.huntStarted()),
                    section.getString("surface-warning", defaults.surfaceWarning()),
                    section.getString("underground-ban", defaults.undergroundBan()),
                    section.getString("ace-died", defaults.aceDied()),
                    section.getString("hunt-scored", defaults.huntScored()),
                    section.getString("hunt-missed", defaults.huntMissed()),
                    section.getString("event-ended", defaults.eventEnded()),
                    section.getString("lockup-enter", defaults.lockupEnter()),
                    section.getString("lockup-release", defaults.lockupRelease())
            );
        }
    }
}
