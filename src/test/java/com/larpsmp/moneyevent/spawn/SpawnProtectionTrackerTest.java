package com.larpsmp.moneyevent.spawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpawnProtectionTrackerTest {

    @Test
    void grantProtectsUntilExactExpiryThenExpires() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        SpawnProtectionTracker tracker = new SpawnProtectionTracker(Duration.ofSeconds(10), clock);
        UUID player = UUID.randomUUID();

        tracker.grant(player);
        assertTrue(tracker.isProtected(player));

        clock.set(start.plusSeconds(10));
        assertTrue(tracker.isProtected(player));

        clock.set(start.plusSeconds(10).plusMillis(1));
        assertFalse(tracker.isProtected(player));
    }

    @Test
    void clearRemovesProtection() {
        SpawnProtectionTracker tracker = new SpawnProtectionTracker(Duration.ofSeconds(10));
        UUID player = UUID.randomUUID();
        tracker.grant(player);
        assertTrue(tracker.isProtected(player));
        tracker.clear(player);
        assertFalse(tracker.isProtected(player));
    }

    @Test
    void clearOnAttackEndsProtectionEarly() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        SpawnProtectionTracker tracker = new SpawnProtectionTracker(Duration.ofSeconds(10), clock);
        UUID attacker = UUID.randomUUID();
        tracker.grant(attacker);
        clock.advance(Duration.ofSeconds(3));
        assertTrue(tracker.isProtected(attacker));
        tracker.clear(attacker);
        assertFalse(tracker.isProtected(attacker));
    }

    @Test
    void unknownPlayerIsNotProtected() {
        SpawnProtectionTracker tracker = new SpawnProtectionTracker(Duration.ofSeconds(10));
        assertFalse(tracker.isProtected(UUID.randomUUID()));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
