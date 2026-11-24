package com.example.shortener.agents;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-008")
@Requirement("001-FR-009")
class MockAgentProviderTest {

    private final MockAgentProvider provider = new MockAgentProvider();

    @Test
    void providerNameIsMock() {
        assertEquals("mock", provider.providerName());
    }

    @Test
    void allEightStagesResolveToTheirAgent() {
        for (String stage : AgentTestSupport.STAGES) {
            Agent agent = provider.forStage(stage);
            assertEquals(stage, agent.stageId());
            assertFalse(agent.name().isBlank());
        }
    }

    @Test
    void unknownStageThrows() {
        assertThrows(IllegalArgumentException.class, () -> provider.forStage("NOT_A_STAGE"));
    }
}
