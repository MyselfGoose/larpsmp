package com.larpsmp.moneyevent.auth;

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
 * Blocking authentication service: validation, hashing, persistence, and UUID binding.
 */
public final class AuthService {

    private static final Pattern USERNAME_CHARSET = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final AccountRepository repository;
    private final PasswordHasher passwordHasher;
    private final AuthRateLimiter rateLimiter;
    private final AuthConfig.SignupConfig signupConfig;
    private final AuthConfig.Messages messages;
    private final Logger logger;

    public AuthService(
            AccountRepository repository,
            PasswordHasher passwordHasher,
            AuthRateLimiter rateLimiter,
            AuthConfig.SignupConfig signupConfig,
            AuthConfig.Messages messages,
            Logger logger
    ) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.rateLimiter = rateLimiter;
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
            rateLimiter.clear(minecraftUuid);
            logger.info("Created account '" + username + "' bound to Minecraft UUID " + minecraftUuid);
            return new SignupResult.Success(account);
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
