package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.db.DataSourceFactory;
import com.larpsmp.moneyevent.db.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Optional integration tests against a live Postgres instance.
 *
 * <p>Uses env vars when set, otherwise tries local Docker defaults
 * ({@code larpsmp/larpsmp}), then a local fallback ({@code goose/larpsmp}).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class AuthServiceIntegrationTest {

    private static HikariDataSource dataSource;
    private static AuthService authService;
    private static final UUID PROFILE_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROFILE_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String USERNAME = "integ_user_" + System.currentTimeMillis() % 100_000;
    private static final String EMAIL = USERNAME + "@example.com";
    private static final String PASSWORD = "password123";

    @BeforeAll
    static void setUp() throws Exception {
        AuthConfig.DatabaseConfig database = resolveDatabaseConfig();
        assumeTrue(database != null, "No reachable Postgres for integration tests");

        dataSource = DataSourceFactory.create(database);
        new MigrationRunner(dataSource, Logger.getLogger("AuthServiceIntegrationTest")).migrate();

        AuthConfig.SignupConfig signupConfig = new AuthConfig.SignupConfig(true, 8, 64, 3, 16);
        AuthConfig.Messages messages = sampleMessages();
        AuthRateLimiter rateLimiter = new AuthRateLimiter(new AuthConfig.RateLimitConfig(20, 300));
        authService = new AuthService(
                new AccountRepository(dataSource),
                new PasswordHasher(),
                rateLimiter,
                signupConfig,
                messages,
                Logger.getLogger("AuthServiceIntegrationTest")
        );
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement deleteIdentity = connection.prepareStatement(
                        "DELETE FROM account_minecraft_identities WHERE minecraft_uuid = ? OR minecraft_uuid = ?")) {
                    deleteIdentity.setObject(1, PROFILE_A);
                    deleteIdentity.setObject(2, PROFILE_B);
                    deleteIdentity.executeUpdate();
                }
                try (PreparedStatement deleteAccount = connection.prepareStatement(
                        "DELETE FROM accounts WHERE username = ?")) {
                    deleteAccount.setString(1, USERNAME);
                    deleteAccount.executeUpdate();
                }
            } finally {
                dataSource.close();
            }
        }
    }

    @Test
    @Order(1)
    void signupCreatesAccountAndHash() throws Exception {
        SignupResult result = authService.signup(USERNAME, EMAIL, PASSWORD, PROFILE_A, "PlayerA");
        assertInstanceOf(SignupResult.Success.class, result);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password_hash FROM accounts WHERE username = ?")) {
            statement.setString(1, USERNAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                String hash = resultSet.getString("password_hash");
                assertTrue(hash.startsWith("$argon2id$"));
                assertFalse(hash.contains(PASSWORD));
            }
        }
    }

    @Test
    @Order(2)
    void loginWithUsernameAndEmail() {
        LoginResult byUsername = authService.login(USERNAME, PASSWORD, PROFILE_A, "PlayerA");
        assertInstanceOf(LoginResult.Success.class, byUsername);

        LoginResult byEmail = authService.login(EMAIL.toUpperCase(), PASSWORD, PROFILE_A, "PlayerA");
        assertInstanceOf(LoginResult.Success.class, byEmail);
    }

    @Test
    @Order(3)
    void rejectsDuplicateSignupAndWrongPassword() {
        SignupResult duplicate = authService.signup(USERNAME, "other_" + EMAIL, PASSWORD, PROFILE_B, "PlayerB");
        assertInstanceOf(SignupResult.UsernameTaken.class, duplicate);

        LoginResult wrong = authService.login(USERNAME, "wrong-password", PROFILE_A, "PlayerA");
        assertInstanceOf(LoginResult.InvalidCredentials.class, wrong);
    }

    @Test
    @Order(4)
    void rejectsUuidMismatch() {
        LoginResult mismatch = authService.login(USERNAME, PASSWORD, PROFILE_B, "PlayerB");
        assertInstanceOf(LoginResult.AccountBoundToOtherUuid.class, mismatch);
    }

    private static AuthConfig.DatabaseConfig resolveDatabaseConfig() {
        String jdbcUrl = envOr("LARPSMP_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/larpsmp");
        String[][] candidates = {
                {envOr("LARPSMP_DB_USER", "larpsmp"), envOr("LARPSMP_DB_PASSWORD", "larpsmp")},
                {"goose", "larpsmp"}
        };
        for (String[] candidate : candidates) {
            AuthConfig.DatabaseConfig config = new AuthConfig.DatabaseConfig(
                    jdbcUrl,
                    candidate[0],
                    candidate[1],
                    2
            );
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

    private static String envOr(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static AuthConfig.Messages sampleMessages() {
        return new AuthConfig.Messages(
                "Login", "body", "id", "pw", "Log in", "Sign up",
                "invalid", "empty login", "uuid other", "account other", "rate login", "internal login",
                "Sign up", "signup body", "user", "email", "pw", "Create", "Back",
                "empty signup", "bad user", "bad email", "bad password",
                "user taken", "email taken", "disabled", "rate signup", "internal signup",
                "uuid bound", "Back to menu", "cancelled", "timeout", "denied", "missing", "db down"
        );
    }
}
