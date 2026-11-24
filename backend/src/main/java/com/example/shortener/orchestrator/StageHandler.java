package com.example.shortener.orchestrator;

/**
 * Executes one stage of a run. Implementations must be side-effect free with respect to the host: they never run shell
 * commands, apply patches or deploy (generated output is only ever a proposal). Throwing marks the stage FAILED.
 */
public interface StageHandler {
    void execute(StageExecution execution) throws Exception;
}
