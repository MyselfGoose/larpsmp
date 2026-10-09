package com.larpsmp.moneyevent.playerstate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountPlayerStateRepositoryTest {

    @Test
    void upsertAndReloadRoundTrip() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open(getClass().getSimpleName())) {
            assumeTrue(fixture != null, "PostgreSQL is not available");

            TestDatabaseSupport.RegisteredPlayer player = fixture.register("bodyplayer");
            UUID accountId = fixture.accounts()
                    .findIdentityByMinecraftUuid(player.minecraftUuid())
                    .orElseThrow()
                    .accountId();

            AccountPlayerStateRepository repository = new AccountPlayerStateRepository(fixture.dataSource());

            byte[] inventory = PlayerStateCodec.encodeOpaqueSlots(new byte[] {10, 20}, new byte[] {30});
            byte[] armor = PlayerStateCodec.encodeOpaqueSlots(new byte[] {1}, new byte[] {}, new byte[] {}, new byte[] {});
            byte[] offhand = PlayerStateCodec.encodeOpaqueSlots(new byte[] {7});
            byte[] ender = PlayerStateCodec.encodeOpaqueSlots();
            byte[] effects = PlayerStateCodec.encodeOpaqueSlots(new byte[] {1, 1, 1});

            AccountPlayerState original = new AccountPlayerState(
                    accountId,
                    PlayerStateCodec.CURRENT_SCHEMA_VERSION,
                    "world",
                    12.5,
                    70.0,
                    -8.25,
                    90.0f,
                    15.0f,
                    inventory,
                    armor,
                    offhand,
                    ender,
                    18.5,
                    17,
                    4.5f,
                    1.25f,
                    12,
                    150,
                    0.4f,
                    "SURVIVAL",
                    effects,
                    Instant.parse("2026-01-02T03:04:05Z")
            );

            repository.upsert(original);

            Optional<AccountPlayerState> loaded = repository.findByAccountId(accountId);
            assertTrue(loaded.isPresent());
            AccountPlayerState state = loaded.get();
            assertEquals(accountId, state.accountId());
            assertEquals("world", state.worldName());
            assertEquals(12.5, state.x());
            assertEquals(70.0, state.y());
            assertEquals(-8.25, state.z());
            assertEquals(90.0f, state.yaw());
            assertEquals(15.0f, state.pitch());
            assertEquals(18.5, state.health());
            assertEquals(17, state.foodLevel());
            assertEquals(4.5f, state.saturation());
            assertEquals(1.25f, state.exhaustion());
            assertEquals(12, state.level());
            assertEquals(150, state.totalExperience());
            assertEquals(0.4f, state.exp());
            assertEquals("SURVIVAL", state.gameMode());
            assertArrayEquals(inventory, state.inventoryData());
            assertArrayEquals(armor, state.armorData());
            assertArrayEquals(offhand, state.offhandData());
            assertArrayEquals(ender, state.enderChestData());
            assertArrayEquals(effects, state.potionEffectsData());

            AccountPlayerState updated = new AccountPlayerState(
                    accountId,
                    PlayerStateCodec.CURRENT_SCHEMA_VERSION,
                    "world_nether",
                    1.0,
                    64.0,
                    2.0,
                    0.0f,
                    0.0f,
                    inventory,
                    armor,
                    offhand,
                    ender,
                    10.0,
                    10,
                    2.0f,
                    0.0f,
                    3,
                    30,
                    0.1f,
                    "ADVENTURE",
                    effects,
                    Instant.now()
            );
            repository.upsert(updated);

            AccountPlayerState reloaded = repository.findByAccountId(accountId).orElseThrow();
            assertEquals("world_nether", reloaded.worldName());
            assertEquals("ADVENTURE", reloaded.gameMode());
            assertEquals(10.0, reloaded.health());
            assertEquals(3, reloaded.level());
        }
    }

    @Test
    void sequentialAccountStatesAreIndependent() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open(getClass().getSimpleName())) {
            assumeTrue(fixture != null, "PostgreSQL is not available");

            TestDatabaseSupport.RegisteredPlayer a = fixture.register("state_a");
            TestDatabaseSupport.RegisteredPlayer b = fixture.register("state_b");
            UUID accountA = fixture.accounts().findIdentityByMinecraftUuid(a.minecraftUuid()).orElseThrow().accountId();
            UUID accountB = fixture.accounts().findIdentityByMinecraftUuid(b.minecraftUuid()).orElseThrow().accountId();

            AccountPlayerStateRepository repository = new AccountPlayerStateRepository(fixture.dataSource());
            byte[] emptyInv = PlayerStateCodec.encodeOpaqueSlots();
            byte[] emptyArmor = PlayerStateCodec.encodeOpaqueSlots();
            byte[] emptyOff = PlayerStateCodec.encodeOpaqueSlots();
            byte[] emptyEnder = PlayerStateCodec.encodeOpaqueSlots();
            byte[] emptyFx = PlayerStateCodec.encodeOpaqueSlots();

            repository.upsert(new AccountPlayerState(
                    accountA, 1, "world", 100, 64, 100, 0, 0,
                    emptyInv, emptyArmor, emptyOff, emptyEnder,
                    20, 20, 5, 0, 0, 0, 0, "SURVIVAL", emptyFx, Instant.now()));
            repository.upsert(new AccountPlayerState(
                    accountB, 1, "world", 200, 70, 200, 45, 10,
                    emptyInv, emptyArmor, emptyOff, emptyEnder,
                    15, 10, 1, 0, 5, 50, 0.5f, "CREATIVE", emptyFx, Instant.now()));

            AccountPlayerState loadedA = repository.findByAccountId(accountA).orElseThrow();
            AccountPlayerState loadedB = repository.findByAccountId(accountB).orElseThrow();
            assertEquals(100, loadedA.x());
            assertEquals(200, loadedB.x());
            assertEquals("SURVIVAL", loadedA.gameMode());
            assertEquals("CREATIVE", loadedB.gameMode());
            assertEquals(0, loadedA.level());
            assertEquals(5, loadedB.level());
        }
    }
}
