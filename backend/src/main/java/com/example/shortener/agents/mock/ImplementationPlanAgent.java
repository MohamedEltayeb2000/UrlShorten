package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns the architecture into ordered implementation steps and a list of files to change. */
public class ImplementationPlanAgent extends BaseMockAgent {

    public ImplementationPlanAgent() {
        super("ImplementationPlanAgent", "IMPLEMENTATION_PLAN");
    }

    @Override
    protected AgentResult build(AgentInput in) {
        boolean links = isLinks(in);
        List<Object> steps = new ArrayList<>();
        int n = 1;
        for (Object c : upstreamList(in, "ARCHITECTURE_DESIGN", "components")) {
            if (label(c, "name").startsWith("Work item")) continue;
            steps.add(map("order", n++, "description", "Implement " + label(c, "name") + ": " + label(c, "responsibility")));
        }
        if (steps.isEmpty()) {
            steps.add(map("order", n++, "description", "Implement the core behavior for: " + topic(in)));
        }
        steps.add(map("order", n++, "description", "Add unit and integration tests"));
        steps.add(map("order", n, "description", "Update documentation"));
        List<Object> files = new ArrayList<>(links
                ? List.of("LinkController", "LinkRepository", "LinkService", "schema.sql")
                : List.of("DomainService", "Storage", "ApiLayer"));
        if (isScenario(in, BROWNFIELD)) {
            files.clear();
            for (Object f : upstreamList(in, "ARCHITECTURE_DESIGN", "impactedFiles")) files.add(f);
            if (files.isEmpty()) files.addAll(List.of("LinkController", "LinkRepository", "LinkService", "schema.sql"));
        }
        Map<String, Object> out = map("steps", steps, "filesToChange", files);
        return ok(in, "Planned " + steps.size() + " implementation steps", out,
                List.of("Steps are executed in the listed order"),
                List.of("LOW: step order may need adjusting once code review starts"),
                List.of("Review the plan", "Proceed to the implementation proposal"));
    }
}
