package com.example.shortener.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.*;
import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Secrets must be caught in prose AND inside the JSON-serialised structured output (quoted keys and values). */
@Tag("eval")
@Requirement("002-FR-010")
class SecretInStructuredOutputTest {
    private final AgentOutputEvaluator evaluator =
            new AgentOutputEvaluator(EvaluationCriteria.load(RepoFiles.file("eval/criteria.json")));

    private OutputEvaluation evaluate(Map<String, Object> structured) {
        AgentInput in = new AgentInput("TASK_DECOMPOSITION", "GREENFIELD", "Build links", "Build links", Map.of(), List.of());
        AgentResult r = new AgentResult("PlannerAgent", AgentStatus.SUCCESS, "[SIMULATED] plan", structured, List.of(), List.of(),
                List.of(), List.of(), 0.9, "", List.of(), true);
        return evaluator.evaluate(in, r, r);
    }

    @Test
    void quotedJsonKeyWithSecretValueIsFlagged() {
        OutputEvaluation e = evaluate(Map.of("tasks", List.of(Map.of("id", "t1", "title", "x")), "password", "hunter2hunter2"));
        assertTrue(e.failedCriteria().contains("no_unsafe_content"), "failed: " + e.failedCriteria());
    }

    @Test
    void nestedApiKeyInStructuredOutputIsFlagged() {
        OutputEvaluation e = evaluate(Map.of("tasks", List.of(Map.of("id", "t1", "title", "x", "api_key", "abcd1234efgh5678"))));
        assertTrue(e.failedCriteria().contains("no_unsafe_content"));
    }

    @Test
    void innocentWordsAreNotFlagged() {
        OutputEvaluation e = evaluate(Map.of("tasks", List.of(Map.of("id", "t1", "title", "Add token bucket rate limiting")),
                "note", "passwords are never logged"));
        assertFalse(e.failedCriteria().contains("no_unsafe_content"), "false positive: " + e.failedCriteria());
    }
}
