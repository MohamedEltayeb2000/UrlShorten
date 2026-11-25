package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Splits the goals into ordered tasks with dependencies; constraints become acceptance criteria. */
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
        List<Object> constraints = upstreamList(in, "REQUIREMENTS_ANALYSIS", "constraints");
        List<Object> tasks = new ArrayList<>();
        List<Object> all = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) {
            String id = "T" + (i + 1);
            List<Object> deps = i == 0 ? List.of() : List.of("T1");
            tasks.add(map("id", id, "title", "Implement: " + goals.get(i), "dependsOn", deps,
                    "acceptanceCriteria", criteriaFor(String.valueOf(goals.get(i)), constraints)));
            all.add(id);
        }
        tasks.add(map("id", "T" + (goals.size() + 1), "title", "Verify behavior and update documentation",
                "dependsOn", all, "acceptanceCriteria", new ArrayList<>(constraints)));
        Map<String, Object> out = map("tasks", tasks);
        return ok(in, "Decomposed the work into " + tasks.size() + " tasks derived from the goals", out,
                List.of("Each goal maps to one task of similar size"),
                List.of("MEDIUM: tasks sharing T1 cannot start before it is finished"),
                List.of("Review the task order", "Proceed to architecture design"));
    }

    /** Constraints that share a word with the goal become that task's acceptance criteria. */
    private static List<Object> criteriaFor(String goal, List<Object> constraints) {
        Set<String> goalWords = significant(goal);
        List<Object> out = new ArrayList<>();
        for (Object c : constraints) {
            Set<String> shared = significant(String.valueOf(c));
            shared.retainAll(goalWords);
            if (!shared.isEmpty()) out.add("Satisfy: " + c);
        }
        return out;
    }

    private static Set<String> significant(String text) {
        Set<String> out = new TreeSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 3) out.add(w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
        }
        return out;
    }
}
