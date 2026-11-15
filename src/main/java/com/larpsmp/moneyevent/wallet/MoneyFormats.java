package com.larpsmp.moneyevent.wallet;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Player-facing money formatting. Whole-dollar balances with US-style grouping separators.
 */
public final class MoneyFormats {

    private MoneyFormats() {
    }

    /** Digits only, e.g. {@code 2,601}. */
    public static String amount(long dollars) {
        return String.format(Locale.US, "%,d", dollars);
    }

    /** Currency with dollar sign, e.g. {@code $2,601}. */
    public static String currency(long dollars) {
        return "$" + amount(dollars);
    }

    /** Currency, or an em dash when the player has no team total. */
    public static String currencyOrDash(@Nullable Long dollars) {
        return dollars == null ? "—" : currency(dollars);
    }
}
