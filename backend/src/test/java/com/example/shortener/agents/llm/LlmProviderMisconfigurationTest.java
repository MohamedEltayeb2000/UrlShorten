package com.example.shortener.agents.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.AgentProvider;
import com.example.shortener.orchestrator.AgentsConfig;
import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@Requirement("001-FR-010")
class LlmProviderMisconfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AgentsConfig.class)
            .withPropertyValues("shortener.evaluation.criteria-path=../eval/criteria.json");

    @Test
    void llmWithBlankEndpointFailsFast() {
        runner.withPropertyValues("shortener.agents.provider=llm", "shortener.agents.llm.endpoint=")
                .run(ctx -> {
                    assertNotNull(ctx.getStartupFailure());
                    assertTrue(rootMessages(ctx.getStartupFailure()).contains("shortener.agents.llm.endpoint"));
                });
    }

    @Test
    void defaultIsMock() {
        runner.run(ctx -> assertEquals("mock", ctx.getBean(AgentProvider.class).providerName()));
    }

    private static String rootMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (; t != null; t = t.getCause()) sb.append(t.getMessage()).append('\n');
        return sb.toString();
    }
}
