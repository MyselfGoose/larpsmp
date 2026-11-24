package com.larpsmp.moneyevent.auction;

import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Auction host payment recipient configuration.
 */
public final class AuctionConfig {

    public static final String DEFAULT_PAYMENT_USERNAME = "akiracreates";

    private final String paymentUsername;

    public AuctionConfig(String paymentUsername) {
        String trimmed = paymentUsername == null ? "" : paymentUsername.trim();
        this.paymentUsername = trimmed.isEmpty() ? DEFAULT_PAYMENT_USERNAME : trimmed;
    }

    public static AuctionConfig defaults() {
        return new AuctionConfig(DEFAULT_PAYMENT_USERNAME);
    }

    public static AuctionConfig from(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        return new AuctionConfig(config.getString("auction.payment-username", DEFAULT_PAYMENT_USERNAME));
    }

    public String paymentUsername() {
        return paymentUsername;
    }

    public AuctionConfig withPaymentUsername(String username) {
        return new AuctionConfig(username);
    }
}
