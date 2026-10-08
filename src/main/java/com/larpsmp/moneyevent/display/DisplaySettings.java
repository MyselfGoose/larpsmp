package com.larpsmp.moneyevent.display;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.Consumer;

public final class DisplaySettings {
    private final Path file;
    private final Consumer<String> warningLogger;
    private boolean enabled;

    public DisplaySettings(Path file, Consumer<String> warningLogger) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        this.warningLogger = warningLogger;
        Files.createDirectories(this.file.getParent());
        enabled = load();
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) throws IOException {
        this.enabled = enabled;
        save();
    }

    public void save() throws IOException {
        Properties values = new Properties();
        values.setProperty("enabled", Boolean.toString(enabled));
        Path temporary = Files.createTempFile(file.getParent(), "display-", ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "LarpSMP personal balance display");
            }
            try {
                Files.move(temporary, file,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private boolean load() throws IOException {
        if (!Files.exists(file)) {
            return true;
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        }
        String value = values.getProperty("enabled");
        if (value == null || (!value.equals("true") && !value.equals("false"))) {
            warningLogger.accept("Invalid personal balance display setting; falling back to enabled.");
            return true;
        }
        return Boolean.parseBoolean(value);
    }
}
