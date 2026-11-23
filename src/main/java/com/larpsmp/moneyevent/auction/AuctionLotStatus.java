package com.larpsmp.moneyevent.auction;

/**
 * Lifecycle status for a single auction lot.
 */
public enum AuctionLotStatus {
    OPEN,
    COUNTDOWN,
    AWAITING_PAYMENT,
    PAID,
    DELIVERED,
    UNSOLD;

    public boolean acceptsBids() {
        return this == OPEN;
    }

    public boolean isPaymentPending() {
        return this == AWAITING_PAYMENT;
    }

    /**
     * Lot is fully finished and should not remain the runtime active pointer.
     * {@link #PAID} stays active so delivery can still be marked after restart.
     */
    public boolean clearsRuntimePointer() {
        return this == DELIVERED || this == UNSOLD;
    }

    public boolean blocksNewLot() {
        return this == OPEN || this == COUNTDOWN || this == AWAITING_PAYMENT;
    }
}
