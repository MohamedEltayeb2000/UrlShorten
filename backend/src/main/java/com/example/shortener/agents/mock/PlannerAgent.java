package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Splits the goals into ordered tasks with dependencies. */
public class PlannerAgent extends BaseMockAgent {

    public PlannerAgent() {
        super("PlannerAgent", "TASK_DECOMPOSITION");
    }

    @Override
    protected AgentResult build(AgentInput in) {
        List<Object> goals = upstreamList(in, "REQUIREMENTS_ANALYSIS", "goals");
        if (goals.isEmpty()) {
            goals = List.of("Deliver the core capability: " + topic(in), "Validate inputs and report clear errors");
        }
        List<Object> tasks = new ArrayList<>();
        List<Object> all = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) {
            String id = "T" + (i + 1);
            List<Object> deps = i == 0 ? List.of() : List.of("T1");
            tasks.add(map("id", id, "title", "Implement: " + goals.get(i), "dependsOn", deps));
            all.add(id);
        }
        tasks.add(map("id", "T" + (goals.size() + 1), "title", "Verify behavior and update documentation",
                "dependsOn", all));
        Map<String, Object> out = map("tasks", tasks);
        return ok(in, "Decomposed the work into " + tasks.size() + " tasks derived from the goals", out,
                List.of("Each goal maps to one task of similar size"),
                List.of("MEDIUM: tasks sharing T1 cannot start before it is finished"),
                List.of("Review the task order", "Proceed to architecture design"));
    }
}
