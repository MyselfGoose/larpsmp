package com.larpsmp.moneyevent.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class CaptureCreditMessagesTest {

    @Test
    void creditCardPlainTextShowsPointTeamShareAndWalletDelta() {
        Component[] lines = CaptureCreditMessages.creditCard(
                "Quarry",
                "RedTest",
                50,
                13,
                435,
                448
        );
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        assertEquals("— Capture Reward —", plain.serialize(lines[0]));
        assertEquals("Point: Quarry", plain.serialize(lines[1]));
        assertEquals("Team: RedTest", plain.serialize(lines[2]));
        assertEquals("Pool: $50 split among roster", plain.serialize(lines[3]));
        assertEquals("Your share: +$13", plain.serialize(lines[4]));
        assertEquals("Wallet: $435 → $448", plain.serialize(lines[5]));
    }
}
