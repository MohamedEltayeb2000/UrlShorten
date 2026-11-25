package com.example.shortener.orchestrator;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Explicit stage entry and exit gates. Entry: every prerequisite SUCCEEDED (and, for artifact-producing handlers, has a
 * CURRENT artifact). Exit: the output is stored as an artifact, passed evaluation and passed policy (for handlers that
 * produce no artifact: the handler returned normally). Every evaluation is recorded as a GATE_* audit event whose details
 * are the stage id and the check results (never secrets), and the run detail derives its gate view from those events.
 */
@Component
public class StageGates {
    public static final String PREREQS_SUCCEEDED = "all prerequisites succeeded";
    public static final String PREREQ_ARTIFACTS_CURRENT = "prerequisite artifacts are current";
    public static final String REQUIREMENT_PRESENT = "requirement is present";
    public static final String OUTPUT_STORED = "output stored as an artifact";
    public static final String OUTPUT_EVALUATED = "output passed evaluation";
    public static final String OUTPUT_POLICY = "output passed policy";
    public static final String HANDLER_COMPLETED = "handler completed normally";

    static final String ENTRY_PASSED = "GATE_ENTRY_PASSED";
    static final String ENTRY_FAILED = "GATE_ENTRY_FAILED";
    static final String EXIT_PASSED = "GATE_EXIT_PASSED";
    static final String EXIT_FAILED = "GATE_EXIT_FAILED";
    private static final List<String> EVENT_TYPES = List.of(ENTRY_PASSED, ENTRY_FAILED, EXIT_PASSED, EXIT_FAILED);
    private static final String SEP = " :: ";
    private static final String CHECK_SEP = " | ";

    /** Outcome of one gate: {@code checks} are "name: passed|failed" lines in declaration order. */
    public record GateResult(boolean passed, List<String> checks, String firstFailure) {}

    /** One gate as shown in the run detail; {@code passed} null means not evaluated yet (omitted from JSON). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Gate(Boolean passed, List<String> checks) {}

    public record Gates(Gate entry, Gate exit) {}

    private final AuditService audit;
    private final ArtifactService artifacts;
    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final Workflows workflows;
    private final StageHandler handler;

    public StageGates(AuditService audit, ArtifactService artifacts, AgentRunRepository runs, RunNodeRepository nodes,
                      Workflows workflows, StageHandler handler) {
        this.audit = audit;
        this.artifacts = artifacts;
        this.runs = runs;
        this.nodes = nodes;
        this.workflows = workflows;
        this.handler = handler;
    }

    /** Pure evaluation: each named check passes or fails according to {@code passes}. */
    static GateResult evaluate(List<String> names, Predicate<String> passes) {
        List<String> lines = new ArrayList<>();
        String first = null;
        for (String name : names) {
            boolean ok = passes.test(name);
            lines.add(name + ": " + (ok ? "passed" : "failed"));
            if (!ok && first == null) first = name;
        }
        return new GateResult(first == null, List.copyOf(lines), first);
    }

    /** Evaluates and records the entry gate of a node that is about to start. */
    public GateResult entry(long runId, String nodeId) {
        AgentRun run = runs.findById(runId).orElseThrow();
        RunNode node = nodes.findByRunIdAndNodeId(runId, nodeId).orElseThrow();
        List<String> names = entryChecks(declared(run, nodeId));
        Map<String, RunNode> byId = new HashMap<>();
        for (RunNode n : nodes.findByRunIdOrderById(runId)) byId.put(n.getNodeId(), n);
        GateResult r = evaluate(names, name -> switch (name) {
            case PREREQS_SUCCEEDED -> node.dependsOnIds().stream()
                    .allMatch(d -> byId.get(d) != null && byId.get(d).getStatus() == NodeStatus.SUCCEEDED);
            case PREREQ_ARTIFACTS_CURRENT -> node.dependsOnIds().stream()
                    .allMatch(d -> artifacts.current(runId, d, AgentStageHandler.ARTIFACT_TYPES.get(d)).isPresent());
            case REQUIREMENT_PRESENT -> run.getRawRequirement() != null && !run.getRawRequirement().isBlank();
            default -> true;
        });
        record(runId, r.passed() ? ENTRY_PASSED : ENTRY_FAILED, nodeId, r);
        return r;
    }

    /**
     * Evaluates and records the exit gate after the handler returned (or held its output). {@code held} is the
     * review-hold exception when the output failed evaluation or policy, otherwise null.
     */
    public GateResult exit(long runId, String nodeId, HeldForReviewException held) {
        AgentRun run = runs.findById(runId).orElseThrow();
        boolean policyHold = held != null && held.getMessage() != null && held.getMessage().contains("violates policy");
        List<String> names = exitChecks(declared(run, nodeId));
        String type = AgentStageHandler.ARTIFACT_TYPES.get(nodeId);
        GateResult r = evaluate(names, name -> switch (name) {
            case OUTPUT_STORED -> held == null && type != null && artifacts.current(runId, nodeId, type).isPresent();
            case OUTPUT_EVALUATED -> held == null || policyHold;
            case OUTPUT_POLICY -> !policyHold;
            case HANDLER_COMPLETED -> held == null;
            default -> true;
        });
        record(runId, r.passed() ? EXIT_PASSED : EXIT_FAILED, nodeId, r);
        return r;
    }

    private void record(long runId, String type, String nodeId, GateResult r) {
        audit.append(runId, "SYSTEM", "gates", type, nodeId + SEP + String.join(CHECK_SEP, r.checks()), null);
    }

    private NodeDef declared(AgentRun run, String nodeId) {
        try {
            return workflows.get(run.getWorkflowName()).nodes().stream().filter(n -> n.id().equals(nodeId)).findFirst().orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<String> entryChecks(NodeDef def) {
        List<String> names = def == null || def.entryChecks().isEmpty()
                ? List.of(PREREQS_SUCCEEDED, PREREQ_ARTIFACTS_CURRENT) : def.entryChecks();
        if (!handler.producesArtifacts()) names = names.stream().filter(n -> !n.equals(PREREQ_ARTIFACTS_CURRENT)).toList();
        return names.isEmpty() ? List.of(PREREQS_SUCCEEDED) : names;
    }

    private List<String> exitChecks(NodeDef def) {
        if (!handler.producesArtifacts()) return List.of(HANDLER_COMPLETED);
        return def == null || def.exitChecks().isEmpty() ? List.of(OUTPUT_STORED, OUTPUT_EVALUATED, OUTPUT_POLICY) : def.exitChecks();
    }

    /** Gate view per stage, derived from the persisted audit events (survives restart); null passed = not evaluated yet. */
    public Map<String, Gates> views(long runId, WorkflowDefinition def) {
        Map<String, Gate[]> state = new HashMap<>();
        for (AuditEvent e : audit.eventsOfTypes(runId, EVENT_TYPES)) {
            String details = e.getDetails() == null ? "" : e.getDetails();
            int i = details.indexOf(SEP);
            if (i < 0) continue;
            String stage = details.substring(0, i);
            String rest = details.substring(i + SEP.length());
            List<String> checks = rest.isEmpty() ? List.of() : List.of(rest.split(Pattern.quote(CHECK_SEP)));
            Gate[] g = state.computeIfAbsent(stage, k -> new Gate[2]);
            if (ENTRY_PASSED.equals(e.getEventType()) || ENTRY_FAILED.equals(e.getEventType())) {
                g[0] = new Gate(ENTRY_PASSED.equals(e.getEventType()), checks);
                g[1] = null; // a new entry evaluation starts a new execution: its exit is not evaluated yet
            } else {
                g[1] = new Gate(EXIT_PASSED.equals(e.getEventType()), checks);
            }
        }
        Map<String, Gates> out = new HashMap<>();
        for (NodeDef n : def.nodes()) {
            Gate[] g = state.getOrDefault(n.id(), new Gate[2]);
            out.put(n.id(), new Gates(g[0] != null ? g[0] : new Gate(null, n.entryChecks()),
                    g[1] != null ? g[1] : new Gate(null, n.exitChecks())));
        }
        return out;
    }
}
