package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Produces a test plan and simulated expected results; nothing is executed. */
public class TestAgent extends BaseMockAgent {

    public TestAgent() {
        super("TestAgent", "TESTING");
    }

    @Override
    protected AgentResult build(AgentInput in) {
        List<Object> plan = new ArrayList<>();
        if (isLinks(in)) {
            plan.add("Creating a link with a valid https URL returns a short code");
            plan.add("Creating a link with an unsafe scheme is rejected");
            plan.add("Resolving a known code redirects to the target URL");
            plan.add("Resolving an unknown code returns not found");
        } else {
            plan.add("Valid input produces the expected result");
            plan.add("Invalid input is rejected with a clear error");
        }
        if (isScenario(in, BROWNFIELD)) {
            plan.add("Retrying POST /links with the same Idempotency-Key returns the original link");
            plan.add("A different fingerprint under the same key is rejected");
        }
        for (Object t : upstreamList(in, "TASK_DECOMPOSITION", "tasks")) {
            plan.add("Covers " + label(t, "id") + ": " + label(t, "title"));
        }
        Map<String, Object> out = map("testPlan", plan, "expectedResults", map("passed", 12, "failed", 0));
        return ok(in, "Planned " + plan.size() + " test cases; results are simulated and nothing was executed", out,
                List.of("Expected results are estimates, not measured"),
                List.of("MEDIUM: simulated results do not replace a real test run"),
                List.of("Run the real test suite", "Proceed to documentation"));
    }
}
