package dev.infrai.property;

public final class PropertyAgentExample {
    private PropertyAgentExample() {}

    public static void main(String[] args) {
        AgentTrackingConfig config = AgentTrackingConfig.load(System.getenv());
        PropertyAgentLoopService service = new PropertyAgentLoopService(
            new InfraiErrorsClient(config),
            request -> System.out.println("Maintenance request classified: " + request.requestId()),
            document -> System.out.println("Tenant document checked: " + document.documentId()),
            reminder -> System.out.println("Inspection reminder prepared: " + reminder.inspectionId()));

        PropertyAgentLoopService.AgentWork work = new PropertyAgentLoopService.AgentWork(
            new PropertyAgentLoopService.MaintenanceRequest("maint-2048", "unit-3B", "Kitchen sink is leaking"),
            new PropertyAgentLoopService.TenantDocument("doc-771", "tenant-42", "entry-consent"),
            new PropertyAgentLoopService.InspectionReminder("inspect-91", "unit-3B", "2026-10-03"));

        System.out.println(service.run(work));
    }
}
