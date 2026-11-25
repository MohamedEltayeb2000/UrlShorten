package com.example.shortener.orchestrator;

import com.example.shortener.audit.AuditService;
import com.example.shortener.common.ApiException;
import com.example.shortener.shortener.dto.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunService {
    public static final int MAX_REQUIREMENT = 4000;
    private static final Set<String> SCENARIOS = Set.of("GREENFIELD", "BROWNFIELD", "AMBIGUOUS");

    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final RunTransitions transitions;
    private final RunScheduler scheduler;
    private final Workflows workflows;
    private final AuditService audit;
    private final StageGates gates;

    public RunService(AgentRunRepository runs, RunNodeRepository nodes, RunTransitions transitions, RunScheduler scheduler,
                      Workflows workflows, AuditService audit, StageGates gates) {
        this.gates = gates;
        this.runs = runs;
        this.nodes = nodes;
        this.transitions = transitions;
        this.scheduler = scheduler;
        this.workflows = workflows;
        this.audit = audit;
    }

    public RunDtos.RunDetail create(String requirement, String scenario) {
        return create(requirement, scenario, workflows.defaultName());
    }

    /** Creates a run for a registered workflow (graph validated at registration) with every node PENDING. */
    @Transactional
    public RunDtos.RunDetail create(String requirement, String scenario, String workflowName) {
        String sc = validateScenario(scenario);
        // A scenario launched without its own requirement uses the seeded preset requirement.
        String req = (requirement == null || requirement.isBlank()) && sc != null
                ? ScenarioPresets.byId(sc).requirement()
                : validateRequirement(requirement);
        WorkflowDefinition def = workflows.get(workflowName);
        AgentRun run = runs.save(new AgentRun(def.name(), sc, req, req.replaceAll("\\s+", " ")));
        run.setStatus(RunStatus.CREATED);
        for (NodeDef d : def.nodes()) {
            RunNode n = nodes.save(new RunNode(run.getId(), d.id(), d.name(), d.dependsOn(), d.requiresApproval()));
            transitions.recordInitial(n);
        }
        audit.append(run.getId(), "HUMAN", "demo-user", "RUN_CREATED", "workflow " + def.name() + (sc == null ? "" : ", scenario " + sc), null);
        return detail(run.getId());
    }

    public PageResponse<RunDtos.RunSummary> list(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(size, 100));
        List<RunDtos.RunSummary> items = runs.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(p, s)).stream()
                .map(r -> new RunDtos.RunSummary(r.getId(), r.getScenarioType(), r.getRawRequirement(), r.getStatus().name(),
                        r.getCreatedAt()))
                .toList();
        long total = runs.count();
        return new PageResponse<>(items, p, s, total, (int) Math.ceil(total / (double) s));
    }

    public RunDtos.RunDetail detail(long runId) {
        AgentRun r = runs.findById(runId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Run not found"));
        WorkflowDefinition def = workflows.get(r.getWorkflowName());
        Map<String, Integer> levels = GraphValidator.levels(def);
        Map<String, StageGates.Gates> gateViews = gates.views(runId, def);
        List<RunDtos.NodeView> views = nodes.findByRunIdOrderById(runId).stream()
                .map(n -> new RunDtos.NodeView(n.getNodeId(), n.getName(), n.getStatus().name(), n.dependsOnIds(),
                        levels.getOrDefault(n.getNodeId(), 0), n.getAttemptCount(), n.getStartedAt(), n.getCompletedAt(),
                        n.getErrorSummary(), gateViews.get(n.getNodeId())))
                .toList();
        return new RunDtos.RunDetail(r.getId(), r.getScenarioType(), r.getRawRequirement(), r.getStatus().name(),
                r.getCreatedAt(), r.getPlanVersion(), r.getStartedAt(), r.getCompletedAt(), views);
    }

    public RunDtos.RunDetail start(long runId) {
        scheduler.start(runId);
        return detail(runId);
    }

    public RunDtos.RunDetail pause(long runId) {
        scheduler.pause(runId);
        return detail(runId);
    }

    static String validateRequirement(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUIREMENT", "requirement is required");
        }
        String r = requirement.strip();
        if (r.length() > MAX_REQUIREMENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUIREMENT", "requirement is too long (max " + MAX_REQUIREMENT + ")");
        }
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r') {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUIREMENT", "requirement contains control characters");
            }
        }
        return r;
    }

    static String validateScenario(String scenario) {
        if (scenario == null || scenario.isBlank()) return null;
        String s = scenario.strip().toUpperCase(Locale.ROOT);
        if (!SCENARIOS.contains(s)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCENARIO", "scenario must be GREENFIELD, BROWNFIELD or AMBIGUOUS");
        }
        return s;
    }
}
