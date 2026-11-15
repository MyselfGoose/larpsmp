package com.larpsmp.moneyevent.email;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test double that records outbound messages and extracts verification codes from plaintext bodies.
 */
public final class CapturingEmailSender implements EmailSender {

    private static final Pattern CODE_PATTERN = Pattern.compile("Your code:\\s*(\\d+)");

    private final List<EmailMessage> messages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(EmailMessage message) {
        messages.add(message);
    }

    public List<EmailMessage> messages() {
        return List.copyOf(messages);
    }

    public Optional<String> lastCode() {
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        EmailMessage last = messages.get(messages.size() - 1);
        Matcher matcher = CODE_PATTERN.matcher(last.textBody());
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    public void clear() {
        messages.clear();
    }
}
