package com.larpsmp.moneyevent.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Argon2id password hashing with PHC string encoding.
 *
 * <p>Encoded format:
 * {@code $argon2id$v=19$m=<memory>,t=<iterations>,p=<parallelism>$<salt>$<hash>}
 */
public final class PasswordHasher {

    private static final int DEFAULT_MEMORY_KIB = 65_536;
    private static final int DEFAULT_ITERATIONS = 3;
    private static final int DEFAULT_PARALLELISM = 1;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;

    private static final Pattern ENCODED_PATTERN = Pattern.compile(
            "^\\$argon2id\\$v=(\\d+)\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/]+)\\$([A-Za-z0-9+/]+)$"
    );

    private final SecureRandom secureRandom;
    private final int memoryKib;
    private final int iterations;
    private final int parallelism;

    public PasswordHasher() {
        this(new SecureRandom(), DEFAULT_MEMORY_KIB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM);
    }

    PasswordHasher(SecureRandom secureRandom, int memoryKib, int iterations, int parallelism) {
        this.secureRandom = secureRandom;
        this.memoryKib = memoryKib;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    public String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(password, salt, memoryKib, iterations, parallelism);
        return encode(memoryKib, iterations, parallelism, salt, hash);
    }

    public boolean verify(String password, String encodedHash) {
        if (password == null || password.isEmpty() || encodedHash == null || encodedHash.isBlank()) {
            return false;
        }

        Matcher matcher = ENCODED_PATTERN.matcher(encodedHash.trim());
        if (!matcher.matches()) {
            return false;
        }

        int memory = Integer.parseInt(matcher.group(2));
        int time = Integer.parseInt(matcher.group(3));
        int lanes = Integer.parseInt(matcher.group(4));
        byte[] salt = Base64.getDecoder().decode(matcher.group(5));
        byte[] expected = Base64.getDecoder().decode(matcher.group(6));
        byte[] actual = derive(password, salt, memory, time, lanes);
        return Arrays.equals(expected, actual);
    }

    private static byte[] derive(String password, byte[] salt, int memoryKib, int iterations, int parallelism) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKib)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] hash = new byte[HASH_LENGTH_BYTES];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), hash);
        return hash;
    }

    private static String encode(int memoryKib, int iterations, int parallelism, byte[] salt, byte[] hash) {
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return String.format(
                Locale.ROOT,
                "$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s",
                memoryKib,
                iterations,
                parallelism,
                encoder.encodeToString(salt),
                encoder.encodeToString(hash)
        );
    }
}
