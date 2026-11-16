package com.larpsmp.moneyevent.capture;

import java.time.Duration;
import java.util.List;

/**
 * Confirmed capture-point constants and playtest defaults.
 */
public final class CaptureRules {

    public static final int HEIGHT_BLOCKS = 3;
    public static final Duration TEMP_BLOCK_TTL = Duration.ofSeconds(30);
    public static final Duration BASE_CAPTURE_DURATION = Duration.ofMinutes(10);
    public static final double DEFAULT_DECAY_MULTIPLIER = 2.0;
    public static final int TICK_SECONDS_DEFAULT = 1;
    public static final List<Integer> THRESHOLD_PERCENTS = List.of(25, 50, 75, 100);

    private CaptureRules() {
    }
}
