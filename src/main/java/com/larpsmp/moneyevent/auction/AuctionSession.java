package com.larpsmp.moneyevent.auction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One auction event run with fixed representatives across lots.
 */
public final class AuctionSession {

    private final UUID sessionId;
    private AuctionSessionStatus status;
    private final Instant startedAt;
    private Instant updatedAt;
    private @Nullable Instant closedAt;

    public AuctionSession(
            UUID sessionId,
            AuctionSessionStatus status,
            Instant startedAt,
            Instant updatedAt,
            @Nullable Instant closedAt) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.status = Objects.requireNonNull(status, "status");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.closedAt = closedAt;
    }

    public static AuctionSession start(UUID sessionId, Instant now) {
        return new AuctionSession(sessionId, AuctionSessionStatus.SETUP, now, now, null);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public AuctionSessionStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Instant closedAt() {
        return closedAt;
    }

    public boolean isOpen() {
        return status == AuctionSessionStatus.SETUP || status == AuctionSessionStatus.ACTIVE;
    }

    public void markActive(Instant now) {
        this.status = AuctionSessionStatus.ACTIVE;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void close(Instant now) {
        this.status = AuctionSessionStatus.CLOSED;
        this.updatedAt = Objects.requireNonNull(now, "now");
        this.closedAt = now;
    }
}
