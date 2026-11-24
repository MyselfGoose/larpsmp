package com.larpsmp.moneyevent.auction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fixed team representative for one auction session.
 */
public record AuctionRepresentative(
        UUID sessionId,
        UUID teamId,
        UUID accountId,
        Instant registeredAt) {

    public AuctionRepresentative {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }
}
