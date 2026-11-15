package com.larpsmp.moneyevent.team;

import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Named team colors stored in the database.
 *
 * <p>UI styling maps these keys to Adventure {@code TextColor} values in dialog code only,
 * so the domain layer stays free of Paper/Adventure runtime dependencies in tests.
 */
public enum TeamColor {
    BLUE("blue", "Blue", 0x5555FF),
    RED("red", "Red", 0xFF5555),
    YELLOW("yellow", "Yellow", 0xFFFF55),
    GREEN("green", "Green", 0x55FF55),
    AQUA("aqua", "Aqua", 0x55FFFF),
    GOLD("gold", "Gold", 0xFFAA00),
    LIGHT_PURPLE("light_purple", "Light Purple", 0xFF55FF),
    WHITE("white", "White", 0xFFFFFF),
    GRAY("gray", "Gray", 0xAAAAAA);

    private final String key;
    private final String displayName;
    private final int rgb;

    TeamColor(String key, String displayName, int rgb) {
        this.key = key;
        this.displayName = displayName;
        this.rgb = rgb;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** Packed 0xRRGGBB color for Adventure {@code TextColor.color(rgb)}. */
    public int rgb() {
        return rgb;
    }

    public static Optional<TeamColor> fromKey(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (TeamColor color : values()) {
            if (color.key.equals(normalized) || color.name().equalsIgnoreCase(normalized)) {
                return Optional.of(color);
            }
        }
        return Optional.empty();
    }

    public static TeamColor requireKey(String raw) {
        return fromKey(raw).orElseThrow(() -> new IllegalArgumentException("Unknown team color: " + raw));
    }
}
