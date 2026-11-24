package com.example.shortener.orchestrator;

import java.util.List;

/** The built-in 8-stage greenfield SDLC workflow. */
public final class DefaultWorkflow {
    public static final WorkflowDefinition GREENFIELD = create();

    private DefaultWorkflow() {}

    public static WorkflowDefinition create() {
        return new WorkflowDefinition("greenfield-sdlc", List.of(
                new NodeDef("REQUIREMENTS_ANALYSIS", "Requirements analysis", List.of(), false),
                new NodeDef("TASK_DECOMPOSITION", "Task decomposition", List.of("REQUIREMENTS_ANALYSIS"), false),
                new NodeDef("ARCHITECTURE_DESIGN", "Architecture design", List.of("TASK_DECOMPOSITION"), false),
                new NodeDef("IMPLEMENTATION_PLAN", "Implementation plan", List.of("TASK_DECOMPOSITION"), false),
                new NodeDef("IMPLEMENTATION", "Implementation",
                        List.of("ARCHITECTURE_DESIGN", "IMPLEMENTATION_PLAN"), true),
                new NodeDef("TESTING", "Testing", List.of("IMPLEMENTATION"), false),
                new NodeDef("DOCUMENTATION", "Documentation", List.of("ARCHITECTURE_DESIGN"), false),
                new NodeDef("RELEASE_READINESS", "Release readiness", List.of("TESTING", "DOCUMENTATION"), true)));
    }
}
