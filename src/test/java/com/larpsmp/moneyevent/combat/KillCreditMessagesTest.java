package com.larpsmp.moneyevent.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.larpsmp.moneyevent.team.PlayerTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class KillCreditMessagesTest {

    @Test
    void creditCardPlainTextShowsVictimTierWorthAndWalletDelta() {
        Component[] lines = KillCreditMessages.creditCard(
                "secondgoose",
                PlayerTier.TIER_1,
                40,
                40,
                200,
                240
        );
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        assertEquals("— Kill Credit —", plain.serialize(lines[0]));
        assertEquals("Killed: secondgoose", plain.serialize(lines[1]));
        assertEquals("Tier: Tier 1 · worth $40", plain.serialize(lines[2]));
        assertEquals("Earned: +$40", plain.serialize(lines[3]));
        assertEquals("Wallet: $200 → $240", plain.serialize(lines[4]));
    }
}
