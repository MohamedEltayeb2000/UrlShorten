package com.example.shortener.agents.mock;

import com.example.shortener.agents.Agent;
import com.example.shortener.agents.AgentProvider;
import java.util.List;

/** Deterministic, offline agent provider. Works with no AI service configured. */
public class MockAgentProvider implements AgentProvider {

    private final List<Agent> agents;

    public MockAgentProvider() {
        this(BrownfieldAnalyzer.fromConfiguredPath());
    }

    public MockAgentProvider(BrownfieldAnalyzer analyzer) {
        this.agents = List.of(
                new RequirementsAnalystAgent(analyzer),
                new PlannerAgent(),
                new ArchitectAgent(analyzer),
                new ImplementationPlanAgent(),
                new ImplementerAgent(analyzer),
                new TestAgent(),
                new DocumentationAgent(),
                new ReleaseReadinessAgent());
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public Agent forStage(String stageId) {
        for (Agent a : agents) {
            if (a.stageId().equals(stageId)) {
                return a;
            }
        }
        throw new IllegalArgumentException("Unknown stage: " + stageId);
    }
}
