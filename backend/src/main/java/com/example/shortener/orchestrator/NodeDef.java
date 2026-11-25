package com.example.shortener.orchestrator;

import java.util.List;

/**
 * Definition of one workflow node (stage): its id, display name, the ids it depends on, whether a human approval gate
 * applies before it may complete, and the human-readable names of its entry and exit gate checks (see {@link StageGates}).
 * An empty {@code dependsOn} means the node is an entry node; empty check lists fall back to the default checks.
 */
public record NodeDef(String id, String name, List<String> dependsOn, boolean requiresApproval,
                      List<String> entryChecks, List<String> exitChecks) {
    public NodeDef {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        entryChecks = entryChecks == null ? List.of() : List.copyOf(entryChecks);
        exitChecks = exitChecks == null ? List.of() : List.copyOf(exitChecks);
    }

    public NodeDef(String id, String name, List<String> dependsOn, boolean requiresApproval) {
        this(id, name, dependsOn, requiresApproval, List.of(), List.of());
    }
}
