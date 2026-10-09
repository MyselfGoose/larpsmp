package com.larpsmp.moneyevent.wallet;

import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.auth.AuthConfig;
import com.larpsmp.moneyevent.config.EnvSettings;
import com.larpsmp.moneyevent.db.DataSourceFactory;
import com.larpsmp.moneyevent.db.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class TestDatabaseSupport {

    private TestDatabaseSupport() {
    }

    /** @return null when Postgres is unreachable */
    public static AuthConfig.DatabaseConfig resolveDatabaseConfig() {
        EnvSettings env = EnvSettings.load();
        String jdbcUrl = env.get("LARPSMP_JDBC_URL", "jdbc:postgresql://127.0.0.1:5433/larpsmp");
        String[][] candidates = {
                {env.get("LARPSMP_DB_USER", "larpsmp"), env.get("LARPSMP_DB_PASSWORD", "larpsmp")},
                {"goose", "larpsmp"}
        };
        for (String[] candidate : candidates) {
            AuthConfig.DatabaseConfig config = new AuthConfig.DatabaseConfig(
                    jdbcUrl, candidate[0], candidate[1], 2);
            try (HikariDataSource probe = DataSourceFactory.create(config);
                 Connection connection = probe.getConnection()) {
                if (connection.isValid(3)) {
                    return config;
                }
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return null;
    }

    public static Fixture open(String testName) throws Exception {
        AuthConfig.DatabaseConfig config = resolveDatabaseConfig();
        if (config == null) {
            return null;
        }
        HikariDataSource dataSource = DataSourceFactory.create(config);
        new MigrationRunner(dataSource, Logger.getLogger(testName)).migrate();
        AccountRepository accounts = new AccountRepository(dataSource);
        JdbcMoneyRepository moneyRepository = new JdbcMoneyRepository(dataSource);
        MoneyService money = new MoneyService(accounts, moneyRepository, message -> { });
        return new Fixture(dataSource, accounts, moneyRepository, money, new ArrayList<>());
    }

    public static final class Fixture implements AutoCloseable {
        private final HikariDataSource dataSource;
        private final AccountRepository accounts;
        private final JdbcMoneyRepository moneyRepository;
        private final MoneyService money;
        private final List<UUID> minecraftUuids;
        private final List<String> usernames = new ArrayList<>();

        Fixture(
                HikariDataSource dataSource,
                AccountRepository accounts,
                JdbcMoneyRepository moneyRepository,
                MoneyService money,
                List<UUID> minecraftUuids) {
            this.dataSource = dataSource;
            this.accounts = accounts;
            this.moneyRepository = moneyRepository;
            this.money = money;
            this.minecraftUuids = minecraftUuids;
        }

        public AccountRepository accounts() {
            return accounts;
        }

        public JdbcMoneyRepository moneyRepository() {
            return moneyRepository;
        }

        public MoneyService money() {
            return money;
        }

        public RegisteredPlayer register(String username) throws Exception {
            UUID minecraftUuid = UUID.randomUUID();
            String unique = username.toLowerCase(java.util.Locale.ROOT)
                    + "_"
                    + Integer.toHexString(minecraftUuid.hashCode());
            accounts.createAccountWithIdentity(
                    unique,
                    unique + "@example.com",
                    "test-hash",
                    minecraftUuid,
                    username);
            minecraftUuids.add(minecraftUuid);
            usernames.add(unique);
            return new RegisteredPlayer(minecraftUuid, username);
        }

        @Override
        public void close() throws Exception {
            money.close();
            try (Connection connection = dataSource.getConnection()) {
                for (UUID minecraftUuid : minecraftUuids) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM account_minecraft_identities WHERE minecraft_uuid = ?")) {
                        statement.setObject(1, minecraftUuid);
                        statement.executeUpdate();
                    }
                }
                for (String username : usernames) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM accounts WHERE username = ?")) {
                        statement.setString(1, username);
                        statement.executeUpdate();
                    }
                }
            } finally {
                dataSource.close();
            }
        }
    }

    public record RegisteredPlayer(UUID minecraftUuid, String username) {
    }
}
