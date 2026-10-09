package com.larpsmp.moneyevent.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.db.DataSourceFactory;
import com.larpsmp.moneyevent.db.MigrationRunner;
import com.larpsmp.moneyevent.email.CapturingEmailSender;
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
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class AuthServiceIntegrationTest {

    private static HikariDataSource dataSource;
    private static AuthService authService;
    private static CapturingEmailSender emailSender;
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

        emailSender = new CapturingEmailSender();
        AccountRepository accountRepository = new AccountRepository(dataSource);
        EmailChallengeRepository challengeRepository = new EmailChallengeRepository(dataSource);
        EmailChallengeService emailChallengeService = new EmailChallengeService(
                challengeRepository,
                accountRepository,
                emailSender,
                new VerificationCodeHasher("integration-pepper", 6),
                new AuthConfig.EmailConfig(6, 600, 0, 5),
                true,
                Logger.getLogger("AuthServiceIntegrationTest")
        );

        AuthConfig.SignupConfig signupConfig = new AuthConfig.SignupConfig(true, 8, 64, 3, 16);
        AuthRateLimiter rateLimiter = new AuthRateLimiter(new AuthConfig.RateLimitConfig(20, 300));
        authService = new AuthService(
                accountRepository,
                new PasswordHasher(),
                rateLimiter,
                emailChallengeService,
                signupConfig,
                TestAuthFixtures.sampleMessages(),
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
    void signupCreatesUnverifiedAccountWalletAndSendsCode() throws Exception {
        SignupResult result = authService.signup(USERNAME, EMAIL, PASSWORD, PROFILE_A, "PlayerA");
        assertInstanceOf(SignupResult.PendingVerification.class, result);
        assertTrue(emailSender.lastCode().isPresent());

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password_hash, email_verified, last_login_at FROM accounts WHERE username = ?")) {
            statement.setString(1, USERNAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                String hash = resultSet.getString("password_hash");
                assertTrue(hash.startsWith("$argon2id$"));
                assertFalse(hash.contains(PASSWORD));
                assertFalse(resultSet.getBoolean("email_verified"));
                assertTrue(resultSet.getTimestamp("last_login_at") == null);
            }
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT w.balance, t.type, t.status
                     FROM accounts a
                     INNER JOIN wallets w ON w.account_id = a.id
                     INNER JOIN wallet_transactions t ON t.source_account_id = a.id
                     WHERE a.username = ?
                       AND t.type = 'STARTING_BALANCE'
                       AND t.status = 'SUCCESS'
                     """)) {
            statement.setString(1, USERNAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(200, resultSet.getLong("balance"));
            }
        }
    }

    @Test
    @Order(2)
    void loginBlockedUntilEmailVerified() {
        LoginResult before = authService.login(USERNAME, PASSWORD, PROFILE_A, "PlayerA");
        assertInstanceOf(LoginResult.EmailNotVerified.class, before);

        String code = emailSender.lastCode().orElseThrow();
        Account account = ((LoginResult.EmailNotVerified) before).account();
        VerifyEmailResult verified = authService.verifySignupEmail(account.id(), code, PROFILE_A);
        assertInstanceOf(VerifyEmailResult.Success.class, verified);

        LoginResult after = authService.login(USERNAME, PASSWORD, PROFILE_A, "PlayerA");
        assertInstanceOf(LoginResult.Success.class, after);

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
    void loginFromDifferentMinecraftProfileRebindsIdentity() throws Exception {
        LoginResult migrated = authService.login(USERNAME, PASSWORD, PROFILE_B, "PlayerB");
        assertInstanceOf(LoginResult.Success.class, migrated);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT minecraft_uuid, minecraft_name
                     FROM account_minecraft_identities
                     WHERE account_id = (SELECT id FROM accounts WHERE username = ?)
                     """)) {
            statement.setString(1, USERNAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(PROFILE_B, resultSet.getObject("minecraft_uuid", UUID.class));
                assertEquals("PlayerB", resultSet.getString("minecraft_name"));
            }
        }

        // Original profile is free; account follows the latest successful login.
        LoginResult back = authService.login(USERNAME, PASSWORD, PROFILE_A, "PlayerA");
        assertInstanceOf(LoginResult.Success.class, back);
    }

    @Test
    @Order(5)
    void passwordResetAndUsernameRecovery() {
        emailSender.clear();
        ForgotPasswordResult resetStart = authService.beginRecovery(
                EMAIL,
                EmailChallengePurpose.PASSWORD_RESET,
                PROFILE_A
        );
        assertInstanceOf(ForgotPasswordResult.Accepted.class, resetStart);
        ForgotPasswordResult.Accepted accepted = (ForgotPasswordResult.Accepted) resetStart;
        assertTrue(accepted.accountFound());
        String resetCode = emailSender.lastCode().orElseThrow();

        RecoverVerifyResult resetVerify = authService.verifyRecoveryCode(
                accepted.accountIdOrNull(),
                EmailChallengePurpose.PASSWORD_RESET,
                resetCode,
                PROFILE_A
        );
        assertInstanceOf(RecoverVerifyResult.PasswordResetAuthorized.class, resetVerify);

        ResetPasswordResult reset = authService.resetPassword(
                accepted.accountIdOrNull(),
                "newpassword99",
                "newpassword99"
        );
        assertInstanceOf(ResetPasswordResult.Success.class, reset);

        LoginResult oldPassword = authService.login(USERNAME, PASSWORD, PROFILE_A, "PlayerA");
        assertInstanceOf(LoginResult.InvalidCredentials.class, oldPassword);

        LoginResult newPassword = authService.login(USERNAME, "newpassword99", PROFILE_A, "PlayerA");
        assertInstanceOf(LoginResult.Success.class, newPassword);

        emailSender.clear();
        ForgotPasswordResult usernameStart = authService.beginRecovery(
                EMAIL,
                EmailChallengePurpose.USERNAME_RECOVERY,
                PROFILE_A
        );
        assertInstanceOf(ForgotPasswordResult.Accepted.class, usernameStart);
        String recoveryCode = emailSender.lastCode().orElseThrow();
        RecoverVerifyResult usernameVerify = authService.verifyRecoveryCode(
                ((ForgotPasswordResult.Accepted) usernameStart).accountIdOrNull(),
                EmailChallengePurpose.USERNAME_RECOVERY,
                recoveryCode,
                PROFILE_A
        );
        assertInstanceOf(RecoverVerifyResult.UsernameRevealed.class, usernameVerify);
        assertTrue(((RecoverVerifyResult.UsernameRevealed) usernameVerify).username().equals(USERNAME));
    }

    @Test
    @Order(6)
    void unknownRecoveryEmailDoesNotRevealAccount() {
        ForgotPasswordResult result = authService.beginRecovery(
                "missing_" + EMAIL,
                EmailChallengePurpose.PASSWORD_RESET,
                PROFILE_A
        );
        assertInstanceOf(ForgotPasswordResult.Accepted.class, result);
        assertFalse(((ForgotPasswordResult.Accepted) result).accountFound());
    }

    private static AuthConfig.DatabaseConfig resolveDatabaseConfig() {
        com.larpsmp.moneyevent.config.EnvSettings env = com.larpsmp.moneyevent.config.EnvSettings.load();
        String jdbcUrl = env.get("LARPSMP_JDBC_URL", "jdbc:postgresql://127.0.0.1:5433/larpsmp");
        String[][] candidates = {
                {env.get("LARPSMP_DB_USER", "larpsmp"), env.get("LARPSMP_DB_PASSWORD", "larpsmp")},
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
}
