package com.example.shortener.orchestrator;

import java.util.List;

/** The built-in 8-stage greenfield SDLC workflow. */
public final class DefaultWorkflow {
    public static final WorkflowDefinition GREENFIELD = create();

    private DefaultWorkflow() {}

    private static List<String> entry() {
        return List.of(StageGates.PREREQS_SUCCEEDED, StageGates.PREREQ_ARTIFACTS_CURRENT);
    }

    private static List<String> exit() {
        return List.of(StageGates.OUTPUT_STORED, StageGates.OUTPUT_EVALUATED, StageGates.OUTPUT_POLICY);
    }

    public static WorkflowDefinition create() {
        return new WorkflowDefinition("greenfield-sdlc", List.of(
                new NodeDef("REQUIREMENTS_ANALYSIS", "Requirements analysis", List.of(), false,
                        List.of(StageGates.REQUIREMENT_PRESENT), exit()),
                new NodeDef("TASK_DECOMPOSITION", "Task decomposition", List.of("REQUIREMENTS_ANALYSIS"), false, entry(), exit()),
                new NodeDef("ARCHITECTURE_DESIGN", "Architecture design", List.of("TASK_DECOMPOSITION"), false, entry(), exit()),
                new NodeDef("IMPLEMENTATION_PLAN", "Implementation plan", List.of("TASK_DECOMPOSITION"), false, entry(), exit()),
                new NodeDef("IMPLEMENTATION", "Implementation",
                        List.of("ARCHITECTURE_DESIGN", "IMPLEMENTATION_PLAN"), true, entry(), exit()),
                new NodeDef("TESTING", "Testing", List.of("IMPLEMENTATION"), false, entry(), exit()),
                new NodeDef("DOCUMENTATION", "Documentation", List.of("ARCHITECTURE_DESIGN"), false, entry(), exit()),
                new NodeDef("RELEASE_READINESS", "Release readiness", List.of("TESTING", "DOCUMENTATION"), true, entry(), exit())));
    }
}
