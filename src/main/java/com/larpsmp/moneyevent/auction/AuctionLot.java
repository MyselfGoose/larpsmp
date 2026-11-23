package com.larpsmp.moneyevent.auction;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * One mystery-shulker lot within an auction session.
 */
public final class AuctionLot {

    private final UUID lotId;
    private final UUID sessionId;
    private final int lotNumber;
    private final String theme;
    private final long startingBid;
    private AuctionLotStatus status;
    private @Nullable Long highBid;
    private @Nullable UUID highBidderAccountId;
    private @Nullable UUID owedAccountId;
    private @Nullable Long owedAmount;
    private @Nullable UUID paymentTransactionId;
    private final LinkedHashSet<UUID> excludedPayerIds;
    private final Instant openedAt;
    private Instant updatedAt;
    private @Nullable Instant closedAt;
    private @Nullable Instant paidAt;
    private @Nullable Instant deliveredAt;

    public AuctionLot(
            UUID lotId,
            UUID sessionId,
            int lotNumber,
            String theme,
            long startingBid,
            AuctionLotStatus status,
            @Nullable Long highBid,
            @Nullable UUID highBidderAccountId,
            @Nullable UUID owedAccountId,
            @Nullable Long owedAmount,
            @Nullable UUID paymentTransactionId,
            String excludedPayerIds,
            Instant openedAt,
            Instant updatedAt,
            @Nullable Instant closedAt,
            @Nullable Instant paidAt,
            @Nullable Instant deliveredAt) {
        this.lotId = Objects.requireNonNull(lotId, "lotId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        if (lotNumber <= 0) {
            throw new IllegalArgumentException("lotNumber must be positive");
        }
        this.lotNumber = lotNumber;
        this.theme = Objects.requireNonNull(theme, "theme").trim();
        if (this.theme.isEmpty()) {
            throw new IllegalArgumentException("theme cannot be blank");
        }
        if (startingBid < 0) {
            throw new IllegalArgumentException("startingBid cannot be negative");
        }
        this.startingBid = startingBid;
        this.status = Objects.requireNonNull(status, "status");
        this.highBid = highBid;
        this.highBidderAccountId = highBidderAccountId;
        this.owedAccountId = owedAccountId;
        this.owedAmount = owedAmount;
        this.paymentTransactionId = paymentTransactionId;
        this.excludedPayerIds = parseExcluded(excludedPayerIds);
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.closedAt = closedAt;
        this.paidAt = paidAt;
        this.deliveredAt = deliveredAt;
    }

    public static AuctionLot open(
            UUID lotId,
            UUID sessionId,
            int lotNumber,
            String theme,
            long startingBid,
            Instant now) {
        return new AuctionLot(
                lotId,
                sessionId,
                lotNumber,
                theme,
                startingBid,
                AuctionLotStatus.OPEN,
                null,
                null,
                null,
                null,
                null,
                "",
                now,
                now,
                null,
                null,
                null);
    }

    public UUID lotId() {
        return lotId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public int lotNumber() {
        return lotNumber;
    }

    public String theme() {
        return theme;
    }

    public long startingBid() {
        return startingBid;
    }

    public AuctionLotStatus status() {
        return status;
    }

    public @Nullable Long highBid() {
        return highBid;
    }

    public @Nullable UUID highBidderAccountId() {
        return highBidderAccountId;
    }

    public @Nullable UUID owedAccountId() {
        return owedAccountId;
    }

    public @Nullable Long owedAmount() {
        return owedAmount;
    }

    public @Nullable UUID paymentTransactionId() {
        return paymentTransactionId;
    }

    public String excludedPayerIdsSerialized() {
        return excludedPayerIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    public Set<UUID> excludedPayerIds() {
        return Set.copyOf(excludedPayerIds);
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public @Nullable Instant closedAt() {
        return closedAt;
    }

    public @Nullable Instant paidAt() {
        return paidAt;
    }

    public @Nullable Instant deliveredAt() {
        return deliveredAt;
    }

    public long minimumNextBid() {
        if (highBid == null) {
            return startingBid;
        }
        return highBid + 1L;
    }

    public void applyBid(UUID accountId, long amount, Instant now) {
        this.highBid = amount;
        this.highBidderAccountId = Objects.requireNonNull(accountId, "accountId");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void beginCountdown(Instant now) {
        this.status = AuctionLotStatus.COUNTDOWN;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void closeAwaitingPayment(UUID owedAccountId, long owedAmount, Instant now) {
        this.status = AuctionLotStatus.AWAITING_PAYMENT;
        this.owedAccountId = Objects.requireNonNull(owedAccountId, "owedAccountId");
        this.owedAmount = owedAmount;
        this.closedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void markUnsold(Instant now) {
        this.status = AuctionLotStatus.UNSOLD;
        this.owedAccountId = null;
        this.owedAmount = null;
        this.closedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void offerTo(UUID accountId, long amount, Instant now) {
        if (owedAccountId != null) {
            excludedPayerIds.add(owedAccountId);
        }
        this.status = AuctionLotStatus.AWAITING_PAYMENT;
        this.owedAccountId = Objects.requireNonNull(accountId, "accountId");
        this.owedAmount = amount;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void markPaid(UUID paymentTransactionId, Instant now) {
        this.status = AuctionLotStatus.PAID;
        this.paymentTransactionId = Objects.requireNonNull(paymentTransactionId, "paymentTransactionId");
        this.paidAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void markDelivered(Instant now) {
        this.status = AuctionLotStatus.DELIVERED;
        this.deliveredAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    private static LinkedHashSet<UUID> parseExcluded(@Nullable String raw) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ids.add(UUID.fromString(trimmed));
        }
        return ids;
    }

}
