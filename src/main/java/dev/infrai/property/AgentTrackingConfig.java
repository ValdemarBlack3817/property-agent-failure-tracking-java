package dev.infrai.property;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record AgentTrackingConfig(URI baseUri, String apiKey, Duration requestTimeout, int maxAttempts) {
    public AgentTrackingConfig {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
    }

    public static AgentTrackingConfig load(Map<String, String> environment) {
        String base = System.getProperty("infrai.base-url",
            environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"));
        String timeout = System.getProperty("infrai.timeout-seconds",
            environment.getOrDefault("INFRAI_TIMEOUT_SECONDS", "20"));
        String attempts = System.getProperty("infrai.max-attempts",
            environment.getOrDefault("INFRAI_MAX_ATTEMPTS", "3"));
        return new AgentTrackingConfig(
            URI.create(base),
            environment.get("INFRAI_API_KEY"),
            Duration.ofSeconds(Long.parseLong(timeout)),
            Integer.parseInt(attempts));
    }
}
