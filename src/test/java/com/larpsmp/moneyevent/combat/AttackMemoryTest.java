package com.larpsmp.moneyevent.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttackMemoryTest {

    @Test
    void lastWriteWinsWithinWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AttackMemory memory = new AttackMemory(Duration.ofSeconds(60), clock);
        UUID victim = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        memory.record(victim, first, AttributionCause.MELEE, null);
        clock.advance(Duration.ofSeconds(10));
        memory.record(victim, second, AttributionCause.PROJECTILE, "ARROW");

        AttackRecord record = memory.lastAttacker(victim).orElseThrow();
        assertEquals(second, record.attackerMinecraftUuid());
        assertEquals(AttributionCause.PROJECTILE, record.cause());
    }

    @Test
    void creditsAtExactWindowEndAndExpiresAfter() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        AttackMemory memory = new AttackMemory(Duration.ofSeconds(60), clock);
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        memory.record(victim, new AttackRecord(attacker, AttributionCause.MELEE, start, null));

        clock.set(start.plusSeconds(60));
        assertTrue(memory.lastAttacker(victim).isPresent());

        clock.set(start.plusSeconds(60).plusMillis(1));
        assertTrue(memory.lastAttacker(victim).isEmpty());
    }

    @Test
    void clearRemovesVictimEntry() {
        AttackMemory memory = new AttackMemory();
        UUID victim = UUID.randomUUID();
        memory.record(victim, UUID.randomUUID(), AttributionCause.PET, "WOLF");
        memory.clear(victim);
        assertTrue(memory.lastAttacker(victim).isEmpty());
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
