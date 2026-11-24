package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Proposes components, decisions and data flow based on the planned tasks. */
public class ArchitectAgent extends BaseMockAgent {

    private final BrownfieldAnalyzer analyzer;

    public ArchitectAgent() {
        this(new BrownfieldAnalyzer());
    }

    public ArchitectAgent(BrownfieldAnalyzer analyzer) {
        super("ArchitectAgent", "ARCHITECTURE_DESIGN");
        this.analyzer = analyzer;
    }

    @Override
    protected AgentResult build(AgentInput in) {
        boolean links = isLinks(in);
        List<Object> components = new ArrayList<>();
        if (links) {
            components.add(map("name", "LinkController", "responsibility", "HTTP API for creating and resolving links"));
            components.add(map("name", "LinkService", "responsibility", "Validates URLs and generates short codes"));
            components.add(map("name", "LinkRepository", "responsibility", "Persists links"));
        } else {
            components.add(map("name", "ApiLayer", "responsibility", "Accepts and validates requests"));
            components.add(map("name", "DomainService", "responsibility", "Implements the core behavior"));
            components.add(map("name", "Storage", "responsibility", "Persists state"));
        }
        List<Object> tasks = upstreamList(in, "TASK_DECOMPOSITION", "tasks");
        for (Object t : tasks) {
            components.add(map("name", "Work item " + label(t, "id"), "responsibility", label(t, "title")));
        }
        List<Object> decisions = new ArrayList<>(List.of(
                "Keep a layered design: API, service, storage",
                "Validate all input at the API boundary",
                "Prefer small, reversible changes behind existing interfaces"));
        List<Object> dataFlow = new ArrayList<>(links
                ? List.of("Client -> POST /links -> LinkService -> LinkRepository -> database",
                        "Client -> GET /{code} -> LinkService -> redirect response")
                : List.of("Client -> API -> DomainService -> Storage"));
        Map<String, Object> out = map("components", components, "decisions", decisions, "dataFlow", dataFlow);
        List<String> risks = new ArrayList<>(List.of("MEDIUM: a single database may become the bottleneck at high volume"));
        if (isScenario(in, BROWNFIELD)) {
            BrownfieldAnalyzer.Analysis a = analyzer.analyze();
            dataFlow.clear();
            dataFlow.addAll(a.dataFlow());
            decisions.add("Add idempotency to link creation using an Idempotency-Key header and a unique key");
            out.put("impactedFiles", a.impactedFiles());
            out.put("dataFlow", dataFlow);
            out.put("compatibilityRisks", a.compatibilityRisks());
            risks.addAll(a.compatibilityRisks());
        }
        return ok(in, "Designed " + components.size() + " components for: " + topic(in), out,
                List.of("The existing technology stack stays unchanged"), risks,
                List.of("Review the design", "Proceed to the implementation plan"));
    }
}
