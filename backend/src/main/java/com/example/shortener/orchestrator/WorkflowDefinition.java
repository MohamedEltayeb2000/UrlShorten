package com.example.shortener.orchestrator;

import java.util.List;

/** An explicit dependency graph of nodes. Validate with {@code GraphValidator} before use. */
public record WorkflowDefinition(String name, List<NodeDef> nodes) {
    public WorkflowDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
