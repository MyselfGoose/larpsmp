package com.larpsmp.moneyevent.email;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resend REST API client ({@code POST /emails}) using JDK {@link HttpClient}.
 */
public final class ResendClient implements EmailSender {

    private static final URI ENDPOINT = URI.create("https://api.resend.com/emails");

    private final String apiKey;
    private final String fromEmail;
    private final HttpClient httpClient;
    private final Logger logger;

    public ResendClient(String apiKey, String fromEmail, Logger logger) {
        this(
                apiKey,
                fromEmail,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                logger
        );
    }

    ResendClient(String apiKey, String fromEmail, HttpClient httpClient, Logger logger) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.fromEmail = Objects.requireNonNull(fromEmail, "fromEmail");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.logger = Objects.requireNonNull(logger, "logger");
        if (apiKey.isBlank() || fromEmail.isBlank()) {
            throw new IllegalArgumentException("Resend API key and from email are required");
        }
    }

    @Override
    public void send(EmailMessage message) throws EmailSendException {
        Objects.requireNonNull(message, "message");
        String payload = buildJsonPayload(message);
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                logger.info("Sent auth email to " + EmailTemplates.maskEmail(message.to())
                        + " (" + message.subject() + ")");
                return;
            }
            String body = response.body() == null ? "" : response.body().strip();
            if (body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }
            logger.log(Level.WARNING, "Resend API rejected email send with HTTP " + status
                    + " to " + EmailTemplates.maskEmail(message.to())
                    + (body.isBlank() ? "" : ": " + body));
            throw new EmailSendException("Email provider rejected the request (HTTP " + status + ")");
        } catch (EmailSendException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new EmailSendException("Failed to reach email provider", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Email send interrupted", exception);
        }
    }

    private String buildJsonPayload(EmailMessage message) {
        return "{"
                + "\"from\":" + jsonString(fromEmail) + ","
                + "\"to\":[" + jsonString(message.to()) + "],"
                + "\"subject\":" + jsonString(message.subject()) + ","
                + "\"html\":" + jsonString(message.htmlBody()) + ","
                + "\"text\":" + jsonString(message.textBody())
                + "}";
    }

    private static String jsonString(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }
}
