package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Summarizes whether the change looks ready for a human release decision. */
public class ReleaseReadinessAgent extends BaseMockAgent {

    public ReleaseReadinessAgent() {
        super("ReleaseReadinessAgent", "RELEASE_READINESS");
    }

    @Override
    protected AgentResult build(AgentInput in) {
        boolean ambiguous = isScenario(in, AMBIGUOUS);
        List<Object> blockers = new ArrayList<>();
        if (ambiguous) {
            blockers.add("Open clarification questions have not been confirmed by a human");
        }
        String testStatus = upstreamList(in, "TESTING", "testPlan").isEmpty() ? "PENDING" : "PASS";
        List<Object> checklist = List.of(
                map("item", "Requirements confirmed", "status", ambiguous ? "PENDING" : "PASS"),
                map("item", "Tests planned", "status", testStatus),
                map("item", "Documentation drafted", "status", "PASS"),
                map("item", "Human approval recorded before any release", "status", "PENDING"));
        Map<String, Object> out = map("checklist", checklist, "ready", blockers.isEmpty(), "blockers", blockers);
        return ok(in, blockers.isEmpty() ? "The change looks ready for a human release decision"
                        : "The change is not ready: " + blockers.size() + " blocker(s)", out,
                List.of("Release decisions are made by a human, never by an agent"),
                List.of("MEDIUM: simulated checks are not a substitute for a real review"),
                List.of("Hold a human release review"));
    }
}
