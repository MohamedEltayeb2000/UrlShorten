package com.example.shortener.agents.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentProvider;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import com.example.shortener.evaluation.AgentOutputEvaluator;
import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"shortener.agents.provider=llm", "shortener.agents.llm.endpoint=http://localhost:1/none"})
@ActiveProfiles("test")
@Requirement("001-FR-010")
class LlmProviderWiringTest {

    @Autowired AgentProvider provider;
    @Autowired AgentOutputEvaluator evaluator;

    @Test
    void llmProviderIsWired() {
        assertInstanceOf(LlmAgentProvider.class, provider);
        assertEquals("llm", provider.providerName());
        assertFalse(provider.deterministic());
    }

    @Test
    void secondRunEqualToResultPassesDeterministicCriterion() {
        AgentInput input = new AgentInput("TASK_DECOMPOSITION", null, "r", "r", Map.of(), List.of());
        AgentResult result = new AgentResult("LlmTaskDecompositionAgent", AgentStatus.SUCCESS, "s",
                Map.of("tasks", List.of("t")), List.of(), List.of(), List.of(), List.of(), 0.5, "n", List.of(), false);
        var evaluation = evaluator.evaluate(input, result, result);
        assertFalse(evaluation.failedCriteria().contains("deterministic"), evaluation.failedCriteria().toString());
    }
}
