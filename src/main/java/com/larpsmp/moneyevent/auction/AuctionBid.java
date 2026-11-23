package com.larpsmp.moneyevent.auction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only bid row for previous-bidder fallback.
 */
public record AuctionBid(
        UUID bidId,
        UUID lotId,
        UUID accountId,
        UUID teamId,
        long amount,
        Instant createdAt) {

    public AuctionBid {
        Objects.requireNonNull(bidId, "bidId");
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
