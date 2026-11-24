package com.example.shortener.agents;

import com.example.shortener.agents.mock.MockAgentProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs the eight stages in order, feeding each stage the structured outputs of the earlier ones. */
final class AgentTestSupport {

    static final List<String> STAGES = List.of(
            "REQUIREMENTS_ANALYSIS", "TASK_DECOMPOSITION", "ARCHITECTURE_DESIGN", "IMPLEMENTATION_PLAN",
            "IMPLEMENTATION", "TESTING", "DOCUMENTATION", "RELEASE_READINESS");

    static final String LINKS_REQUIREMENT = "Build a URL shortener that creates short links and redirects to the original URL";

    private AgentTestSupport() {}

    static AgentInput input(String stage, String scenario, String raw, Map<String, Map<String, Object>> upstream) {
        return new AgentInput(stage, scenario, raw, raw, upstream, List.of());
    }

    /** Full pipeline results keyed by stage id, in stage order. */
    static Map<String, AgentResult> pipeline(String scenario, String raw) {
        MockAgentProvider provider = new MockAgentProvider();
        Map<String, AgentResult> results = new LinkedHashMap<>();
        Map<String, Map<String, Object>> upstream = new LinkedHashMap<>();
        for (String stage : STAGES) {
            AgentResult r = provider.forStage(stage).run(input(stage, scenario, raw, upstream));
            results.put(stage, r);
            upstream.put(stage, r.structuredOutput());
        }
        return results;
    }
}
