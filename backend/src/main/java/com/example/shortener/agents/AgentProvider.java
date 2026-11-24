package com.example.shortener.agents;

/** Source of agents. The deterministic mock provider is the default; an LLM provider is optional and must follow the same contract. */
public interface AgentProvider {
    String providerName();

    /** Returns the agent for the stage id, or throws IllegalArgumentException for an unknown stage. */
    Agent forStage(String stageId);
}
