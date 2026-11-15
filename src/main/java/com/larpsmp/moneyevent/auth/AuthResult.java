package com.larpsmp.moneyevent.auth;

/**
 * Outcome of a configure-phase authentication attempt.
 */
public enum AuthResult {
    /**
     * Credentials accepted; allow the player into the world.
     */
    ALLOWED,
    /**
     * Player chose to leave (back to main menu). Disconnect already handled.
     */
    CANCELLED,
    /**
     * Timed out, plugin disabled, or connection closed without authenticating.
     */
    REJECTED
}
