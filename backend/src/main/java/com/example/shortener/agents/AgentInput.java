package com.example.shortener.agents;

import java.util.List;
import java.util.Map;

/**
 * Scoped context handed to one agent: only what the stage needs, not the whole run.
 * {@code scenario} is GREENFIELD, BROWNFIELD, AMBIGUOUS or null. {@code upstreamOutputs} maps an upstream stage id
 * to that stage's structuredOutput.
 */
public record AgentInput(
        String stageId,
        String scenario,
        String rawRequirement,
        String normalizedRequirement,
        Map<String, Map<String, Object>> upstreamOutputs,
        List<String> policyConstraints) {

    public AgentInput {
        upstreamOutputs = upstreamOutputs == null ? Map.of() : Map.copyOf(upstreamOutputs);
        policyConstraints = policyConstraints == null ? List.of() : List.copyOf(policyConstraints);
    }
}
