package com.example.shortener.orchestrator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Registry of known workflow definitions by name. Every definition is validated when registered. */
@Component
public class Workflows {
    private final Map<String, WorkflowDefinition> byName = new ConcurrentHashMap<>();

    public Workflows() {
        register(DefaultWorkflow.create());
    }

    public void register(WorkflowDefinition definition) {
        GraphValidator.validate(definition);
        byName.put(definition.name(), definition);
    }

    public WorkflowDefinition get(String name) {
        WorkflowDefinition d = byName.get(name);
        if (d == null) throw new IllegalArgumentException("Unknown workflow: " + name);
        return d;
    }

    public String defaultName() {
        return DefaultWorkflow.create().name();
    }
}
