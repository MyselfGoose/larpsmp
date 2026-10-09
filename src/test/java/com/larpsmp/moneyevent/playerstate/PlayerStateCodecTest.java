package com.larpsmp.moneyevent.playerstate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class PlayerStateCodecTest {

    @Test
    void opaqueSlotsRoundTrip() throws Exception {
        byte[] a = new byte[] {1, 2, 3};
        byte[] b = new byte[] {};
        byte[] c = new byte[] {9};

        byte[] encoded = PlayerStateCodec.encodeOpaqueSlots(a, b, c);
        byte[][] decoded = PlayerStateCodec.decodeOpaqueSlots(encoded);

        assertEquals(3, decoded.length);
        assertArrayEquals(a, decoded[0]);
        assertArrayEquals(b, decoded[1]);
        assertArrayEquals(c, decoded[2]);
    }

    @Test
    void emptyOpaqueArrayRoundTrip() throws Exception {
        byte[] encoded = PlayerStateCodec.encodeOpaqueSlots();
        byte[][] decoded = PlayerStateCodec.decodeOpaqueSlots(encoded);
        assertEquals(0, decoded.length);
    }

    @Test
    void rejectsCorruptOpaquePayload() {
        assertThrows(IOException.class, () -> PlayerStateCodec.decodeOpaqueSlots(new byte[] {1, 2, 3}));
    }
}
