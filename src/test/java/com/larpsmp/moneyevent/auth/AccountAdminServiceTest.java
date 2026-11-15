package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountAdminServiceTest {

    @Test
    void grantRevokeIsAccountScopedNotClientUuid() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("AccountAdminServiceTest")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TestDatabaseSupport.RegisteredPlayer player = fixture.register("AdminGoose");
            UUID accountId = fixture.accountId(player);
            UUID otherClientUuid = UUID.randomUUID();

            AccountSessionManager sessions = new AccountSessionManager();
            sessions.tryBegin(accountId, otherClientUuid, player.username());

            AccountAdminService admins = new AccountAdminService(
                    new AccountAdminRepository(fixture.dataSource()),
                    fixture.accounts(),
                    sessions,
                    message -> {
                    });

            assertFalse(admins.isAdminMinecraftUuid(otherClientUuid));
            assertInstanceOf(AccountAdminService.GrantResult.Granted.class,
                    admins.grant(player.username(), "console"));
            assertTrue(admins.isAdminAccount(accountId));
            assertTrue(admins.isAdminMinecraftUuid(otherClientUuid));

            // Same account, different client UUID still admin.
            sessions.endByMinecraftUuid(otherClientUuid);
            UUID thirdClient = UUID.randomUUID();
            sessions.tryBegin(accountId, thirdClient, player.username());
            assertTrue(admins.isAdminMinecraftUuid(thirdClient));

            assertInstanceOf(AccountAdminService.GrantResult.Revoked.class,
                    admins.revoke(player.username()));
            assertFalse(admins.isAdminMinecraftUuid(thirdClient));
            assertEquals(0, admins.listAdmins().size());
        }
    }
}
