package com.larpsmp.moneyevent;

import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.auth.AuthConfig;
import com.larpsmp.moneyevent.auth.AuthConnectionListener;
import com.larpsmp.moneyevent.auth.AuthDialogFactory;
import com.larpsmp.moneyevent.auth.AuthRateLimiter;
import com.larpsmp.moneyevent.auth.AuthService;
import com.larpsmp.moneyevent.auth.AuthSessionManager;
import com.larpsmp.moneyevent.auth.PasswordHasher;
import com.larpsmp.moneyevent.config.EnvSettings;
import com.larpsmp.moneyevent.db.DataSourceFactory;
import com.larpsmp.moneyevent.db.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.java.JavaPlugin;

public final class MoneyEventPlugin extends JavaPlugin {

    private AuthSessionManager authSessionManager;
    private HikariDataSource dataSource;
    private ExecutorService authExecutor;
    private ScheduledExecutorService rateLimitCleanupExecutor;
    private boolean databaseReady;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        EnvSettings env = EnvSettings.load();
        env.loadedFrom().ifPresentOrElse(
                path -> getLogger().info("Loaded secrets from " + path.toAbsolutePath() + "."),
                () -> getLogger().info("No project .env found; using process env / config.yml fallbacks.")
        );
        AuthConfig authConfig = AuthConfig.from(getConfig(), env);
        authSessionManager = new AuthSessionManager();
        AuthDialogFactory dialogFactory = new AuthDialogFactory(authConfig.messages());

        AuthService authService = null;
        databaseReady = false;

        if (authConfig.enabled()) {
            try {
                dataSource = DataSourceFactory.create(authConfig.database());
                try (var connection = dataSource.getConnection()) {
                    connection.isValid(5);
                }
                new MigrationRunner(dataSource, getLogger()).migrate();

                PasswordHasher passwordHasher = new PasswordHasher();
                AccountRepository accountRepository = new AccountRepository(dataSource);
                AuthRateLimiter rateLimiter = new AuthRateLimiter(authConfig.rateLimit());
                authService = new AuthService(
                        accountRepository,
                        passwordHasher,
                        rateLimiter,
                        authConfig.signup(),
                        authConfig.messages(),
                        getLogger()
                );

                AtomicInteger threadCounter = new AtomicInteger();
                authExecutor = Executors.newFixedThreadPool(
                        authConfig.database().poolSize(),
                        runnable -> {
                            Thread thread = new Thread(runnable, "larpsmp-auth-" + threadCounter.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        }
                );

                rateLimitCleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "larpsmp-auth-rate-limit-cleanup");
                    thread.setDaemon(true);
                    return thread;
                });
                rateLimitCleanupExecutor.scheduleAtFixedRate(
                        rateLimiter::purgeExpired,
                        60L,
                        60L,
                        TimeUnit.SECONDS
                );

                databaseReady = true;
                getLogger().info("PostgreSQL authentication ready ("
                        + authConfig.database().jdbcUrl() + ").");
                if (authConfig.integrations().resendConfigured()) {
                    getLogger().info("Resend API key present in environment.");
                }
                if (authConfig.integrations().storageConfigured()) {
                    getLogger().info("Remote storage credentials present in environment.");
                }
            } catch (Exception exception) {
                getLogger().log(
                        java.util.logging.Level.SEVERE,
                        "Failed to initialize authentication database: " + exception.getMessage(),
                        exception
                );
                getLogger().severe("Players will be denied until the database is available.");
                closeDataSourceQuietly();
                databaseReady = false;
                authService = null;
            }
        } else {
            getLogger().info("Pre-join authentication disabled.");
        }

        ExecutorService listenerExecutor = authExecutor != null
                ? authExecutor
                : Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "larpsmp-auth-fallback");
                    thread.setDaemon(true);
                    return thread;
                });
        if (authExecutor == null) {
            // Keep a handle so onDisable can shut down the fallback executor.
            authExecutor = listenerExecutor;
        }

        AuthConnectionListener authListener = new AuthConnectionListener(
                authConfig,
                authService,
                authSessionManager,
                dialogFactory,
                listenerExecutor,
                databaseReady,
                getLogger()
        );
        getServer().getPluginManager().registerEvents(authListener, this);

        if (authConfig.enabled() && databaseReady) {
            getLogger().info("Pre-join authentication enabled (database-backed).");
        }
        getLogger().info("Money Event plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (authSessionManager != null) {
            authSessionManager.cancelAll();
            authSessionManager = null;
        }
        shutdownExecutor(authExecutor, "auth executor");
        authExecutor = null;
        shutdownExecutor(rateLimitCleanupExecutor, "rate-limit cleanup executor");
        rateLimitCleanupExecutor = null;
        closeDataSourceQuietly();
        getLogger().info("Money Event plugin disabled.");
    }

    private void closeDataSourceQuietly() {
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (Exception exception) {
                getLogger().warning("Error closing datasource: " + exception.getMessage());
            }
            dataSource = null;
        }
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                getLogger().warning("Timed out waiting for " + name + " to stop.");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            getLogger().warning("Interrupted while shutting down " + name + ".");
        }
    }
}
