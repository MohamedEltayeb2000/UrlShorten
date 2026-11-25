package com.example.shortener.orchestrator;

import com.example.shortener.agents.AgentProvider;
import com.example.shortener.agents.llm.LlmAgentProvider;
import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.evaluation.AgentOutputEvaluator;
import com.example.shortener.evaluation.EvaluationCriteria;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the deterministic mock provider (the default; no API key needed) and the evaluator with the versioned criteria. */
@Configuration
public class AgentsConfig {

    @Bean
    AgentProvider agentProvider(
            @Value("${shortener.agents.provider:mock}") String provider,
            @Value("${shortener.agents.llm.endpoint:}") String endpoint,
            @Value("${shortener.agents.llm.api-key:}") String apiKey,
            @Value("${shortener.agents.llm.model:default}") String model,
            @Value("${shortener.agents.llm.timeout-ms:10000}") long timeoutMs) {
        if ("mock".equals(provider)) {
            return new MockAgentProvider();
        }
        if ("llm".equals(provider)) {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException(
                        "shortener.agents.provider=llm requires shortener.agents.llm.endpoint (env LLM_ENDPOINT) to be set");
            }
            return new LlmAgentProvider(endpoint, apiKey, model, timeoutMs);
        }
        throw new IllegalStateException("Unknown agent provider '" + provider + "'; use 'mock' or 'llm'");
    }

    @Bean
    EvaluationCriteria evaluationCriteria(@Value("${shortener.evaluation.criteria-path:}") String configured) {
        return EvaluationCriteria.load(locate(configured));
    }

    @Bean
    AgentOutputEvaluator agentOutputEvaluator(EvaluationCriteria criteria) {
        return new AgentOutputEvaluator(criteria);
    }

    /** Finds eval/criteria.json: the configured path, else by walking up from the working directory. */
    static Path locate(String configured) {
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        Path p = Path.of("").toAbsolutePath();
        while (p != null) {
            Path candidate = p.resolve("eval/criteria.json");
            if (Files.exists(candidate)) return candidate;
            p = p.getParent();
        }
        throw new IllegalStateException("eval/criteria.json not found; set shortener.evaluation.criteria-path");
    }
}
