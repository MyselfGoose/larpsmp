package com.larpsmp.moneyevent.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Applies ordered SQL migrations from {@code db/migrations} on the classpath.
 *
 * <p>Migration files must be named {@code V###__description.sql} (Flyway-style).
 * Applied versions are tracked in {@code schema_migrations}.
 */
public final class MigrationRunner {

    private static final String MIGRATIONS_PATH = "db/migrations";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");

    private final DataSource dataSource;
    private final Logger logger;

    public MigrationRunner(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
    }

    public void migrate() throws SQLException, IOException {
        ensureMigrationsTable();
        Set<String> applied = loadAppliedVersions();
        List<MigrationFile> pending = discoverMigrations().stream()
                .filter(migration -> !applied.contains(migration.version()))
                .sorted(Comparator.comparing(MigrationFile::version))
                .toList();

        if (pending.isEmpty()) {
            logger.info("Database schema is up to date.");
            return;
        }

        for (MigrationFile migration : pending) {
            applyMigration(migration);
        }
    }

    private void ensureMigrationsTable() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version     TEXT PRIMARY KEY,
                    applied_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Set<String> loadAppliedVersions() throws SQLException {
        Set<String> versions = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT version FROM schema_migrations");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                versions.add(resultSet.getString("version"));
            }
        }
        return versions;
    }

    private List<MigrationFile> discoverMigrations() throws IOException {
        ClassLoader classLoader = MigrationRunner.class.getClassLoader();
        Enumeration<URL> resources = classLoader.getResources(MIGRATIONS_PATH);
        List<MigrationFile> migrations = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();
            if ("file".equals(protocol)) {
                try {
                    collectFromDirectory(Paths.get(resource.toURI()), migrations, seen);
                } catch (URISyntaxException exception) {
                    collectFromDirectory(Paths.get(resource.getPath()), migrations, seen);
                }
            } else if ("jar".equals(protocol)) {
                collectFromJar(resource, migrations, seen);
            } else {
                logger.warning("Unsupported migration resource protocol: " + protocol);
            }
        }
        return migrations;
    }

    private void collectFromDirectory(Path migrationsDir, List<MigrationFile> migrations, Set<String> seen)
            throws IOException {
        if (!Files.isDirectory(migrationsDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(migrationsDir, "*.sql")) {
            for (Path path : stream) {
                addMigration(path.getFileName().toString(), migrations, seen);
            }
        }
    }

    private void collectFromJar(URL resource, List<MigrationFile> migrations, Set<String> seen) throws IOException {
        String path = resource.getPath();
        int separator = path.indexOf('!');
        if (separator < 0) {
            return;
        }
        String jarPath = path.substring(0, separator);
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring(5);
        }
        jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);

        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            String prefix = MIGRATIONS_PATH + "/";
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".sql")) {
                    continue;
                }
                String fileName = name.substring(prefix.length());
                if (fileName.contains("/")) {
                    continue;
                }
                addMigration(fileName, migrations, seen);
            }
        }
    }

    private void addMigration(String fileName, List<MigrationFile> migrations, Set<String> seen) {
        Matcher matcher = VERSION_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            logger.warning("Skipping unrecognized migration file: " + fileName);
            return;
        }
        if (!seen.add(fileName)) {
            return;
        }
        migrations.add(new MigrationFile(matcher.group(1), fileName));
    }

    private void applyMigration(MigrationFile migration) throws SQLException, IOException {
        String sql = readClasspathResource(MIGRATIONS_PATH + "/" + migration.fileName());
        logger.info("Applying migration V" + migration.version() + " (" + migration.fileName() + ")...");

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String part : splitSqlStatements(sql)) {
                    statement.execute(part);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_migrations (version) VALUES (?)")) {
                    insert.setString(1, migration.version());
                    insert.executeUpdate();
                }
                connection.commit();
                logger.info("Applied migration V" + migration.version() + ".");
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /**
     * Splits a migration script into individual statements.
     * Comments and blank lines are ignored; statements end at {@code ;}.
     */
    private static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : sql.split("\n")) {
            String line = rawLine.stripTrailing();
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String statement = current.toString().trim();
                if (statement.endsWith(";")) {
                    statement = statement.substring(0, statement.length() - 1).trim();
                }
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
            }
        }
        String trailing = current.toString().trim();
        if (!trailing.isEmpty()) {
            statements.add(trailing);
        }
        return statements;
    }

    private static String readClasspathResource(String path) throws IOException {
        ClassLoader classLoader = MigrationRunner.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("Migration resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        }
    }

    private record MigrationFile(String version, String fileName) {
    }
}
