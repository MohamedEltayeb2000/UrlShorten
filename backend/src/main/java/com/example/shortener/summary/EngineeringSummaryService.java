package com.example.shortener.summary;

import com.example.shortener.agents.AgentStatus;
import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.artifacts.ArtifactStatus;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.common.ApiException;
import com.example.shortener.governance.Approval;
import com.example.shortener.governance.ApprovalRepository;
import com.example.shortener.governance.PolicyDecisionRecord;
import com.example.shortener.governance.PolicyDecisionRepository;
import com.example.shortener.governance.PolicyEngine;
import com.example.shortener.governance.PolicyOutcome;
import com.example.shortener.orchestrator.AgentRun;
import com.example.shortener.orchestrator.AgentRunRepository;
import com.example.shortener.orchestrator.GraphValidator;
import com.example.shortener.orchestrator.NodeDef;
import com.example.shortener.orchestrator.NodeStatus;
import com.example.shortener.orchestrator.RunNode;
import com.example.shortener.orchestrator.RunNodeRepository;
import com.example.shortener.orchestrator.StageAttempt;
import com.example.shortener.orchestrator.StageAttemptRepository;
import com.example.shortener.orchestrator.WorkflowDefinition;
import com.example.shortener.orchestrator.Workflows;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Builds the final engineering summary of a run (001-FR-022) as Markdown, only from persisted data. The output is
 * deterministic for a given run state: it carries the run's own timestamps and no "generated at" clock text.
 */
@Service
public class EngineeringSummaryService {
    static final int MAX_TEXT = 500;
    static final String REDACTED = "[redacted by policy]";

    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final StageAttemptRepository attempts;
    private final ArtifactService artifacts;
    private final ApprovalRepository approvals;
    private final PolicyDecisionRepository policyDecisions;
    private final AuditService audit;
    private final Workflows workflows;
    private final ObjectMapper mapper;
    private final PolicyEngine policy = new PolicyEngine();

    public EngineeringSummaryService(AgentRunRepository runs, RunNodeRepository nodes, StageAttemptRepository attempts,
                                     ArtifactService artifacts, ApprovalRepository approvals,
                                     PolicyDecisionRepository policyDecisions, AuditService audit, Workflows workflows,
                                     ObjectMapper mapper) {
        this.runs = runs;
        this.nodes = nodes;
        this.attempts = attempts;
        this.artifacts = artifacts;
        this.approvals = approvals;
        this.policyDecisions = policyDecisions;
        this.audit = audit;
        this.workflows = workflows;
        this.mapper = mapper;
    }

    /** Everything read from the database for one run, so each section works on a consistent snapshot. */
    private record Data(AgentRun run, WorkflowDefinition workflow, List<RunNode> nodes, List<Artifact> allArtifacts,
                        List<Artifact> current, Map<Long, JsonNode> content, List<Approval> approvals,
                        List<PolicyDecisionRecord> policy, List<AuditEvent> events) {}

    public String generate(long runId) {
        AgentRun run = runs.findById(runId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Run not found"));
        List<Artifact> all = artifacts.list(runId);
        List<Artifact> current = all.stream().filter(a -> a.getStatus() == ArtifactStatus.CURRENT).toList();
        Map<Long, JsonNode> content = new LinkedHashMap<>();
        for (Artifact a : all) content.put(a.getId(), parse(a.getContent()));
        Data d = new Data(run, workflows.get(run.getWorkflowName()), nodes.findByRunIdOrderById(runId), all, current,
                content, approvals.findByRunIdOrderByIdAsc(runId), policyDecisions.findByRunIdOrderByIdAsc(runId),
                allEvents(runId));

        StringBuilder out = new StringBuilder("# Engineering summary - run " + runId + "\n\n");
        out.append(section("Overview", overview(d)));
        out.append(section("Plan and rationale", plan(d)));
        out.append(section("Artifacts", artifactsSection(d)));
        out.append(section("Decisions and approvals", decisions(d)));
        out.append(section("Assumptions", assumptions(d)));
        out.append(section("Risks and trade-offs", risks(d)));
        out.append(section("Validation", validation(d)));
        out.append(section("Limitations", limitations(d)));
        return out.toString();
    }

    /** Appends one section, replacing its text when the policy engine denies it (e.g. an embedded secret). */
    private String section(String heading, String body) {
        boolean denied = policy.evaluateText(body).stream().anyMatch(p -> p.outcome() == PolicyOutcome.DENY);
        return "## " + heading + "\n\n" + (denied ? REDACTED : body.stripTrailing()) + "\n\n";
    }

    // ---- Overview and plan ------------------------------------------------------------------------------------

    private String overview(Data d) {
        AgentRun r = d.run();
        StringBuilder s = new StringBuilder();
        s.append("- Run id: ").append(r.getId()).append('\n');
        s.append("- Scenario: ").append(r.getScenarioType() == null ? "not specified" : r.getScenarioType()).append('\n');
        s.append("- Status: ").append(r.getStatus()).append('\n');
        s.append("- Plan version: ").append(r.getPlanVersion()).append('\n');
        s.append("- Workflow: ").append(text(r.getWorkflowName())).append('\n');
        s.append("- Created: ").append(time(r.getCreatedAt())).append('\n');
        s.append("- Started: ").append(time(r.getStartedAt())).append('\n');
        s.append("- Completed: ").append(time(r.getCompletedAt())).append("\n\n");
        s.append("Requirement:\n\n");
        for (String line : limit(r.getRawRequirement()).split("\\R", -1)) {
            s.append("> ").append(escapeHtml(line)).append('\n');
        }
        return s.toString();
    }

    private String plan(Data d) {
        WorkflowDefinition wf = d.workflow();
        Map<String, NodeDef> byId = new LinkedHashMap<>();
        wf.nodes().forEach(n -> byId.put(n.id(), n));
        Map<String, Integer> levels = GraphValidator.levels(wf);
        StringBuilder s = new StringBuilder("Workflow graph (stage, prerequisites):\n\n");
        for (String id : GraphValidator.topologicalOrder(wf)) {
            NodeDef n = byId.get(id);
            s.append("- ").append(id).append(" (").append(text(n.name())).append("): ")
                    .append(n.dependsOn().isEmpty() ? "no prerequisites" : "after " + String.join(", ", n.dependsOn()));
            if (n.requiresApproval()) s.append(" [approval gate]");
            s.append('\n');
        }
        s.append("\nParallel stages (same dependency level):\n\n");
        Map<Integer, List<String>> byLevel = new TreeMap<>();
        levels.forEach((id, lvl) -> byLevel.computeIfAbsent(lvl, k -> new ArrayList<>()).add(id));
        boolean anyParallel = false;
        for (Map.Entry<Integer, List<String>> e : byLevel.entrySet()) {
            if (e.getValue().size() > 1) {
                anyParallel = true;
                s.append("- Level ").append(e.getKey()).append(": ").append(String.join(", ", e.getValue().stream().sorted().toList())).append('\n');
            }
        }
        if (!anyParallel) s.append("- none, the stages run strictly in sequence\n");
        List<String> gates = wf.nodes().stream().filter(NodeDef::requiresApproval).map(NodeDef::id).toList();
        s.append("\nApproval gates: ").append(gates.isEmpty() ? "none declared" : String.join(", ", gates))
                .append(". Policy rules can add further gates at run time.\n");

        s.append("\nRationale (from the requirement, task plan and design artifacts):\n\n");
        boolean any = false;
        for (Artifact a : d.current()) {
            if (!Set.of("REQUIREMENT", "TASK_PLAN", "DESIGN").contains(a.getArtifactType())) continue;
            JsonNode c = d.content().get(a.getId());
            String summary = c.path("summary").asText("");
            if (!summary.isBlank()) {
                s.append("- ").append(a.getArtifactType()).append(": ").append(text(summary)).append('\n');
                any = true;
            }
            for (String key : List.of("goals", "decisions")) {
                for (String item : strings(c.path("structuredOutput").path(key))) {
                    s.append("- ").append(a.getArtifactType()).append(' ').append(key).append(": ").append(text(item)).append('\n');
                    any = true;
                }
            }
        }
        if (!any) s.append("- none recorded yet\n");
        return s.toString();
    }

    // ---- Artifacts, decisions ---------------------------------------------------------------------------------

    private String artifactsSection(Data d) {
        Map<Long, String> label = new LinkedHashMap<>();
        d.allArtifacts().forEach(a -> label.put(a.getId(), a.getArtifactType() + " v" + a.getVersion()));
        StringBuilder s = new StringBuilder();
        s.append("| Stage | Type | Version | Status | Checksum | Created by | Parents |\n");
        s.append("|---|---|---|---|---|---|---|\n");
        for (Artifact a : d.allArtifacts()) {
            List<String> parents = new ArrayList<>();
            for (String p : a.getParentArtifactIds().split(",")) {
                if (p.isBlank()) continue;
                parents.add(parseId(p).map(id -> label.getOrDefault(id, "#" + id)).orElse(p.strip()));
            }
            s.append("| ").append(cell(a.getNodeId())).append(" | ").append(cell(a.getArtifactType()))
                    .append(" | ").append(a.getVersion()).append(" | ").append(a.getStatus())
                    .append(" | ").append(cell(a.getChecksum().substring(0, Math.min(12, a.getChecksum().length()))))
                    .append(" | ").append(cell(a.getCreatedBy()))
                    .append(" | ").append(parents.isEmpty() ? "-" : cell(String.join(", ", parents))).append(" |\n");
        }
        if (d.allArtifacts().isEmpty()) s.append("| - | - | - | - | - | - | - |\n");
        s.append("\nThe table shows metadata only. CODE_PROPOSAL artifacts are proposals: they were never applied to any code base.\n");
        return s.toString();
    }

    private String decisions(Data d) {
        StringBuilder s = new StringBuilder("Approvals:\n\n");
        if (d.approvals().isEmpty()) s.append("- none requested\n");
        for (Approval a : d.approvals()) {
            s.append("- ").append(a.getNodeId()).append(" (").append(a.getKind()).append(", artifact v").append(a.getArtifactVersion())
                    .append("): ").append(a.getStatus());
            if (a.getActorId() != null) s.append(" by ").append(text(a.getActorId()));
            if (a.getComment() != null && !a.getComment().isBlank()) s.append(", comment: \"").append(text(a.getComment())).append('"');
            if (a.getPolicyReason() != null && !a.getPolicyReason().isBlank()) s.append(", reason: ").append(text(a.getPolicyReason()));
            s.append('\n');
        }
        s.append("\nPolicy decisions:\n\n");
        if (d.policy().isEmpty()) s.append("- none recorded\n");
        for (PolicyDecisionRecord p : d.policy()) {
            s.append("- ").append(p.getNodeId()).append(": ").append(text(p.getRule())).append(" -> ").append(p.getOutcome())
                    .append(" (").append(text(p.getReason())).append(")\n");
        }
        return s.toString();
    }

    // ---- Assumptions, risks, validation, limitations ----------------------------------------------------------

    /** Union of a string-list field over the CURRENT artifacts, de-duplicated, mapped to the stages that raised it. */
    private Map<String, Set<String>> union(Data d, String field) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Artifact a : d.current()) {
            for (String item : strings(d.content().get(a.getId()).path(field))) {
                result.computeIfAbsent(text(item), k -> new LinkedHashSet<>()).add(a.getNodeId());
            }
        }
        return result;
    }

    private String assumptions(Data d) {
        StringBuilder s = new StringBuilder();
        Map<String, Set<String>> items = union(d, "assumptions");
        if (items.isEmpty()) s.append("- none recorded\n");
        items.forEach((t, stages) -> s.append("- ").append(t).append(" (").append(String.join(", ", stages)).append(")\n"));

        s.append("\nOpen questions:\n\n");
        boolean any = false;
        for (Artifact a : d.current()) {
            List<String> questions = strings(d.content().get(a.getId()).path("openQuestions"));
            if (questions.isEmpty()) continue;
            boolean resolved = d.approvals().stream().anyMatch(p -> p.getNodeId().equals(a.getNodeId())
                    && p.getKind() == Approval.Kind.CLARIFICATION && p.getStatus() == Approval.Status.APPROVED);
            for (String q : questions) {
                any = true;
                s.append("- ").append(text(q)).append(" (").append(a.getNodeId()).append(", ")
                        .append(resolved ? "resolved by an approved clarification" : "unresolved").append(")\n");
            }
        }
        if (!any) s.append("- none\n");
        return s.toString();
    }

    private String risks(Data d) {
        StringBuilder s = new StringBuilder();
        Map<String, Set<String>> items = union(d, "risks");
        if (items.isEmpty()) s.append("- none recorded\n");
        items.forEach((t, stages) -> s.append("- ").append(t).append(" (").append(String.join(", ", stages)).append(")\n"));
        return s.toString();
    }

    private String validation(Data d) {
        StringBuilder s = new StringBuilder("Stage attempts:\n\n");
        for (RunNode n : d.nodes()) {
            List<StageAttempt> list = attempts.findByRunNodeIdOrderByAttemptAsc(n.getId());
            s.append("- ").append(n.getNodeId()).append(": ").append(n.getStatus()).append(", ").append(list.size()).append(" attempt(s)");
            if (!list.isEmpty()) {
                s.append(" [");
                for (int i = 0; i < list.size(); i++) {
                    StageAttempt a = list.get(i);
                    if (i > 0) s.append("; ");
                    s.append('#').append(a.getAttempt()).append(' ').append(a.getStatus());
                    if (a.isFallbackUsed()) s.append(" (fallback)");
                }
                s.append(']');
            }
            s.append('\n');
        }
        s.append("\nRetries: ").append(count(d, "STAGE_RETRY")).append(", fallbacks used: ").append(count(d, "FALLBACK_USED")).append("\n\n");

        s.append("Test report:\n\n");
        JsonNode results = d.current().stream().filter(a -> a.getArtifactType().equals("TEST_REPORT")).findFirst()
                .map(a -> d.content().get(a.getId()).path("structuredOutput").path("expectedResults")).orElse(null);
        if (results == null || !results.isObject() || results.isEmpty()) {
            s.append("- no test report available\n");
        } else {
            results.fields().forEachRemaining(e -> s.append("- ").append(text(e.getKey())).append(": ").append(text(e.getValue().asText())).append('\n'));
        }
        s.append("\nEvaluation and policy flags: ").append(count(d, "OUTPUT_FLAGGED")).append(" output(s) flagged, ")
                .append(count(d, "POLICY_DENIED")).append(" policy denial(s)\n");
        s.append("\nAudit events recorded: ").append(d.events().size()).append('\n');
        return s.toString();
    }

    private String limitations(Data d) {
        StringBuilder s = new StringBuilder();
        s.append("- The agents are deterministic simulations, not real model output.\n");
        s.append("- All output is a proposal; nothing was applied to a code base.\n");
        s.append("- The identity is a demo header, not authentication.\n");
        s.append("- The scheduler is single-instance.\n");
        for (RunNode n : d.nodes()) {
            if (n.getStatus() == NodeStatus.WAITING_FOR_APPROVAL) s.append("- Stage ").append(n.getNodeId()).append(" is still waiting for a decision.\n");
            if (n.getStatus() == NodeStatus.BLOCKED) s.append("- Stage ").append(n.getNodeId()).append(" is blocked or held for review").append(reason(n)).append(".\n");
            if (n.getStatus() == NodeStatus.FAILED) s.append("- Stage ").append(n.getNodeId()).append(" failed").append(reason(n)).append(".\n");
        }
        for (RunNode n : d.nodes()) {
            if (attempts.findByRunNodeIdOrderByAttemptAsc(n.getId()).stream().anyMatch(StageAttempt::isFallbackUsed)) {
                s.append("- Stage ").append(n.getNodeId()).append(" used fallback output.\n");
            }
        }
        for (Artifact a : d.current()) {
            JsonNode c = d.content().get(a.getId());
            if (c.path("simulated").asBoolean(false)) s.append("- Output of ").append(a.getNodeId()).append(" is flagged as simulated.\n");
            if (AgentStatus.NEEDS_CLARIFICATION.name().equals(c.path("status").asText())) {
                s.append("- Output of ").append(a.getNodeId()).append(" asked for clarification.\n");
            }
        }
        return s.toString();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static String reason(RunNode n) {
        return n.getErrorSummary() == null || n.getErrorSummary().isBlank() ? "" : " (" + text(n.getErrorSummary()) + ")";
    }

    private long count(Data d, String type) {
        return d.events().stream().filter(e -> e.getEventType().equals(type)).count();
    }

    private List<AuditEvent> allEvents(long runId) {
        List<AuditEvent> events = new ArrayList<>();
        for (int page = 0; ; page++) {
            var p = audit.events(runId, page, 100);
            events.addAll(p.items());
            if (page + 1 >= p.totalPages()) return events;
        }
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) return result;
        if (node.isArray()) node.forEach(n -> result.add(n.isValueNode() ? n.asText() : n.toString()));
        else result.add(node.isValueNode() ? node.asText() : node.toString());
        return result;
    }

    private static java.util.Optional<Long> parseId(String s) {
        try {
            return java.util.Optional.of(Long.parseLong(s.strip()));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private static String time(java.time.Instant t) {
        return t == null ? "not yet" : t.toString();
    }

    private static String limit(String s) {
        if (s == null) return "";
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) + "..." : s;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Single-line, truncated, HTML-escaped text for list items. */
    static String text(String s) {
        return escapeHtml(limit(s).replaceAll("\\s+", " ").strip());
    }

    /** Text safe inside a Markdown table cell: no pipes, no newlines. */
    static String cell(String s) {
        return text(s).replace("\\", "\\\\").replace("|", "\\|");
    }
}
