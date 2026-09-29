package dev.infrai.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PropertyAgentLoopServiceTest {
    record Capture(String key, String exception, Map<String, Object> context) {}

    public static void main(String[] args) {
        List<Capture> captures = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        FailureReporter reporter = (key, exception, context) -> captures.add(new Capture(key, exception, context));
        PropertyAgentLoopService service = new PropertyAgentLoopService(
            reporter,
            request -> calls.add("maintenance"),
            document -> { calls.add("document"); throw new IllegalStateException("Document text was empty"); },
            reminder -> calls.add("reminder"));

        var result = service.run(new PropertyAgentLoopService.AgentWork(
            new PropertyAgentLoopService.MaintenanceRequest("maint-9", "unit-8", "No heat"),
            new PropertyAgentLoopService.TenantDocument("doc-2", "tenant-7", "lease"),
            new PropertyAgentLoopService.InspectionReminder("inspection-4", "unit-8", "2026-10-10")));

        check(result.status().equals("FAILED"), "loop should report FAILED");
        check(result.completedStages().equals(List.of("maintenance-request")), "only maintenance should complete");
        check(calls.equals(List.of("maintenance", "document")), "reminder must wait after document failure");
        check(captures.size() == 1, "one failure should be captured");
        check(captures.get(0).key().equals("property-agent/maint-9/tenant-document"), "idempotency key must be stable");
        check(captures.get(0).context().get("stage").equals("tenant-document"), "capture should name the failed stage");
        System.out.println("PASS: document failure is captured and later work is stopped");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
