package com.larpsmp.moneyevent.playerstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class AccountIdentityBinderTest {

    @Test
    void joinAndQuitMessagesUseAccountUsername() {
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        assertEquals(
                "secondgoose joined the game",
                plain.serialize(AccountIdentityBinder.joinMessage("secondgoose")));
        assertEquals(
                "secondgoose left the game",
                plain.serialize(AccountIdentityBinder.quitMessage("secondgoose")));
    }

    @Test
    void bindResultCollisionIsNotSuccessful() {
        AccountIdentityBinder.BindResult result = AccountIdentityBinder.BindResult.collision("taken");
        assertEquals(AccountIdentityBinder.BindResult.Status.COLLISION, result.status());
        assertFalse(result.successful());
        assertFalse(result.commandIndexBound());
    }

    @Test
    void bindResultOkReportsIndexed() {
        AccountIdentityBinder.BindResult result =
                AccountIdentityBinder.BindResult.ok("secondgoose", "Yamite", true);
        assertEquals(AccountIdentityBinder.BindResult.Status.OK, result.status());
        assertTrue(result.successful());
        assertTrue(result.commandIndexBound());
        assertEquals("Yamite", result.launcherName());
        assertEquals("secondgoose", result.accountUsername());
    }

    @Test
    void bindResultPartialMeansDisplayOnly() {
        AccountIdentityBinder.BindResult result =
                AccountIdentityBinder.BindResult.partial("secondgoose", "Yamite");
        assertEquals(AccountIdentityBinder.BindResult.Status.PARTIAL, result.status());
        assertFalse(result.successful());
        assertFalse(result.commandIndexBound());
    }
}
