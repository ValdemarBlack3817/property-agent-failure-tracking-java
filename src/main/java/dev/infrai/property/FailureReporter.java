package dev.infrai.property;

import java.util.Map;

@FunctionalInterface
public interface FailureReporter {
    void capture(String idempotencyKey, String exception, Map<String, Object> context);
}
