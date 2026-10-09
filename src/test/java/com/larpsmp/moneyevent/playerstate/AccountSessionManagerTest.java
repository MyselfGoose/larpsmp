package com.larpsmp.moneyevent.playerstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountSessionManagerTest {

    private AccountSessionManager sessions;

    @BeforeEach
    void setUp() {
        sessions = new AccountSessionManager();
    }

    @Test
    void tryBeginRegistersPendingSession() {
        UUID accountId = UUID.randomUUID();
        UUID minecraftUuid = UUID.randomUUID();

        Optional<AccountSession> begun = sessions.tryBegin(accountId, minecraftUuid, "alice");
        assertTrue(begun.isPresent());
        assertEquals("alice", begun.get().username());
        assertFalse(begun.get().isActive());
        assertEquals(accountId, sessions.findByMinecraftUuid(minecraftUuid).orElseThrow().accountId());
        assertEquals(minecraftUuid, sessions.findByAccountId(accountId).orElseThrow().minecraftUuid());
    }

    @Test
    void concurrentLoginForSameAccountIsRejected() {
        UUID accountId = UUID.randomUUID();
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();

        assertTrue(sessions.tryBegin(accountId, firstUuid, "alice").isPresent());
        assertTrue(sessions.tryBegin(accountId, secondUuid, "alice").isEmpty());
        assertTrue(sessions.findByMinecraftUuid(firstUuid).isPresent());
        assertTrue(sessions.findByMinecraftUuid(secondUuid).isEmpty());
    }

    @Test
    void sequentialAccountsOnSameMinecraftUuidAfterEnd() {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        UUID minecraftUuid = UUID.randomUUID();

        assertTrue(sessions.tryBegin(accountA, minecraftUuid, "alice").isPresent());
        sessions.endByMinecraftUuid(minecraftUuid);

        Optional<AccountSession> second = sessions.tryBegin(accountB, minecraftUuid, "bob");
        assertTrue(second.isPresent());
        assertEquals(accountB, second.get().accountId());
        assertEquals("bob", second.get().username());
        assertTrue(sessions.findByAccountId(accountA).isEmpty());
    }

    @Test
    void cancelPendingDoesNotRemoveActiveSession() {
        UUID accountId = UUID.randomUUID();
        UUID minecraftUuid = UUID.randomUUID();
        AccountSession session = sessions.tryBegin(accountId, minecraftUuid, "alice").orElseThrow();
        session.markActive();

        sessions.cancelPending(minecraftUuid);
        assertTrue(sessions.findByMinecraftUuid(minecraftUuid).isPresent());
    }

    @Test
    void cancelPendingRemovesUnusedSession() {
        UUID accountId = UUID.randomUUID();
        UUID minecraftUuid = UUID.randomUUID();
        assertTrue(sessions.tryBegin(accountId, minecraftUuid, "alice").isPresent());

        sessions.cancelPending(minecraftUuid);
        assertTrue(sessions.findByMinecraftUuid(minecraftUuid).isEmpty());
        assertTrue(sessions.findByAccountId(accountId).isEmpty());
    }
}
