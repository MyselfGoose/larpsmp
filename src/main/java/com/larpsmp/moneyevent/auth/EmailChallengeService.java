package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.email.EmailMessage;
import com.larpsmp.moneyevent.email.EmailSendException;
import com.larpsmp.moneyevent.email.EmailSender;
import com.larpsmp.moneyevent.email.EmailTemplates;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Issues, delivers, and verifies one-time email codes.
 */
public final class EmailChallengeService {

    public sealed interface IssueResult {
        record Sent(String maskedEmail) implements IssueResult {
        }

        record Cooldown(long retryAfterSeconds) implements IssueResult {
        }

        record EmailUnavailable() implements IssueResult {
        }

        record InternalError() implements IssueResult {
        }
    }

    public sealed interface VerifyResult {
        record Success(Account account) implements VerifyResult {
        }

        record InvalidCode() implements VerifyResult {
        }

        record Expired() implements VerifyResult {
        }

        record AttemptsExhausted() implements VerifyResult {
        }

        record NoChallenge() implements VerifyResult {
        }

        record InternalError() implements VerifyResult {
        }
    }

    private final EmailChallengeRepository challengeRepository;
    private final AccountRepository accountRepository;
    private final EmailSender emailSender;
    private final VerificationCodeHasher codeHasher;
    private final AuthConfig.EmailConfig emailConfig;
    private final boolean emailConfigured;
    private final Logger logger;

    public EmailChallengeService(
            EmailChallengeRepository challengeRepository,
            AccountRepository accountRepository,
            EmailSender emailSender,
            VerificationCodeHasher codeHasher,
            AuthConfig.EmailConfig emailConfig,
            boolean emailConfigured,
            Logger logger
    ) {
        this.challengeRepository = challengeRepository;
        this.accountRepository = accountRepository;
        this.emailSender = emailSender;
        this.codeHasher = Objects.requireNonNull(codeHasher, "codeHasher");
        this.emailConfig = Objects.requireNonNull(emailConfig, "emailConfig");
        this.emailConfigured = emailConfigured;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean isEmailConfigured() {
        return emailConfigured && emailSender != null;
    }

    public IssueResult issueAndSend(Account account, EmailChallengePurpose purpose) {
        if (!isEmailConfigured()) {
            return new IssueResult.EmailUnavailable();
        }

        try {
            Instant now = Instant.now();
            Optional<EmailChallenge> existing = challengeRepository.findLatestActive(account.id(), purpose);
            if (existing.isPresent()) {
                Duration sinceSent = Duration.between(existing.get().lastSentAt(), now);
                long cooldown = emailConfig.resendCooldownSeconds();
                if (sinceSent.getSeconds() < cooldown) {
                    return new IssueResult.Cooldown(cooldown - sinceSent.getSeconds());
                }
            }

            String code = codeHasher.generateCode();
            String codeHash = codeHasher.hash(code);
            Instant expiresAt = now.plusSeconds(emailConfig.codeTtlSeconds());

            challengeRepository.invalidateActive(account.id(), purpose);
            challengeRepository.create(
                    account.id(),
                    purpose,
                    codeHash,
                    expiresAt,
                    emailConfig.maxVerifyAttempts()
            );

            int ttlMinutes = Math.max(1, (int) Math.ceil(emailConfig.codeTtlSeconds() / 60.0));
            EmailMessage message = EmailTemplates.verificationCode(account.email(), purpose, code, ttlMinutes);
            emailSender.send(message);
            return new IssueResult.Sent(EmailTemplates.maskEmail(account.email()));
        } catch (EmailSendException exception) {
            logger.log(Level.WARNING, "Failed to send auth email for account " + account.id(), exception);
            return new IssueResult.EmailUnavailable();
        } catch (SQLException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to issue email challenge for account " + account.id(), exception);
            return new IssueResult.InternalError();
        }
    }

    /**
     * Re-sends using the same active challenge when still valid; otherwise issues a new one.
     */
    public IssueResult resend(Account account, EmailChallengePurpose purpose) {
        if (!isEmailConfigured()) {
            return new IssueResult.EmailUnavailable();
        }

        try {
            Instant now = Instant.now();
            Optional<EmailChallenge> existing = challengeRepository.findLatestActive(account.id(), purpose);
            if (existing.isPresent()) {
                EmailChallenge challenge = existing.get();
                Duration sinceSent = Duration.between(challenge.lastSentAt(), now);
                long cooldown = emailConfig.resendCooldownSeconds();
                if (sinceSent.getSeconds() < cooldown) {
                    return new IssueResult.Cooldown(cooldown - sinceSent.getSeconds());
                }
                if (!challenge.isExpired(now) && !challenge.attemptsExhausted()) {
                    // Cannot resend the same plaintext code (only the hash is stored) — issue a fresh code.
                    return issueAndSend(account, purpose);
                }
            }
            return issueAndSend(account, purpose);
        } catch (SQLException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to resend email challenge for account " + account.id(), exception);
            return new IssueResult.InternalError();
        }
    }

    public VerifyResult verify(UUID accountId, EmailChallengePurpose purpose, String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return new VerifyResult.InvalidCode();
        }

        try {
            Optional<EmailChallenge> challengeOpt = challengeRepository.findLatestActive(accountId, purpose);
            if (challengeOpt.isEmpty()) {
                return new VerifyResult.NoChallenge();
            }

            EmailChallenge challenge = challengeOpt.get();
            Instant now = Instant.now();
            if (challenge.isExpired(now)) {
                challengeRepository.markConsumed(challenge.id());
                return new VerifyResult.Expired();
            }
            if (challenge.attemptsExhausted()) {
                return new VerifyResult.AttemptsExhausted();
            }

            if (!codeHasher.matches(rawCode.trim(), challenge.codeHash())) {
                challengeRepository.incrementAttempts(challenge.id());
                EmailChallenge after = challengeRepository.findLatestActive(accountId, purpose).orElse(challenge);
                if (after.attemptsExhausted()) {
                    return new VerifyResult.AttemptsExhausted();
                }
                return new VerifyResult.InvalidCode();
            }

            challengeRepository.markConsumed(challenge.id());
            Optional<Account> account = accountRepository.findById(accountId);
            if (account.isEmpty()) {
                return new VerifyResult.InternalError();
            }
            return new VerifyResult.Success(account.get());
        } catch (SQLException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to verify email challenge for account " + accountId, exception);
            return new VerifyResult.InternalError();
        }
    }
}
