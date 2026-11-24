package com.example.shortener.orchestrator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Optional handler (property shortener.orchestrator.handler=noop): waits a configurable time so parallelism is visible, then succeeds. */
@Component
@ConditionalOnProperty(name = "shortener.orchestrator.handler", havingValue = "noop")
public class NoOpStageHandler implements StageHandler {
    private final long delayMs;

    public NoOpStageHandler(@Value("${shortener.orchestrator.stage-delay-ms:250}") long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    @Override
    public void execute(StageExecution execution) throws InterruptedException {
        if (delayMs > 0) Thread.sleep(delayMs);
    }
}
