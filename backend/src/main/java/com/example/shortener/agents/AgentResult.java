package com.example.shortener.agents;

import java.util.List;
import java.util.Map;

/**
 * Structured result every agent must return (never free text only).
 * {@code simulated} is true for mock and fallback output and must be surfaced to users.
 * {@code referencesToInputs} lists upstream artifact ids/versions such as "ARCHITECTURE_DESIGN:v1".
 */
public record AgentResult(
        String agentName,
        AgentStatus status,
        String summary,
        Map<String, Object> structuredOutput,
        List<String> assumptions,
        List<String> risks,
        List<String> openQuestions,
        List<String> recommendedNextActions,
        double confidence,
        String confidenceNote,
        List<String> referencesToInputs,
        boolean simulated) {

    public AgentResult {
        structuredOutput = structuredOutput == null ? Map.of() : Map.copyOf(structuredOutput);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        risks = risks == null ? List.of() : List.copyOf(risks);
        openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
        recommendedNextActions = recommendedNextActions == null ? List.of() : List.copyOf(recommendedNextActions);
        referencesToInputs = referencesToInputs == null ? List.of() : List.copyOf(referencesToInputs);
    }
}
