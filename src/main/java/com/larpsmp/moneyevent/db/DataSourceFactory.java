package com.larpsmp.moneyevent.db;

import com.larpsmp.moneyevent.auth.AuthConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Builds a HikariCP pool from plugin database settings.
 *
 * <p>Paper loads plugin {@code libraries} in an isolated classloader. HikariCP must be
 * told the driver class explicitly; otherwise {@code DriverManager} cannot see the
 * PostgreSQL driver and pool creation fails with "Failed to get driver instance".
 */
public final class DataSourceFactory {

    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";

    private DataSourceFactory() {
    }

    public static HikariDataSource create(AuthConfig.DatabaseConfig database) {
        ensureDriverLoaded();

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(POSTGRES_DRIVER);
        config.setJdbcUrl(database.jdbcUrl());
        config.setUsername(database.username());
        config.setPassword(database.password());
        config.setMaximumPoolSize(database.poolSize());
        config.setPoolName("larpsmp-auth");
        config.setAutoCommit(true);
        config.addDataSourceProperty("ApplicationName", "MoneyEvent");
        return new HikariDataSource(config);
    }

    private static void ensureDriverLoaded() {
        try {
            Class.forName(POSTGRES_DRIVER, true, DataSourceFactory.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "PostgreSQL JDBC driver not found on the plugin classpath. "
                            + "Ensure org.postgresql:postgresql is listed under plugin.yml libraries.",
                    exception
            );
        }
    }
}
