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
                    new AccountServerOperatorRepository(fixture.dataSource()),
                    fixture.accounts(),
                    sessions,
                    message -> {
                    });

            assertFalse(admins.isAdminMinecraftUuid(otherClientUuid));
            assertInstanceOf(AccountAdminService.GrantResult.Granted.class,
                    admins.grantPluginAdmin(player.username(), "console"));
            assertTrue(admins.isAdminAccount(accountId));
            assertTrue(admins.isAdminMinecraftUuid(otherClientUuid));
            assertFalse(admins.isServerOperatorAccount(accountId));

            // Same account, different client UUID still admin.
            sessions.endByMinecraftUuid(otherClientUuid);
            UUID thirdClient = UUID.randomUUID();
            sessions.tryBegin(accountId, thirdClient, player.username());
            assertTrue(admins.isAdminMinecraftUuid(thirdClient));

            assertInstanceOf(AccountAdminService.GrantResult.Revoked.class,
                    admins.revokePluginAdmin(player.username()));
            assertFalse(admins.isAdminMinecraftUuid(thirdClient));
            assertFalse(admins.isAdminAccount(accountId));
            assertTrue(admins.listAdmins().stream()
                    .noneMatch(row -> row.accountId().equals(accountId)));
        }
    }

    @Test
    void serverOperatorImpliesPluginAdminAndBlocksDeop() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("AccountAdminServiceServerOp")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TestDatabaseSupport.RegisteredPlayer player = fixture.register("ServerOpGoose");
            UUID accountId = fixture.accountId(player);
            UUID clientUuid = UUID.randomUUID();

            AccountSessionManager sessions = new AccountSessionManager();
            sessions.tryBegin(accountId, clientUuid, player.username());

            AccountAdminService admins = new AccountAdminService(
                    new AccountAdminRepository(fixture.dataSource()),
                    new AccountServerOperatorRepository(fixture.dataSource()),
                    fixture.accounts(),
                    sessions,
                    message -> {
                    });

            assertInstanceOf(AccountAdminService.GrantResult.Granted.class,
                    admins.grantServerOperator(player.username(), "console"));
            assertTrue(admins.isServerOperatorAccount(accountId));
            assertTrue(admins.isPluginAdminAccount(accountId));
            assertTrue(admins.isServerOperatorMinecraftUuid(clientUuid));

            assertInstanceOf(AccountAdminService.GrantResult.StillServerOperator.class,
                    admins.revokePluginAdmin(player.username()));
            assertTrue(admins.isServerOperatorAccount(accountId));

            assertInstanceOf(AccountAdminService.GrantResult.Revoked.class,
                    admins.revokeServerOperator(player.username()));
            assertFalse(admins.isServerOperatorAccount(accountId));
            // Plugin admin row remains after serverdeop.
            assertTrue(admins.isPluginAdminAccount(accountId));

            assertInstanceOf(AccountAdminService.GrantResult.Revoked.class,
                    admins.revokePluginAdmin(player.username()));
            assertFalse(admins.isPluginAdminAccount(accountId));
        }
    }

    @Test
    void authorizerAllowsPluginAdminButNotServerOpManagement() {
        AccountPermissionAuthorizer authorizer = new AccountPermissionAuthorizer();
        FakeSource pluginOnly = FakeSource.player(true, false);
        FakeSource serverOp = FakeSource.player(true, true);
        FakeSource none = FakeSource.player(false, false);
        FakeSource console = FakeSource.console();

        assertTrue(authorizer.hasPluginPrivilege(pluginOnly));
        assertTrue(authorizer.hasPluginPrivilege(serverOp));
        assertTrue(authorizer.hasPluginPrivilege(console));
        assertFalse(authorizer.hasPluginPrivilege(none));

        assertFalse(authorizer.canManageServerOperators(pluginOnly));
        assertTrue(authorizer.canManageServerOperators(serverOp));
        assertTrue(authorizer.canManageServerOperators(console));
        assertFalse(authorizer.canManageServerOperators(none));
    }

    private static final class FakeSource implements com.larpsmp.moneyevent.command.CommandSource {
        private final boolean console;
        private final boolean pluginAdmin;
        private final boolean serverOperator;

        private FakeSource(boolean console, boolean pluginAdmin, boolean serverOperator) {
            this.console = console;
            this.pluginAdmin = pluginAdmin;
            this.serverOperator = serverOperator;
        }

        static FakeSource console() {
            return new FakeSource(true, true, true);
        }

        static FakeSource player(boolean pluginAdmin, boolean serverOperator) {
            return new FakeSource(false, pluginAdmin, serverOperator);
        }

        @Override
        public java.util.Optional<UUID> playerId() {
            return console ? java.util.Optional.empty() : java.util.Optional.of(UUID.randomUUID());
        }

        @Override
        public String name() {
            return console ? "CONSOLE" : "player";
        }

        @Override
        public boolean isConsole() {
            return console;
        }

        @Override
        public boolean isOperator() {
            return pluginAdmin || serverOperator;
        }

        @Override
        public boolean isPluginAdmin() {
            return console || pluginAdmin || serverOperator;
        }

        @Override
        public boolean isServerOperator() {
            return console || serverOperator;
        }

        @Override
        public void send(String message, com.larpsmp.moneyevent.command.MessageKind kind) {
        }
    }
}
