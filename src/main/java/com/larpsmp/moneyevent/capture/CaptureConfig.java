package com.larpsmp.moneyevent.capture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Typed capture settings from {@code config.yml}.
 */
public final class CaptureConfig {

    private final int tickSeconds;
    private final int heightBlocks;
    private final int tempBlockSeconds;
    private final double decayMultiplier;
    private final Map<Integer, Integer> durationsSecondsByAdvantage;
    private final List<CapturePointDefinition> points;
    private final Messages messages;

    public CaptureConfig(
            int tickSeconds,
            int heightBlocks,
            int tempBlockSeconds,
            double decayMultiplier,
            Map<Integer, Integer> durationsSecondsByAdvantage,
            List<CapturePointDefinition> points,
            Messages messages) {
        this.tickSeconds = Math.max(1, tickSeconds);
        this.heightBlocks = Math.max(1, heightBlocks);
        this.tempBlockSeconds = Math.max(1, tempBlockSeconds);
        this.decayMultiplier = decayMultiplier > 0 ? decayMultiplier : CaptureRules.DEFAULT_DECAY_MULTIPLIER;
        this.durationsSecondsByAdvantage = Map.copyOf(durationsSecondsByAdvantage);
        this.points = List.copyOf(points);
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public int tickSeconds() {
        return tickSeconds;
    }

    public int heightBlocks() {
        return heightBlocks;
    }

    public int tempBlockSeconds() {
        return tempBlockSeconds;
    }

    public double decayMultiplier() {
        return decayMultiplier;
    }

    public List<CapturePointDefinition> points() {
        return points;
    }

    public Messages messages() {
        return messages;
    }

    public Optional<CapturePointDefinition> point(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String needle = id.trim().toLowerCase(Locale.ROOT);
        return points.stream().filter(point -> point.id().equals(needle)).findFirst();
    }

    public CaptureConfig withPoints(List<CapturePointDefinition> updatedPoints) {
        return new CaptureConfig(
                tickSeconds,
                heightBlocks,
                tempBlockSeconds,
                decayMultiplier,
                durationsSecondsByAdvantage,
                updatedPoints,
                messages);
    }

    /**
     * Full capture duration in milliseconds for the given advantage (lead − runner-up).
     */
    public long fullCaptureMillis(int advantage) {
        int clamped = Math.max(1, advantage);
        Integer configured = durationsSecondsByAdvantage.get(clamped);
        if (configured != null && configured > 0) {
            return configured * 1000L;
        }
        // Fallback: use closest lower configured advantage, else base 10 minutes.
        Integer best = null;
        for (Map.Entry<Integer, Integer> entry : durationsSecondsByAdvantage.entrySet()) {
            if (entry.getKey() <= clamped && entry.getValue() != null && entry.getValue() > 0) {
                if (best == null || entry.getKey() > best) {
                    best = entry.getKey();
                }
            }
        }
        if (best != null) {
            return durationsSecondsByAdvantage.get(best) * 1000L;
        }
        return CaptureRules.BASE_CAPTURE_DURATION.toMillis();
    }

    public long decayFullMillis() {
        long base = fullCaptureMillis(1);
        return Math.max(1L, Math.round(base / decayMultiplier));
    }

    public static CaptureConfig from(FileConfiguration config) {
        ConfigurationSection root = config.getConfigurationSection("capture");
        if (root == null) {
            return defaults();
        }
        int tickSeconds = root.getInt("tick-seconds", CaptureRules.TICK_SECONDS_DEFAULT);
        int heightBlocks = root.getInt("height-blocks", CaptureRules.HEIGHT_BLOCKS);
        int tempBlockSeconds = root.getInt("temp-block-seconds", (int) CaptureRules.TEMP_BLOCK_TTL.toSeconds());
        double decayMultiplier = root.getDouble("decay-multiplier", CaptureRules.DEFAULT_DECAY_MULTIPLIER);

        Map<Integer, Integer> durations = new LinkedHashMap<>();
        ConfigurationSection durationSection = root.getConfigurationSection("durations-seconds");
        if (durationSection != null) {
            for (String key : durationSection.getKeys(false)) {
                try {
                    int advantage = Integer.parseInt(key);
                    int seconds = durationSection.getInt(key);
                    if (advantage >= 1 && seconds > 0) {
                        durations.put(advantage, seconds);
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed keys
                }
            }
        }
        if (durations.isEmpty()) {
            durations.putAll(defaultDurations());
        }

        List<CapturePointDefinition> points = new ArrayList<>();
        List<Map<?, ?>> rawPoints = root.getMapList("points");
        for (Map<?, ?> raw : rawPoints) {
            CapturePointDefinition parsed = parsePoint(raw);
            if (parsed != null) {
                points.add(parsed);
            }
        }
        if (points.isEmpty()) {
            points.addAll(defaultPoints());
        }

        ConfigurationSection messagesSection = root.getConfigurationSection("messages");
        Messages messages = Messages.from(messagesSection);
        return new CaptureConfig(
                tickSeconds,
                heightBlocks,
                tempBlockSeconds,
                decayMultiplier,
                durations,
                points,
                messages);
    }

    public static CaptureConfig defaults() {
        return new CaptureConfig(
                CaptureRules.TICK_SECONDS_DEFAULT,
                CaptureRules.HEIGHT_BLOCKS,
                (int) CaptureRules.TEMP_BLOCK_TTL.toSeconds(),
                CaptureRules.DEFAULT_DECAY_MULTIPLIER,
                defaultDurations(),
                defaultPoints(),
                Messages.defaults());
    }

    private static Map<Integer, Integer> defaultDurations() {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        map.put(1, 600);
        map.put(2, 420);
        map.put(3, 300);
        map.put(4, 210);
        return map;
    }

    private static List<CapturePointDefinition> defaultPoints() {
        return List.of(
                new CapturePointDefinition("quarry", "Quarry", null, null, null, null, null, null, null),
                new CapturePointDefinition("tower", "Tower", null, null, null, null, null, null, null),
                new CapturePointDefinition("ruins", "Ruins", null, null, null, null, null, null, null)
        );
    }

    private static @Nullable CapturePointDefinition parsePoint(Map<?, ?> raw) {
        Object idObj = raw.get("id");
        if (idObj == null) {
            return null;
        }
        String id = String.valueOf(idObj).trim();
        if (id.isEmpty()) {
            return null;
        }
        Object nameObj = raw.get("name");
        String name = nameObj == null ? id : String.valueOf(nameObj).trim();
        if (name.isEmpty()) {
            name = id;
        }
        String world = stringOrNull(raw.get("world"));
        Integer minX = intOrNull(raw.get("min-x"));
        Integer minY = intOrNull(raw.get("min-y"));
        Integer minZ = intOrNull(raw.get("min-z"));
        Integer maxX = intOrNull(raw.get("max-x"));
        Integer maxY = intOrNull(raw.get("max-y"));
        Integer maxZ = intOrNull(raw.get("max-z"));
        return new CapturePointDefinition(id, name, world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static @Nullable String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static @Nullable Integer intOrNull(Object value) {
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

    public record Messages(String open, String threshold, String complete, String paused, String resumed, String stopped) {
        public Messages {
            open = requireMessage(open, "open");
            threshold = requireMessage(threshold, "threshold");
            complete = requireMessage(complete, "complete");
            paused = requireMessage(paused, "paused");
            resumed = requireMessage(resumed, "resumed");
            stopped = requireMessage(stopped, "stopped");
        }

        static Messages defaults() {
            return new Messages(
                    "[LarpSMP] {point} is now open! Capture reward: $50 for your team.",
                    "[LarpSMP] {point}: {team} {percent}%",
                    "[LarpSMP] {team} captured {point}! $50 split among the roster.",
                    "[LarpSMP] Capture paused.",
                    "[LarpSMP] Capture resumed.",
                    "[LarpSMP] Capture stopped."
            );
        }

        static Messages from(@Nullable ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            Messages defaults = defaults();
            return new Messages(
                    section.getString("open", defaults.open()),
                    section.getString("threshold", defaults.threshold()),
                    section.getString("complete", defaults.complete()),
                    section.getString("paused", defaults.paused()),
                    section.getString("resumed", defaults.resumed()),
                    section.getString("stopped", defaults.stopped())
            );
        }

        private static String requireMessage(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("messages." + field + " cannot be blank");
            }
            return value;
        }
    }
}
