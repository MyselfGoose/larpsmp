package com.larpsmp.moneyevent.combat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory last-attacker tracker. Active combat memory only needs the attribution window.
 */
public final class AttackMemory {

    private final ConcurrentHashMap<UUID, AttackRecord> byVictim = new ConcurrentHashMap<>();
    private final Duration window;
    private final Clock clock;

    public AttackMemory() {
        this(CombatRules.ATTRIBUTION_WINDOW, Clock.systemUTC());
    }

    public AttackMemory(Duration window, Clock clock) {
        this.window = Objects.requireNonNull(window, "window");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void record(UUID victimMinecraftUuid, AttackRecord record) {
        Objects.requireNonNull(victimMinecraftUuid, "victimMinecraftUuid");
        Objects.requireNonNull(record, "record");
        byVictim.put(victimMinecraftUuid, record);
    }

    public void record(
            UUID victimMinecraftUuid,
            UUID attackerMinecraftUuid,
            AttributionCause cause,
            String detail
    ) {
        record(victimMinecraftUuid, new AttackRecord(attackerMinecraftUuid, cause, clock.instant(), detail));
    }

    /**
     * Returns the last attacker if still within the attribution window.
     */
    public Optional<AttackRecord> lastAttacker(UUID victimMinecraftUuid) {
        return lastAttacker(victimMinecraftUuid, clock.instant());
    }

    public Optional<AttackRecord> lastAttacker(UUID victimMinecraftUuid, Instant now) {
        Objects.requireNonNull(victimMinecraftUuid, "victimMinecraftUuid");
        Objects.requireNonNull(now, "now");
        AttackRecord record = byVictim.get(victimMinecraftUuid);
        if (record == null) {
            return Optional.empty();
        }
        if (isExpired(record, now)) {
            byVictim.remove(victimMinecraftUuid, record);
            return Optional.empty();
        }
        return Optional.of(record);
    }

    public void clear(UUID victimMinecraftUuid) {
        if (victimMinecraftUuid != null) {
            byVictim.remove(victimMinecraftUuid);
        }
    }

    public void clearExpired() {
        Instant now = clock.instant();
        byVictim.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    /** Expired only after the window elapses; death at exactly window end still credits. */
    private boolean isExpired(AttackRecord record, Instant now) {
        return record.at().plus(window).isBefore(now);
    }
}
