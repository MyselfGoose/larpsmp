package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Account-scoped plugin admin grants. Minecraft client UUID / launcher name are irrelevant;
 * only the LarpSMP account username matters.
 */
public final class AccountAdminService {

    private final AccountAdminRepository repository;
    private final AccountRepository accounts;
    private final AccountSessionManager sessions;
    private final Consumer<String> errorLogger;

    public AccountAdminService(
            AccountAdminRepository repository,
            AccountRepository accounts,
            AccountSessionManager sessions,
            Consumer<String> errorLogger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public boolean isAdminMinecraftUuid(UUID minecraftUuid) {
        return sessions.findByMinecraftUuid(minecraftUuid)
                .map(AccountSession::accountId)
                .map(this::isAdminAccount)
                .orElse(false);
    }

    public boolean isAdminAccount(UUID accountId) {
        try {
            return repository.isAdmin(accountId);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to check plugin admin for " + accountId + ": " + exception.getMessage());
            return false;
        }
    }

    public Optional<Account> findAccount(String username) {
        try {
            return accounts.findByUsernameIgnoreCase(username == null ? "" : username.trim());
        } catch (SQLException exception) {
            errorLogger.accept("Failed to resolve account '" + username + "': " + exception.getMessage());
            return Optional.empty();
        }
    }

    public GrantResult grant(String username, String grantedBy) {
        Optional<Account> account = findAccount(username);
        if (account.isEmpty()) {
            return GrantResult.notFound(username);
        }
        try {
            boolean created = repository.grant(account.get().id(), grantedBy);
            return created
                    ? GrantResult.granted(account.get())
                    : GrantResult.already(account.get());
        } catch (SQLException exception) {
            errorLogger.accept("Failed to grant plugin admin to '" + username + "': " + exception.getMessage());
            return GrantResult.failed(exception.getMessage());
        }
    }

    public GrantResult revoke(String username) {
        Optional<Account> account = findAccount(username);
        if (account.isEmpty()) {
            return GrantResult.notFound(username);
        }
        try {
            boolean removed = repository.revoke(account.get().id());
            return removed
                    ? GrantResult.revoked(account.get())
                    : GrantResult.notAdmin(account.get());
        } catch (SQLException exception) {
            errorLogger.accept("Failed to revoke plugin admin from '" + username + "': " + exception.getMessage());
            return GrantResult.failed(exception.getMessage());
        }
    }

    public List<AccountAdminRepository.AdminRow> listAdmins() {
        try {
            return repository.listAdmins();
        } catch (SQLException exception) {
            errorLogger.accept("Failed to list plugin admins: " + exception.getMessage());
            return List.of();
        }
    }

    /**
     * Aligns Paper's vanilla op flag with account admin status for this online player.
     * Call on join after the account session exists.
     */
    public void syncBukkitOperator(Player player) {
        boolean admin = isAdminMinecraftUuid(player.getUniqueId());
        if (player.isOp() != admin) {
            player.setOp(admin);
        }
    }

    public void syncBukkitOperatorIfOnline(UUID accountId, PlayerLookup onlineLookup) {
        sessions.findByAccountId(accountId).ifPresent(session -> {
            Player player = onlineLookup.find(session.minecraftUuid());
            if (player != null) {
                syncBukkitOperator(player);
            }
        });
    }

    @FunctionalInterface
    public interface PlayerLookup {
        @Nullable Player find(UUID minecraftUuid);
    }

    public sealed interface GrantResult {
        record Granted(Account account) implements GrantResult {
        }

        record Already(Account account) implements GrantResult {
        }

        record Revoked(Account account) implements GrantResult {
        }

        record NotAdmin(Account account) implements GrantResult {
        }

        record NotFound(String username) implements GrantResult {
        }

        record Failed(String message) implements GrantResult {
        }

        static GrantResult granted(Account account) {
            return new Granted(account);
        }

        static GrantResult already(Account account) {
            return new Already(account);
        }

        static GrantResult revoked(Account account) {
            return new Revoked(account);
        }

        static GrantResult notAdmin(Account account) {
            return new NotAdmin(account);
        }

        static GrantResult notFound(String username) {
            return new NotFound(username);
        }

        static GrantResult failed(String message) {
            return new Failed(message);
        }
    }
}
