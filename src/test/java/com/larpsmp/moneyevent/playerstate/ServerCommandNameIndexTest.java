package com.larpsmp.moneyevent.playerstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServerCommandNameIndexTest {

    @Test
    void normalizeLowercasesWithRootLocale() {
        assertEquals("secondgoose", ServerCommandNameIndex.normalize("SecondGoose"));
        assertEquals("yamite", ServerCommandNameIndex.normalize("Yamite"));
    }

    @Test
    void rekeyMovesHandleFromLauncherKeyToAccountKey() {
        Object handle = new Object();
        Map<String, Object> byName = new HashMap<>();
        byName.put("yamite", handle);

        ServerCommandNameIndex.rekey(byName, handle, "yamite", "secondgoose");

        assertFalse(byName.containsKey("yamite"));
        assertSame(handle, byName.get("secondgoose"));
        assertEquals(1, byName.size());
    }

    @Test
    void rekeyClearsStaleKeysEvenWhenPreviousMissing() {
        Object handle = new Object();
        Map<String, Object> byName = new HashMap<>();
        byName.put("oldnick", handle);
        byName.put("other", new Object());

        ServerCommandNameIndex.rekey(byName, handle, "missing", "secondgoose");

        assertFalse(byName.containsKey("oldnick"));
        assertSame(handle, byName.get("secondgoose"));
        assertTrue(byName.containsKey("other"));
        assertEquals(2, byName.size());
    }

    @Test
    void rekeyDoesNotRemoveForeignHandleOnPreviousKey() {
        Object handle = new Object();
        Object other = new Object();
        Map<String, Object> byName = new HashMap<>();
        byName.put("yamite", other);

        ServerCommandNameIndex.rekey(byName, handle, "yamite", "secondgoose");

        assertSame(other, byName.get("yamite"));
        assertSame(handle, byName.get("secondgoose"));
    }

    @Test
    void rekeyIsIdempotentWhenAlreadyBound() {
        Object handle = new Object();
        Map<String, Object> byName = new HashMap<>();
        byName.put("secondgoose", handle);

        ServerCommandNameIndex.rekey(byName, handle, "secondgoose", "secondgoose");

        assertSame(handle, byName.get("secondgoose"));
        assertEquals(1, byName.size());
    }
}
