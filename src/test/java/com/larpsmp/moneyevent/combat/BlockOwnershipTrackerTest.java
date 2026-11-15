package com.larpsmp.moneyevent.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockOwnershipTrackerTest {

    @Test
    void setReplaceAndClear() {
        BlockOwnershipTracker tracker = new BlockOwnershipTracker();
        BlockKey key = BlockKey.of("world", 1, 64, -3);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(tracker.ownerOf(key).isEmpty());

        tracker.setOwner(key, first);
        assertEquals(first, tracker.ownerOf(key).orElseThrow());

        tracker.setOwner(key, second);
        assertEquals(second, tracker.ownerOf(key).orElseThrow());

        tracker.clear(key);
        assertTrue(tracker.ownerOf(key).isEmpty());
        assertEquals(0, tracker.size());
    }

    @Test
    void unknownKeyIsEnvironmental() {
        BlockOwnershipTracker tracker = new BlockOwnershipTracker();
        assertTrue(tracker.ownerOf(BlockKey.of("world", 0, 0, 0)).isEmpty());
    }
}
