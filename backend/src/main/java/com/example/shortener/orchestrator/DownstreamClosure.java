package com.example.shortener.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Which stages are affected when one stage's inputs or outputs change: the stage itself plus everything downstream of it. */
public final class DownstreamClosure {
    private DownstreamClosure() {}

    /** Returns the changed node and all transitive dependents, in the definition's declaration order. */
    public static Set<String> of(WorkflowDefinition def, String changedNodeId) {
        boolean known = def.nodes().stream().anyMatch(n -> n.id().equals(changedNodeId));
        if (!known) throw new IllegalArgumentException("Unknown stage: " + changedNodeId);
        Set<String> affected = new LinkedHashSet<>();
        affected.add(changedNodeId);
        boolean grew;
        do {
            grew = false;
            for (NodeDef n : def.nodes()) {
                if (!affected.contains(n.id()) && n.dependsOn().stream().anyMatch(affected::contains)) {
                    affected.add(n.id());
                    grew = true;
                }
            }
        } while (grew);
        List<String> ordered = new ArrayList<>();
        for (NodeDef n : def.nodes()) if (affected.contains(n.id())) ordered.add(n.id());
        return new LinkedHashSet<>(ordered);
    }
}
