package com.example.shortener.governance;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The policy engine sees stored artifact JSON, so quoted keys and values must be caught as well as key = value prose. */
@Requirement("001-FR-013")
class PolicyEngineJsonSecretTest {
    private final PolicyEngine policy = new PolicyEngine();

    private boolean denied(String text) {
        List<PolicyDecision> ds = policy.evaluateText(text);
        return ds.stream().anyMatch(d -> d.outcome() == PolicyOutcome.DENY);
    }

    @Test
    void quotedJsonSecretsAreDenied() {
        assertTrue(denied("{\"password\":\"hunter2hunter2\"}"));
        assertTrue(denied("{\"nested\":{\"api_key\": \"abcd1234efgh5678\"}}"));
        assertTrue(denied("db.token = 'sup3rs3cr3tvalue'"));
    }

    @Test
    void ordinaryTextAboutSecretsIsAllowed() {
        assertFalse(denied("{\"note\":\"passwords are never logged\",\"task\":\"add token bucket rate limiting\"}"));
    }

    @Test
    void reasonNeverEchoesTheSecret() {
        for (PolicyDecision d : policy.evaluateText("{\"password\":\"hunter2hunter2\"}")) {
            assertFalse(d.reason().contains("hunter2hunter2"));
        }
    }
}
