package dev.infrai.property;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiErrorsClient implements FailureReporter {
    private final AgentTrackingConfig config;
    private final HttpClient http;

    public InfraiErrorsClient(AgentTrackingConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiErrorsClient(AgentTrackingConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    /** Canonical capability: infrai.errors.capture */
    @Override
    public void capture(String idempotencyKey, String exception, Map<String, Object> context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "Property agent step failed");
        payload.put("level", "error");
        payload.put("fingerprint", List.of("property-agent", String.valueOf(context.get("stage"))));
        payload.put("exception", exception);
        payload.put("context", context);
        postCapture(idempotencyKey, payload);
    }

    private void postCapture(String idempotencyKey, Map<String, Object> payload) {
        URI endpoint = config.baseUri().resolve("/v1/errors/capture");
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(config.requestTimeout())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .method("POST", HttpRequest.BodyPublishers.ofString(Json.write(payload)))
                .build();
            HttpResponse<String> response = send(request);
            Map<String, Object> envelope;
            try {
                envelope = Json.parseObject(response.body());
            } catch (RuntimeException malformed) {
                throw new InfraiTransportException("Infrai returned a non-envelope response", malformed);
            }

            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Object error = envelope.get("error");
                throw new InfraiRequestException(response.statusCode(), error);
            }
            if (response.statusCode() >= 500) {
                throw new InfraiTransportException("Infrai transport response " + response.statusCode());
            }
            return;
        }
        throw new InfraiTransportException("Infrai request exhausted retry attempts");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new InfraiTransportException("Could not complete Infrai request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiTransportException("Infrai request interrupted", e);
        }
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("").trim();
        try {
            if (!value.isEmpty()) return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            try {
                Duration delay = Duration.between(ZonedDateTime.now(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
                if (!delay.isNegative()) return delay;
            } catch (RuntimeException ignoredDate) {
                // Fall through to bounded exponential delay.
            }
        }
        return Duration.ofMillis(Math.min(4_000L, 250L * (1L << (attempt - 1))));
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiTransportException("Retry interrupted", e);
        }
    }

    public static final class InfraiRequestException extends RuntimeException {
        private final int statusCode;
        private final Object error;

        InfraiRequestException(int statusCode, Object error) {
            super("Infrai rejected the capture request: " + error);
            this.statusCode = statusCode;
            this.error = error;
        }

        public int statusCode() { return statusCode; }
        public Object error() { return error; }
    }

    public static final class InfraiTransportException extends RuntimeException {
        InfraiTransportException(String message) { super(message); }
        InfraiTransportException(String message, Throwable cause) { super(message, cause); }
    }
}
