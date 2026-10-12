package com.larpsmp.moneyevent.team;

import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Competitor strength tiers used for roster balancing and (later) death costs.
 * Tier 1 is strongest; Tier 4 is weakest.
 *
 * <p>UI maps {@link #rgb()} to Adventure {@code TextColor} in dialog code only,
 * so the domain layer stays free of Paper/Adventure runtime dependencies in tests.
 */
public enum PlayerTier {
    TIER_1(1, "Tier 1", 0xE54545),
    TIER_2(2, "Tier 2", 0xE59530),
    TIER_3(3, "Tier 3", 0x3DB8E0),
    TIER_4(4, "Tier 4", 0x9AA0A6);

    private final int value;
    private final String displayName;
    private final int rgb;

    PlayerTier(int value, String displayName, int rgb) {
        this.value = value;
        this.displayName = displayName;
        this.rgb = rgb;
    }

    public int value() {
        return value;
    }

    public String displayName() {
        return displayName;
    }

    public int rgb() {
        return rgb;
    }

    public String shortLabel() {
        return "T" + value;
    }

    public static Optional<PlayerTier> fromValue(int value) {
        for (PlayerTier tier : values()) {
            if (tier.value == value) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public static Optional<PlayerTier> fromToken(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("tier")) {
            trimmed = trimmed.substring(4).trim();
        }
        if (trimmed.startsWith("t") && trimmed.length() > 1 && Character.isDigit(trimmed.charAt(1))) {
            trimmed = trimmed.substring(1);
        }
        try {
            return fromValue(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
