package com.larpsmp.moneyevent.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class StandingsMessagesTest {

    @Test
    void titleAndEntryKeepReadablePlainText() {
        assertEquals("— Team wealth —",
                PlainTextComponentSerializer.plainText().serialize(StandingsMessages.title("Team wealth")));
        assertEquals("1. Red — $850",
                PlainTextComponentSerializer.plainText().serialize(StandingsMessages.entry(1, "Red", 850)));
    }

    @Test
    void killEntryUsesKillCountsNotCurrency() {
        assertEquals("1. Alice — 3 kills",
                PlainTextComponentSerializer.plainText().serialize(StandingsMessages.killEntry(1, "Alice", 3)));
        assertEquals("2. Bob — 1 kill",
                PlainTextComponentSerializer.plainText().serialize(StandingsMessages.killEntry(2, "Bob", 1)));
    }
}
