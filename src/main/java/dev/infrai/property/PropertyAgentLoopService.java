package dev.infrai.property;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PropertyAgentLoopService {
    public record MaintenanceRequest(String requestId, String unitId, String summary) {}
    public record TenantDocument(String documentId, String tenantId, String kind) {}
    public record InspectionReminder(String inspectionId, String unitId, String dueDate) {}
    public record AgentWork(MaintenanceRequest maintenance, TenantDocument document,
                            InspectionReminder reminder) {}
    public record LoopResult(String requestId, String status, List<String> completedStages) {}

    @FunctionalInterface
    public interface AgentStep<T> { void process(T input) throws Exception; }

    private final FailureReporter reporter;
    private final AgentStep<MaintenanceRequest> maintenanceStep;
    private final AgentStep<TenantDocument> documentStep;
    private final AgentStep<InspectionReminder> reminderStep;

    public PropertyAgentLoopService(FailureReporter reporter,
                                    AgentStep<MaintenanceRequest> maintenanceStep,
                                    AgentStep<TenantDocument> documentStep,
                                    AgentStep<InspectionReminder> reminderStep) {
        this.reporter = reporter;
        this.maintenanceStep = maintenanceStep;
        this.documentStep = documentStep;
        this.reminderStep = reminderStep;
    }

    public LoopResult run(AgentWork work) {
        List<String> completed = new java.util.ArrayList<>();
        if (!runStage(work.maintenance().requestId(), "maintenance-request", work.maintenance(), maintenanceStep, completed))
            return new LoopResult(work.maintenance().requestId(), "FAILED", List.copyOf(completed));
        if (!runStage(work.maintenance().requestId(), "tenant-document", work.document(), documentStep, completed))
            return new LoopResult(work.maintenance().requestId(), "FAILED", List.copyOf(completed));
        if (!runStage(work.maintenance().requestId(), "inspection-reminder", work.reminder(), reminderStep, completed))
            return new LoopResult(work.maintenance().requestId(), "FAILED", List.copyOf(completed));
        return new LoopResult(work.maintenance().requestId(), "COMPLETED", List.copyOf(completed));
    }

    private <T> boolean runStage(String requestId, String stage, T input, AgentStep<T> step,
                                 List<String> completed) {
        try {
            step.process(input);
            completed.add(stage);
            return true;
        } catch (Exception failure) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("workflow", "property-agent-loop");
            context.put("request_id", requestId);
            context.put("stage", stage);
            context.put("input_type", input.getClass().getSimpleName());
            reporter.capture("property-agent/" + requestId + "/" + stage, stackTrace(failure), context);
            return false;
        }
    }

    private static String stackTrace(Exception failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        return text.toString();
    }
}
