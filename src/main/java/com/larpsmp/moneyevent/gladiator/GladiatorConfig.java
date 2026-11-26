package com.larpsmp.moneyevent.gladiator;

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
 * Typed Gladiators settings from {@code config.yml}.
 */
public final class GladiatorConfig {

    private final long[] placementPrizes;
    private final GladiatorCuboid arena;
    private final GladiatorCuboid seats;
    private final GladiatorCuboid barriers;
    private final Map<String, List<GladiatorSpawnPoint>> teamSpawnsBySlug;
    private final GladiatorSpawnPoint duelSpawnA;
    private final GladiatorSpawnPoint duelSpawnB;
    private final GladiatorSpawnPoint seatsSpawn;
    private final Messages messages;

    public GladiatorConfig(
            long[] placementPrizes,
            GladiatorCuboid arena,
            GladiatorCuboid seats,
            GladiatorCuboid barriers,
            Map<String, List<GladiatorSpawnPoint>> teamSpawnsBySlug,
            GladiatorSpawnPoint duelSpawnA,
            GladiatorSpawnPoint duelSpawnB,
            GladiatorSpawnPoint seatsSpawn,
            Messages messages
    ) {
        this.placementPrizes = normalizePrizes(placementPrizes);
        this.arena = Objects.requireNonNullElse(arena, GladiatorCuboid.undefined());
        this.seats = Objects.requireNonNullElse(seats, GladiatorCuboid.undefined());
        this.barriers = Objects.requireNonNullElse(barriers, GladiatorCuboid.undefined());
        Map<String, List<GladiatorSpawnPoint>> spawns = new LinkedHashMap<>();
        if (teamSpawnsBySlug != null) {
            for (Map.Entry<String, List<GladiatorSpawnPoint>> entry : teamSpawnsBySlug.entrySet()) {
                List<GladiatorSpawnPoint> pads = new ArrayList<>(GladiatorRules.TEAM_FIGHT_SIZE);
                List<GladiatorSpawnPoint> source = entry.getValue() == null ? List.of() : entry.getValue();
                for (int i = 0; i < GladiatorRules.TEAM_FIGHT_SIZE; i++) {
                    pads.add(i < source.size() ? source.get(i) : GladiatorSpawnPoint.undefined());
                }
                spawns.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(pads));
            }
        }
        this.teamSpawnsBySlug = Map.copyOf(spawns);
        this.duelSpawnA = Objects.requireNonNullElse(duelSpawnA, GladiatorSpawnPoint.undefined());
        this.duelSpawnB = Objects.requireNonNullElse(duelSpawnB, GladiatorSpawnPoint.undefined());
        this.seatsSpawn = Objects.requireNonNullElse(seatsSpawn, GladiatorSpawnPoint.undefined());
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public long[] placementPrizes() {
        return placementPrizes.clone();
    }

    public long prizeForPlace(int zeroBased) {
        return GladiatorPlacements.prizeForPlace(zeroBased, placementPrizes);
    }

    public GladiatorCuboid arena() {
        return arena;
    }

    public GladiatorCuboid seats() {
        return seats;
    }

    public GladiatorCuboid barriers() {
        return barriers;
    }

    public Map<String, List<GladiatorSpawnPoint>> teamSpawnsBySlug() {
        return teamSpawnsBySlug;
    }

    public Optional<List<GladiatorSpawnPoint>> teamSpawns(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(teamSpawnsBySlug.get(slug.trim().toLowerCase(Locale.ROOT)));
    }

    public GladiatorSpawnPoint duelSpawnA() {
        return duelSpawnA;
    }

    public GladiatorSpawnPoint duelSpawnB() {
        return duelSpawnB;
    }

    public GladiatorSpawnPoint seatsSpawn() {
        return seatsSpawn;
    }

    public Messages messages() {
        return messages;
    }

    public GladiatorConfig withArena(GladiatorCuboid cuboid) {
        return copy(cuboid, seats, barriers, teamSpawnsBySlug, duelSpawnA, duelSpawnB, seatsSpawn);
    }

    public GladiatorConfig withSeats(GladiatorCuboid cuboid) {
        return copy(arena, cuboid, barriers, teamSpawnsBySlug, duelSpawnA, duelSpawnB, seatsSpawn);
    }

    public GladiatorConfig withBarriers(GladiatorCuboid cuboid) {
        return copy(arena, seats, cuboid, teamSpawnsBySlug, duelSpawnA, duelSpawnB, seatsSpawn);
    }

    public GladiatorConfig withTeamSpawn(String slug, int slotOneBased, GladiatorSpawnPoint spawn) {
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(spawn, "spawn");
        if (slotOneBased < 1 || slotOneBased > GladiatorRules.TEAM_FIGHT_SIZE) {
            throw new IllegalArgumentException("slot must be 1–" + GladiatorRules.TEAM_FIGHT_SIZE);
        }
        Map<String, List<GladiatorSpawnPoint>> updated = new LinkedHashMap<>(teamSpawnsBySlug);
        String key = slug.trim().toLowerCase(Locale.ROOT);
        List<GladiatorSpawnPoint> pads = new ArrayList<>(
                updated.getOrDefault(key, defaultPads()));
        while (pads.size() < GladiatorRules.TEAM_FIGHT_SIZE) {
            pads.add(GladiatorSpawnPoint.undefined());
        }
        pads.set(slotOneBased - 1, spawn);
        updated.put(key, List.copyOf(pads));
        return copy(arena, seats, barriers, updated, duelSpawnA, duelSpawnB, seatsSpawn);
    }

    public GladiatorConfig withDuelSpawns(GladiatorSpawnPoint a, GladiatorSpawnPoint b) {
        return copy(arena, seats, barriers, teamSpawnsBySlug, a, b, seatsSpawn);
    }

    public GladiatorConfig withSeatsSpawn(GladiatorSpawnPoint spawn) {
        return copy(arena, seats, barriers, teamSpawnsBySlug, duelSpawnA, duelSpawnB, spawn);
    }

    public GladiatorConfig withPrizes(long[] prizes) {
        return new GladiatorConfig(
                prizes, arena, seats, barriers, teamSpawnsBySlug, duelSpawnA, duelSpawnB, seatsSpawn, messages);
    }

    private GladiatorConfig copy(
            GladiatorCuboid arenaCuboid,
            GladiatorCuboid seatsCuboid,
            GladiatorCuboid barriersCuboid,
            Map<String, List<GladiatorSpawnPoint>> spawns,
            GladiatorSpawnPoint duelA,
            GladiatorSpawnPoint duelB,
            GladiatorSpawnPoint seatsPad
    ) {
        return new GladiatorConfig(
                placementPrizes,
                arenaCuboid,
                seatsCuboid,
                barriersCuboid,
                spawns,
                duelA,
                duelB,
                seatsPad,
                messages);
    }

    public static GladiatorConfig defaults() {
        return new GladiatorConfig(
                new long[] {
                        GladiatorRules.DEFAULT_PRIZE_FIRST,
                        GladiatorRules.DEFAULT_PRIZE_SECOND,
                        GladiatorRules.DEFAULT_PRIZE_THIRD
                },
                GladiatorCuboid.undefined(),
                GladiatorCuboid.undefined(),
                GladiatorCuboid.undefined(),
                Map.of(),
                GladiatorSpawnPoint.undefined(),
                GladiatorSpawnPoint.undefined(),
                GladiatorSpawnPoint.undefined(),
                Messages.defaults()
        );
    }

    public static GladiatorConfig from(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("gladiators");
        if (root == null) {
            return defaults();
        }
        long[] prizes = readPrizes(root.getConfigurationSection("prizes"));
        GladiatorCuboid arena = readCuboid(root.getConfigurationSection("arena"));
        GladiatorCuboid seats = readCuboid(root.getConfigurationSection("seats"));
        GladiatorCuboid barriers = readCuboid(root.getConfigurationSection("barriers"));

        Map<String, List<GladiatorSpawnPoint>> teamSpawns = new LinkedHashMap<>();
        ConfigurationSection spawnsSection = root.getConfigurationSection("team-spawns");
        if (spawnsSection != null) {
            for (String slug : spawnsSection.getKeys(false)) {
                List<?> raw = spawnsSection.getList(slug);
                List<GladiatorSpawnPoint> pads = new ArrayList<>();
                if (raw != null) {
                    for (Object entry : raw) {
                        if (entry instanceof Map<?, ?> map) {
                            pads.add(readSpawnMap(map));
                        }
                    }
                }
                teamSpawns.put(slug.toLowerCase(Locale.ROOT), pads);
            }
        }

        GladiatorSpawnPoint duelA = readSpawn(root.getConfigurationSection("duel-spawns.a"));
        GladiatorSpawnPoint duelB = readSpawn(root.getConfigurationSection("duel-spawns.b"));
        GladiatorSpawnPoint seatsSpawn = readSpawn(root.getConfigurationSection("seats-spawn"));
        Messages messages = Messages.from(root.getConfigurationSection("messages"));

        return new GladiatorConfig(
                prizes, arena, seats, barriers, teamSpawns, duelA, duelB, seatsSpawn, messages);
    }

    private static List<GladiatorSpawnPoint> defaultPads() {
        List<GladiatorSpawnPoint> pads = new ArrayList<>(GladiatorRules.TEAM_FIGHT_SIZE);
        for (int i = 0; i < GladiatorRules.TEAM_FIGHT_SIZE; i++) {
            pads.add(GladiatorSpawnPoint.undefined());
        }
        return pads;
    }

    private static long[] normalizePrizes(long[] prizes) {
        long[] defaults = {
                GladiatorRules.DEFAULT_PRIZE_FIRST,
                GladiatorRules.DEFAULT_PRIZE_SECOND,
                GladiatorRules.DEFAULT_PRIZE_THIRD
        };
        if (prizes == null || prizes.length == 0) {
            return defaults;
        }
        long[] copy = new long[Math.max(3, prizes.length)];
        System.arraycopy(defaults, 0, copy, 0, defaults.length);
        System.arraycopy(prizes, 0, copy, 0, prizes.length);
        for (int i = 0; i < copy.length; i++) {
            if (copy[i] < 0) {
                copy[i] = 0;
            }
        }
        return copy;
    }

    private static long[] readPrizes(@Nullable ConfigurationSection section) {
        if (section == null) {
            return new long[] {
                    GladiatorRules.DEFAULT_PRIZE_FIRST,
                    GladiatorRules.DEFAULT_PRIZE_SECOND,
                    GladiatorRules.DEFAULT_PRIZE_THIRD
            };
        }
        return new long[] {
                Math.max(0L, section.getLong("first", GladiatorRules.DEFAULT_PRIZE_FIRST)),
                Math.max(0L, section.getLong("second", GladiatorRules.DEFAULT_PRIZE_SECOND)),
                Math.max(0L, section.getLong("third", GladiatorRules.DEFAULT_PRIZE_THIRD))
        };
    }

    private static GladiatorCuboid readCuboid(@Nullable ConfigurationSection section) {
        if (section == null) {
            return GladiatorCuboid.undefined();
        }
        String world = blankToNull(section.getString("world"));
        if (world == null || !section.contains("min-x")) {
            return GladiatorCuboid.undefined();
        }
        return new GladiatorCuboid(
                world,
                section.getInt("min-x"),
                section.getInt("min-y"),
                section.getInt("min-z"),
                section.getInt("max-x"),
                section.getInt("max-y"),
                section.getInt("max-z")
        );
    }

    private static GladiatorSpawnPoint readSpawn(@Nullable ConfigurationSection section) {
        if (section == null) {
            return GladiatorSpawnPoint.undefined();
        }
        String world = blankToNull(section.getString("world"));
        if (world == null || !section.contains("x")) {
            return GladiatorSpawnPoint.undefined();
        }
        return new GladiatorSpawnPoint(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0)
        );
    }

    private static GladiatorSpawnPoint readSpawnMap(@Nullable Map<?, ?> row) {
        if (row == null) {
            return GladiatorSpawnPoint.undefined();
        }
        String world = blankToNull(stringOrNull(row.get("world")));
        if (world == null || row.get("x") == null) {
            return GladiatorSpawnPoint.undefined();
        }
        return new GladiatorSpawnPoint(
                world,
                doubleOrNull(row.get("x")),
                doubleOrNull(row.get("y")),
                doubleOrNull(row.get("z")),
                floatOrZero(row.get("yaw")),
                floatOrZero(row.get("pitch"))
        );
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
            String sessionOpened,
            String sessionStopped,
            String fightStarted,
            String fightWon,
            String fightDraw,
            String fightSkipped,
            String duelNominated,
            String kitBanned,
            String duelStarted,
            String payoutDone
    ) {
        public static Messages defaults() {
            return new Messages(
                    "[LarpSMP] Gladiators is open. Admins will run the fight schedule.",
                    "[LarpSMP] Gladiators ended. Bodies restored.",
                    "[LarpSMP] Fight started: {teamA} vs {teamB}.",
                    "[LarpSMP] {team} wins the fight! (+1 point)",
                    "[LarpSMP] Fight ended in a draw — no points awarded.",
                    "[LarpSMP] Schedule bout skipped.",
                    "[LarpSMP] 1v1 nominated: {a} vs {b}. Ban kits until one remains.",
                    "[LarpSMP] {player} banned kit {kit}. Remaining: {remaining}",
                    "[LarpSMP] 1v1 started with kit {kit}: {a} vs {b}.",
                    "[LarpSMP] Gladiators prizes paid."
            );
        }

        public static Messages from(@Nullable ConfigurationSection section) {
            Messages defaults = defaults();
            if (section == null) {
                return defaults;
            }
            return new Messages(
                    section.getString("session-opened", defaults.sessionOpened()),
                    section.getString("session-stopped", defaults.sessionStopped()),
                    section.getString("fight-started", defaults.fightStarted()),
                    section.getString("fight-won", defaults.fightWon()),
                    section.getString("fight-draw", defaults.fightDraw()),
                    section.getString("fight-skipped", defaults.fightSkipped()),
                    section.getString("duel-nominated", defaults.duelNominated()),
                    section.getString("kit-banned", defaults.kitBanned()),
                    section.getString("duel-started", defaults.duelStarted()),
                    section.getString("payout-done", defaults.payoutDone())
            );
        }
    }
}
