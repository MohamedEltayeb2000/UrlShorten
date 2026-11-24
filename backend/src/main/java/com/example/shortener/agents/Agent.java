package com.example.shortener.agents;

/** One SDLC agent. Implementations must be side-effect free (no I/O beyond reading fixtures, no code execution). */
public interface Agent {
    /** Human-readable agent name, e.g. "RequirementsAnalystAgent". */
    String name();

    /** The workflow stage id this agent serves, e.g. "REQUIREMENTS_ANALYSIS". */
    String stageId();

    AgentResult run(AgentInput input);
}
