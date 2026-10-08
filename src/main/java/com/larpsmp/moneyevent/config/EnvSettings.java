package com.larpsmp.moneyevent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads secrets from the process environment and an optional project-root {@code .env} file.
 * Resolution order for each key: {@code System.getenv} → {@code .env} → caller fallback.
 */
public final class EnvSettings {

    public static final String JDBC_URL = "LARPSMP_JDBC_URL";
    public static final String DB_USER = "LARPSMP_DB_USER";
    public static final String DB_PASSWORD = "LARPSMP_DB_PASSWORD";
    public static final String DB_POOL_SIZE = "LARPSMP_DB_POOL_SIZE";
    public static final String POSTGRES_DB = "LARPSMP_POSTGRES_DB";
    public static final String POSTGRES_USER = "LARPSMP_POSTGRES_USER";
    public static final String POSTGRES_PASSWORD = "LARPSMP_POSTGRES_PASSWORD";
    public static final String POSTGRES_HOST = "LARPSMP_POSTGRES_HOST";
    public static final String POSTGRES_PORT = "LARPSMP_POSTGRES_PORT";
    public static final String PGADMIN_EMAIL = "LARPSMP_PGADMIN_EMAIL";
    public static final String PGADMIN_PASSWORD = "LARPSMP_PGADMIN_PASSWORD";
    public static final String PGADMIN_PORT = "LARPSMP_PGADMIN_PORT";
    public static final String RESEND_API_KEY = "LARPSMP_RESEND_API_KEY";
    public static final String RESEND_FROM_EMAIL = "LARPSMP_RESEND_FROM_EMAIL";
    public static final String EMAIL_CODE_PEPPER = "LARPSMP_EMAIL_CODE_PEPPER";
    public static final String STORAGE_BASE_URL = "LARPSMP_STORAGE_BASE_URL";
    public static final String STORAGE_API_KEY = "LARPSMP_STORAGE_API_KEY";

    private final Map<String, String> fileValues;
    private final Path loadedFrom;

    private EnvSettings(Map<String, String> fileValues, Path loadedFrom) {
        this.fileValues = Collections.unmodifiableMap(fileValues);
        this.loadedFrom = loadedFrom;
    }

    public static EnvSettings load() {
        Optional<Path> envFile = resolveEnvFile();
        if (envFile.isEmpty()) {
            return new EnvSettings(Map.of(), null);
        }
        try {
            return new EnvSettings(parseDotEnv(Files.readString(envFile.get(), StandardCharsets.UTF_8)), envFile.get());
        } catch (IOException exception) {
            return new EnvSettings(Map.of(), null);
        }
    }

    /** Visible for tests — parse dotenv text without touching the filesystem. */
    static Map<String, String> parseDotEnv(String contents) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : contents.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.regionMatches(true, 0, "export ", 0, 7)) {
                line = line.substring(7).trim();
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            if (key.isEmpty() || !isValidKey(key)) {
                continue;
            }
            String value = unquote(line.substring(equals + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    public Optional<Path> loadedFrom() {
        return Optional.ofNullable(loadedFrom);
    }

    public String get(String key, String fallback) {
        String fromProcess = System.getenv(key);
        if (fromProcess != null && !fromProcess.isBlank()) {
            return fromProcess;
        }
        String fromFile = fileValues.get(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile;
        }
        return fallback;
    }

    public Optional<String> getOptional(String key) {
        String value = get(key, "");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public int getInt(String key, int fallback) {
        String raw = get(key, "");
        if (raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Optional<Path> resolveEnvFile() {
        String override = System.getenv("LARPSMP_ENV_FILE");
        if (override == null || override.isBlank()) {
            override = System.getProperty("larpsmp.env.file", "");
        }
        if (!override.isBlank()) {
            Path explicit = Path.of(override).toAbsolutePath().normalize();
            if (Files.isRegularFile(explicit)) {
                return Optional.of(explicit);
            }
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            if (Files.isRegularFile(dir.resolve("settings.gradle"))
                    || Files.isRegularFile(dir.resolve("settings.gradle.kts"))
                    || Files.isRegularFile(dir.resolve("build.gradle"))
                    || Files.isRegularFile(dir.resolve("build.gradle.kts"))) {
                // Stop at repo root even if .env is missing.
                break;
            }
        }

        Path parentEnv = cwd.resolve("..").resolve(".env").normalize();
        if (Files.isRegularFile(parentEnv)) {
            return Optional.of(parentEnv);
        }
        return Optional.empty();
    }

    private static boolean isValidKey(String key) {
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        // Strip inline comments for unquoted values: KEY=value # comment
        int hash = indexOfUnescapedHash(value);
        if (hash >= 0) {
            return value.substring(0, hash).trim();
        }
        return value;
    }

    private static int indexOfUnescapedHash(String value) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble) {
                if (i == 0 || Character.isWhitespace(value.charAt(i - 1))) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return "EnvSettings{loadedFrom=" + (loadedFrom == null ? "none" : loadedFrom)
                + ", fileKeys=" + fileValues.size() + "}";
    }
}
