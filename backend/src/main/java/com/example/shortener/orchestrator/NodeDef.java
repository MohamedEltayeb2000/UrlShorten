package com.example.shortener.orchestrator;

import java.util.List;

/**
 * Definition of one workflow node (stage): its id, display name, the ids it depends on, and whether a human
 * approval gate applies before it may complete. An empty {@code dependsOn} means the node is an entry node.
 */
public record NodeDef(String id, String name, List<String> dependsOn, boolean requiresApproval) {
    public NodeDef {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
