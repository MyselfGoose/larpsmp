package com.larpsmp.moneyevent.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoneyFormatsTest {

    @Test
    void formatsGroupedCurrency() {
        assertEquals("0", MoneyFormats.amount(0));
        assertEquals("250", MoneyFormats.amount(250));
        assertEquals("2,601", MoneyFormats.amount(2601));
        assertEquals("$2,601", MoneyFormats.currency(2601));
        assertEquals("—", MoneyFormats.currencyOrDash(null));
        assertEquals("$1,000,000", MoneyFormats.currencyOrDash(1_000_000L));
    }
}
