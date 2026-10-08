package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.email.EmailTemplates;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

/**
 * Blocking authentication service: validation, hashing, persistence, UUID binding,
 * and email verification / recovery orchestration.
 */
public final class AuthService {

    private static final Pattern USERNAME_CHARSET = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final AccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final AuthRateLimiter rateLimiter;
    private final EmailChallengeService emailChallengeService;
    private final AuthConfig.SignupConfig signupConfig;
    private final AuthConfig.Messages messages;
    private final Logger logger;

    public AuthService(
            AccountRepository repository,
            PasswordHasher passwordHasher,
            AuthRateLimiter rateLimiter,
            EmailChallengeService emailChallengeService,
            AuthConfig.SignupConfig signupConfig,
            AuthConfig.Messages messages,
            Logger logger
    ) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.rateLimiter = rateLimiter;
        this.emailChallengeService = emailChallengeService;
        this.signupConfig = signupConfig;
        this.messages = messages;
        this.logger = logger;
    }

    public SignupResult signup(
            String rawUsername,
            String rawEmail,
            String password,
            UUID minecraftUuid,
            @Nullable String minecraftName
    ) {
        if (rateLimiter.isLimited(minecraftUuid)) {
            return new SignupResult.RateLimited();
        }

        Optional<String> validationError = validateSignupFields(rawUsername, rawEmail, password);
        if (validationError.isPresent()) {
            return new SignupResult.ValidationError(validationError.get());
        }

        if (!emailChallengeService.isEmailConfigured()) {
            return new SignupResult.EmailUnavailable();
        }

        String username = normalize(rawUsername);
        String email = normalize(rawEmail);

        try {
            if (repository.findByUsername(username).isPresent()) {
                rateLimiter.recordFailure(minecraftUuid);
                return new SignupResult.UsernameTaken();
            }
            if (repository.findByEmail(email).isPresent()) {
                rateLimiter.recordFailure(minecraftUuid);
                return new SignupResult.EmailTaken();
            }

            Optional<AccountIdentity> existingIdentity = repository.findIdentityByMinecraftUuid(minecraftUuid);
            if (existingIdentity.isPresent()) {
                rateLimiter.recordFailure(minecraftUuid);
                return new SignupResult.ValidationError(messages.uuidAlreadyBound());
            }

            String passwordHash = passwordHasher.hash(password);
            Account account = repository.createAccountWithIdentity(
                    username,
                    email,
                    passwordHash,
                    minecraftUuid,
                    minecraftName
            );

            EmailChallengeService.IssueResult issue =
                    emailChallengeService.issueAndSend(account, EmailChallengePurpose.SIGNUP_VERIFY);
            rateLimiter.clear(minecraftUuid);
            logger.info("Created unverified account '" + username
                    + "' bound to Minecraft UUID " + minecraftUuid);
            String masked = EmailTemplates.maskEmail(account.email());
            return switch (issue) {
                case EmailChallengeService.IssueResult.Sent sent ->
                        new SignupResult.PendingVerification(account, sent.maskedEmail());
                case EmailChallengeService.IssueResult.Cooldown ignored ->
                        new SignupResult.PendingVerification(account, masked);
                case EmailChallengeService.IssueResult.EmailUnavailable ignored ->
                        // Account exists; player can resend from the verify dialog.
                        new SignupResult.PendingVerification(account, masked);
                case EmailChallengeService.IssueResult.InternalError ignored ->
                        new SignupResult.PendingVerification(account, masked);
            };
        } catch (SQLException exception) {
            if (isUniqueViolation(exception)) {
                String detail = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
                rateLimiter.recordFailure(minecraftUuid);
                if (detail.contains("email")) {
                    return new SignupResult.EmailTaken();
                }
                if (detail.contains("username")) {
                    return new SignupResult.UsernameTaken();
                }
                if (detail.contains("minecraft_uuid")) {
                    return new SignupResult.ValidationError(messages.uuidAlreadyBound());
                }
                return new SignupResult.UsernameTaken();
            }
            logger.log(Level.SEVERE, "Signup failed for Minecraft UUID " + minecraftUuid, exception);
            return new SignupResult.InternalError();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Signup failed for Minecraft UUID " + minecraftUuid, exception);
            return new SignupResult.InternalError();
        }
    }

    public LoginResult login(
            String rawIdentifier,
            String password,
            UUID minecraftUuid,
            @Nullable String minecraftName
    ) {
        if (rateLimiter.isLimited(minecraftUuid)) {
            return new LoginResult.RateLimited();
        }

        if (isBlank(rawIdentifier) || password == null || password.isEmpty()) {
            return new LoginResult.InvalidCredentials();
        }

        String identifier = normalize(rawIdentifier);

        try {
            Optional<Account> accountOpt = repository.findByUsernameOrEmail(identifier);
            if (accountOpt.isEmpty()) {
                rateLimiter.recordFailure(minecraftUuid);
                return new LoginResult.InvalidCredentials();
            }

            Account account = accountOpt.get();
            if (!passwordHasher.verify(password, account.passwordHash())) {
                rateLimiter.recordFailure(minecraftUuid);
                return new LoginResult.InvalidCredentials();
            }

            Optional<AccountIdentity> uuidIdentity = repository.findIdentityByMinecraftUuid(minecraftUuid);
            if (uuidIdentity.isPresent() && !uuidIdentity.get().accountId().equals(account.id())) {
                rateLimiter.recordFailure(minecraftUuid);
                return new LoginResult.UuidBoundToOtherAccount();
            }

            Optional<AccountIdentity> accountIdentity = repository.findIdentityByAccountId(account.id());
            if (accountIdentity.isPresent() && !accountIdentity.get().minecraftUuid().equals(minecraftUuid)) {
                rateLimiter.recordFailure(minecraftUuid);
                return new LoginResult.AccountBoundToOtherUuid();
            }

            if (!account.emailVerified()) {
                EmailChallengeService.IssueResult issue =
                        emailChallengeService.issueAndSend(account, EmailChallengePurpose.SIGNUP_VERIFY);
                boolean sent = issue instanceof EmailChallengeService.IssueResult.Sent
                        || issue instanceof EmailChallengeService.IssueResult.Cooldown;
                rateLimiter.clear(minecraftUuid);
                return new LoginResult.EmailNotVerified(
                        account,
                        EmailTemplates.maskEmail(account.email()),
                        sent
                );
            }

            if (uuidIdentity.isPresent()) {
                repository.updateIdentityLastSeen(uuidIdentity.get().id(), minecraftName);
            } else if (accountIdentity.isEmpty()) {
                repository.bindIdentity(account.id(), minecraftUuid, minecraftName);
            }

            repository.updateLastLogin(account.id());
            rateLimiter.clear(minecraftUuid);
            logger.info("Account '" + account.username() + "' logged in from Minecraft UUID " + minecraftUuid);
            return new LoginResult.Success(account);
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Login failed for Minecraft UUID " + minecraftUuid, exception);
            return new LoginResult.InternalError();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Login failed for Minecraft UUID " + minecraftUuid, exception);
            return new LoginResult.InternalError();
        }
    }

    public VerifyEmailResult verifySignupEmail(UUID accountId, String code, UUID minecraftUuid) {
        if (rateLimiter.isLimited(minecraftUuid)) {
            return new VerifyEmailResult.InternalError();
        }

        EmailChallengeService.VerifyResult result =
                emailChallengeService.verify(accountId, EmailChallengePurpose.SIGNUP_VERIFY, code);
        return switch (result) {
            case EmailChallengeService.VerifyResult.Success success -> {
                try {
                    repository.markEmailVerified(success.account().id());
                    Optional<Account> refreshed = repository.findById(success.account().id());
                    rateLimiter.clear(minecraftUuid);
                    logger.info("Email verified for account '" + success.account().username() + "'");
                    yield new VerifyEmailResult.Success(refreshed.orElse(success.account()));
                } catch (SQLException exception) {
                    logger.log(Level.SEVERE, "Failed to mark email verified for " + accountId, exception);
                    yield new VerifyEmailResult.InternalError();
                }
            }
            case EmailChallengeService.VerifyResult.InvalidCode ignored -> {
                rateLimiter.recordFailure(minecraftUuid);
                yield new VerifyEmailResult.InvalidCode();
            }
            case EmailChallengeService.VerifyResult.Expired ignored -> new VerifyEmailResult.Expired();
            case EmailChallengeService.VerifyResult.AttemptsExhausted ignored ->
                    new VerifyEmailResult.AttemptsExhausted();
            case EmailChallengeService.VerifyResult.NoChallenge ignored -> new VerifyEmailResult.NoChallenge();
            case EmailChallengeService.VerifyResult.InternalError ignored -> new VerifyEmailResult.InternalError();
        };
    }

    public EmailChallengeService.IssueResult resendSignupCode(Account account) {
        return emailChallengeService.resend(account, EmailChallengePurpose.SIGNUP_VERIFY);
    }

    public ForgotPasswordResult beginRecovery(
            String rawEmail,
            EmailChallengePurpose purpose,
            UUID minecraftUuid
    ) {
        if (purpose != EmailChallengePurpose.PASSWORD_RESET
                && purpose != EmailChallengePurpose.USERNAME_RECOVERY) {
            return new ForgotPasswordResult.InternalError();
        }
        if (rateLimiter.isLimited(minecraftUuid)) {
            return new ForgotPasswordResult.RateLimited();
        }
        if (isBlank(rawEmail) || !EMAIL_PATTERN.matcher(rawEmail.trim()).matches()) {
            return new ForgotPasswordResult.InvalidEmail();
        }
        if (!emailChallengeService.isEmailConfigured()) {
            return new ForgotPasswordResult.EmailUnavailable();
        }

        String email = normalize(rawEmail);
        try {
            Optional<Account> accountOpt = repository.findByEmail(email);
            if (accountOpt.isEmpty()) {
                // Constant-ish work: hash a dummy password-length string is unnecessary;
                // still clear nothing and return Accepted without revealing absence.
                return new ForgotPasswordResult.Accepted(null, EmailTemplates.maskEmail(email), false);
            }

            Account account = accountOpt.get();
            EmailChallengeService.IssueResult issue = emailChallengeService.issueAndSend(account, purpose);
            return switch (issue) {
                case EmailChallengeService.IssueResult.Sent sent ->
                        new ForgotPasswordResult.Accepted(account.id(), sent.maskedEmail(), true);
                case EmailChallengeService.IssueResult.Cooldown cooldown ->
                        new ForgotPasswordResult.Cooldown(cooldown.retryAfterSeconds());
                case EmailChallengeService.IssueResult.EmailUnavailable ignored ->
                        new ForgotPasswordResult.EmailUnavailable();
                case EmailChallengeService.IssueResult.InternalError ignored ->
                        new ForgotPasswordResult.InternalError();
            };
        } catch (SQLException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Recovery start failed for Minecraft UUID " + minecraftUuid, exception);
            return new ForgotPasswordResult.InternalError();
        }
    }

    public RecoverVerifyResult verifyRecoveryCode(
            UUID accountId,
            EmailChallengePurpose purpose,
            String code,
            UUID minecraftUuid
    ) {
        if (purpose != EmailChallengePurpose.PASSWORD_RESET
                && purpose != EmailChallengePurpose.USERNAME_RECOVERY) {
            return new RecoverVerifyResult.InternalError();
        }
        if (rateLimiter.isLimited(minecraftUuid)) {
            return new RecoverVerifyResult.InternalError();
        }

        EmailChallengeService.VerifyResult result = emailChallengeService.verify(accountId, purpose, code);
        return switch (result) {
            case EmailChallengeService.VerifyResult.Success success -> {
                if (purpose == EmailChallengePurpose.USERNAME_RECOVERY) {
                    yield new RecoverVerifyResult.UsernameRevealed(success.account().username());
                }
                yield new RecoverVerifyResult.PasswordResetAuthorized(success.account());
            }
            case EmailChallengeService.VerifyResult.InvalidCode ignored -> {
                rateLimiter.recordFailure(minecraftUuid);
                yield new RecoverVerifyResult.InvalidCode();
            }
            case EmailChallengeService.VerifyResult.Expired ignored -> new RecoverVerifyResult.Expired();
            case EmailChallengeService.VerifyResult.AttemptsExhausted ignored ->
                    new RecoverVerifyResult.AttemptsExhausted();
            case EmailChallengeService.VerifyResult.NoChallenge ignored -> new RecoverVerifyResult.NoChallenge();
            case EmailChallengeService.VerifyResult.InternalError ignored -> new RecoverVerifyResult.InternalError();
        };
    }

    public EmailChallengeService.IssueResult resendRecoveryCode(Account account, EmailChallengePurpose purpose) {
        return emailChallengeService.resend(account, purpose);
    }

    public ResetPasswordResult resetPassword(UUID accountId, String newPassword, String confirmPassword) {
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            return new ResetPasswordResult.ValidationError(messages.resetPasswordMismatch());
        }
        if (newPassword.length() < signupConfig.minPasswordLength()
                || newPassword.length() > signupConfig.maxPasswordLength()) {
            return new ResetPasswordResult.ValidationError(messages.resetPasswordInvalid());
        }

        try {
            Optional<Account> account = repository.findById(accountId);
            if (account.isEmpty()) {
                return new ResetPasswordResult.Unauthorized();
            }
            String hash = passwordHasher.hash(newPassword);
            repository.updatePasswordHash(accountId, hash);
            logger.info("Password reset for account '" + account.get().username() + "'");
            return new ResetPasswordResult.Success();
        } catch (SQLException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Password reset failed for account " + accountId, exception);
            return new ResetPasswordResult.InternalError();
        }
    }

    public Optional<Account> findAccount(UUID accountId) {
        try {
            return repository.findById(accountId);
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Failed to load account " + accountId, exception);
            return Optional.empty();
        }
    }

    /**
     * @return empty when valid; otherwise a player-facing error message
     */
    public Optional<String> validateSignupFields(String username, String email, String password) {
        if (isBlank(username) || isBlank(email) || password == null || password.isEmpty()) {
            return Optional.of(messages.signupEmptyFields());
        }

        String trimmedUsername = username.trim();
        if (trimmedUsername.length() < signupConfig.minUsernameLength()
                || trimmedUsername.length() > signupConfig.maxUsernameLength()
                || !USERNAME_CHARSET.matcher(trimmedUsername).matches()) {
            return Optional.of(messages.signupInvalidUsername());
        }

        String trimmedEmail = email.trim();
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            return Optional.of(messages.signupInvalidEmail());
        }

        if (password.length() < signupConfig.minPasswordLength()
                || password.length() > signupConfig.maxPasswordLength()) {
            return Optional.of(messages.signupInvalidPassword());
        }

        return Optional.empty();
    }

    public boolean hasEmptyLoginFields(String identifier, String password) {
        return isBlank(identifier) || password == null || password.isEmpty();
    }

    public boolean isValidEmailFormat(String email) {
        return !isBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private static boolean isUniqueViolation(SQLException exception) {
        if (exception instanceof PSQLException psqlException) {
            String sqlState = psqlException.getSQLState();
            return PSQLState.UNIQUE_VIOLATION.getState().equals(sqlState);
        }
        String sqlState = exception.getSQLState();
        return "23505".equals(sqlState);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
