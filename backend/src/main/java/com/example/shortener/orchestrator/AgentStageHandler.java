package com.example.shortener.orchestrator;

import com.example.shortener.agents.Agent;
import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentProvider;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditService;
import com.example.shortener.evaluation.AgentOutputEvaluator;
import com.example.shortener.evaluation.OutputEvaluation;
import com.example.shortener.governance.ActionType;
import com.example.shortener.governance.Approval;
import com.example.shortener.governance.PolicyDecision;
import com.example.shortener.governance.PolicyDecisionRecord;
import com.example.shortener.governance.PolicyDecisionRepository;
import com.example.shortener.governance.PolicyEngine;
import com.example.shortener.governance.PolicyOutcome;
import com.example.shortener.governance.ProposedAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the stage's agent with a scoped context (only the requirement and the artifacts of its prerequisites), evaluates
 * the output against the versioned criteria and the policy engine, and stores it as a new artifact version with lineage.
 * Output that fails evaluation or policy is held (never stored as CURRENT, never consumed downstream); a clarification
 * request, a high-impact stage, or a release with failing tests waits for a recorded human decision.
 */
@Component
@ConditionalOnProperty(name = "shortener.orchestrator.handler", havingValue = "agents", matchIfMissing = true)
public class AgentStageHandler implements StageHandler {
    public static final Map<String, String> ARTIFACT_TYPES = Map.of(
            "REQUIREMENTS_ANALYSIS", "REQUIREMENT",
            "TASK_DECOMPOSITION", "TASK_PLAN",
            "ARCHITECTURE_DESIGN", "DESIGN",
            "IMPLEMENTATION_PLAN", "IMPLEMENTATION_PLAN",
            "IMPLEMENTATION", "CODE_PROPOSAL",
            "TESTING", "TEST_REPORT",
            "DOCUMENTATION", "DOCUMENTATION",
            "RELEASE_READINESS", "RELEASE_CHECKLIST");

    private final AgentProvider provider;
    private final AgentProvider fallbackProvider = new MockAgentProvider();
    private final AgentOutputEvaluator evaluator;
    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final PolicyEngine policy;
    private final PolicyDecisionRepository decisions;
    private final FailureInjector injector;

    public AgentStageHandler(AgentProvider provider, AgentOutputEvaluator evaluator, AgentRunRepository runs,
                             RunNodeRepository nodes, ArtifactService artifacts, AuditService audit, ObjectMapper mapper,
                             PolicyEngine policy, PolicyDecisionRepository decisions, FailureInjector injector) {
        this.provider = provider;
        this.evaluator = evaluator;
        this.runs = runs;
        this.nodes = nodes;
        this.artifacts = artifacts;
        this.audit = audit;
        this.mapper = mapper;
        this.policy = policy;
        this.decisions = decisions;
        this.injector = injector;
    }

    @Override
    public String providerName() {
        return provider.providerName();
    }

    @Override
    public void execute(StageExecution ex) throws Exception {
        injector.maybeFail(ex.runId(), ex.nodeId()); // seeded transient failures (empty unless configured)
        process(ex, provider, false);
    }

    @Override
    public boolean supportsFallback() {
        return true;
    }

    @Override
    public void executeFallback(StageExecution ex) throws Exception {
        process(ex, fallbackProvider, true);
    }

    private void process(StageExecution ex, AgentProvider source, boolean fallback) throws Exception {
        AgentRun run = runs.findById(ex.runId()).orElseThrow();
        RunNode node = nodes.findByRunIdAndNodeId(ex.runId(), ex.nodeId()).orElseThrow();
        String type = ARTIFACT_TYPES.get(ex.nodeId());
        if (type == null) throw new IllegalStateException("No artifact type for stage " + ex.nodeId());

        // Scoped context: only the prerequisites' CURRENT artifacts.
        Map<String, Map<String, Object>> upstream = new HashMap<>();
        List<Long> parentIds = new ArrayList<>();
        for (String dep : node.dependsOnIds()) {
            Artifact a = artifacts.current(ex.runId(), dep, ARTIFACT_TYPES.get(dep)).orElseThrow(
                    () -> new IllegalStateException("prerequisite " + dep + " has no current artifact"));
            upstream.put(dep, structuredOutputOf(a.getContent()));
            parentIds.add(a.getId());
        }
        AgentInput input = new AgentInput(ex.nodeId(), run.getScenarioType(), run.getRawRequirement(),
                run.getNormalizedRequirement(), upstream, List.of("no-secrets", "no-unsafe-urls", "proposals-only"));

        Agent agent = source.forStage(ex.nodeId());
        AgentResult raw = agent.run(input);
        AgentResult result = fallback ? asFallback(raw) : raw;
        // Policy: proposed commands, migrations and deployments (IMPLEMENTATION). Every decision is recorded; a DENY holds
        // the output before it is evaluated or stored.
        List<PolicyDecision> proposalDecisions = proposalDecisions(ex, result.structuredOutput());
        for (PolicyDecision d : proposalDecisions) decisions.save(new PolicyDecisionRecord(ex.runId(), ex.nodeId(), d));
        for (PolicyDecision d : proposalDecisions) {
            if (d.outcome() == PolicyOutcome.DENY) {
                audit.append(ex.runId(), "SYSTEM", "policy", "POLICY_DENIED", ex.nodeId() + ": " + d.rule(), null);
                throw new HeldForReviewException("Output of " + ex.nodeId() + " violates policy " + d.rule());
            }
        }
        OutputEvaluation evaluation = evaluator.evaluate(input, result,
                !source.deterministic() ? result : fallback ? asFallback(agent.run(input)) : agent.run(input));
        if (!evaluation.passed()) {
            audit.append(ex.runId(), "AGENT", agent.name(), "OUTPUT_FLAGGED",
                    "Stage " + ex.nodeId() + " output failed evaluation: " + evaluation.failedCriteria(), null);
            throw new HeldForReviewException("Output of " + ex.nodeId() + " failed evaluation: " + evaluation.failedCriteria());
        }
        if (result.status() == AgentStatus.FAILED) throw new IllegalStateException("Agent reported failure: " + result.summary());

        String content = mapper.writeValueAsString(result);

        // Policy: no secrets / unsafe URL schemes in anything we are about to store.
        for (PolicyDecision d : policy.evaluateText(content)) {
            if (d.outcome() == PolicyOutcome.DENY) {
                decisions.save(new PolicyDecisionRecord(ex.runId(), ex.nodeId(), d));
                audit.append(ex.runId(), "SYSTEM", "policy", "POLICY_DENIED", ex.nodeId() + ": " + d.rule(), null);
                throw new HeldForReviewException("Output of " + ex.nodeId() + " violates policy " + d.rule());
            }
        }

        Artifact stored = artifacts.create(ex.runId(), ex.nodeId(), type, content, agent.name(), parentIds, result.referencesToInputs());
        audit.append(ex.runId(), "AGENT", agent.name(), "ARTIFACT_CREATED",
                type + " v" + stored.getVersion() + " (score " + String.format("%.2f", evaluation.score()) + ", simulated="
                        + result.simulated() + (fallback ? ", fallback" : "") + ")", null);

        if (result.status() == AgentStatus.NEEDS_CLARIFICATION) {
            audit.append(ex.runId(), "AGENT", agent.name(), "CLARIFICATION_NEEDED",
                    result.openQuestions().size() + " open question(s); proceeding needs a human decision", null);
            throw new AwaitingDecisionException(Approval.Kind.CLARIFICATION,
                    result.openQuestions().size() + " open question(s) need a decision before proceeding", stored.getVersion(),
                    "Clarification needed: " + result.openQuestions().size() + " open question(s)");
        }

        PolicyDecision action = actionDecision(ex, node, upstream, content);
        if (action != null) {
            decisions.save(new PolicyDecisionRecord(ex.runId(), ex.nodeId(), action));
            audit.append(ex.runId(), "SYSTEM", "policy", "POLICY_" + action.outcome().name(), ex.nodeId() + ": " + action.rule() + " - " + action.reason(), null);
        }
        boolean needsApproval = node.isRequiresApproval()
                || (action != null && action.outcome() != PolicyOutcome.ALLOW);
        if (needsApproval) {
            String reason = action != null ? action.rule() + ": " + action.reason() : "stage requires human approval";
            throw new AwaitingDecisionException(Approval.Kind.HIGH_IMPACT_ACTION, reason, stored.getVersion(),
                    "Approval required: " + reason);
        }
    }

    /** Evaluates the high-impact action this stage represents (patch proposal, release readiness), if any. */
    private PolicyDecision actionDecision(StageExecution ex, RunNode node, Map<String, Map<String, Object>> upstream, String content)
            throws JsonProcessingException {
        switch (ex.nodeId()) {
            case "IMPLEMENTATION": {
                PolicyDecision patch = policy.evaluateAction(new ProposedAction(ActionType.APPLY_PATCH, content, false, 0, false));
                StringBuilder reason = new StringBuilder(patch.reason());
                for (PolicyDecision d : proposalDecisions(ex, structuredOutputOf(content))) {
                    if (d.outcome() == PolicyOutcome.REQUIRE_APPROVAL) reason.append("; ").append(d.rule()).append(": ").append(d.reason());
                }
                return new PolicyDecision(patch.rule(), patch.outcome(), reason.toString(), patch.policyVersion());
            }
            case "RELEASE_READINESS": {
                int failed = failedTests(upstream.get("TESTING"));
                return policy.evaluateAction(new ProposedAction(ActionType.RELEASE_READINESS, content, false, failed, false));
            }
            default:
                return null;
        }
    }

    /** Policy decisions for the commands, migrations and deployment an IMPLEMENTATION output proposes (empty for other stages). */
    private List<PolicyDecision> proposalDecisions(StageExecution ex, Map<String, Object> structuredOutput) {
        List<PolicyDecision> out = new ArrayList<>();
        if (!"IMPLEMENTATION".equals(ex.nodeId()) || structuredOutput == null) return out;
        {
            com.fasterxml.jackson.databind.JsonNode so = mapper.valueToTree(structuredOutput);
            boolean deploy = false;
            for (com.fasterxml.jackson.databind.JsonNode c : so.path("proposedCommands")) {
                String cmd = c.asText("");
                out.add(policy.evaluateAction(new ProposedAction(ActionType.RUN_COMMAND, cmd, false, 0, false)));
                String lower = cmd.toLowerCase(java.util.Locale.ROOT);
                deploy |= lower.contains("kubectl") || lower.contains("terraform") || lower.contains("deploy");
            }
            for (com.fasterxml.jackson.databind.JsonNode m : so.path("proposedMigrations")) {
                out.add(policy.evaluateAction(new ProposedAction(ActionType.DESTRUCTIVE_DB, m.path("sql").asText(""),
                        m.path("touchesNonTestData").asBoolean(false), 0, false)));
            }
            if (deploy) out.add(policy.evaluateAction(new ProposedAction(ActionType.DEPLOY_PRODUCTION, "deployment command", true, 0, false)));
        }
        return out;
    }

    private static int failedTests(Map<String, Object> testing) {
        if (testing == null) return 0;
        Object results = testing.get("expectedResults");
        if (results instanceof Map<?, ?> m && m.get("failed") instanceof Number n) return n.intValue();
        return 0;
    }

    private static AgentResult asFallback(AgentResult r) {
        AgentStatus status = r.status() == AgentStatus.SUCCESS ? AgentStatus.FALLBACK : r.status();
        return new AgentResult(r.agentName(), status, r.summary(), r.structuredOutput(), r.assumptions(), r.risks(),
                r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(), r.referencesToInputs(), true);
    }

    private Map<String, Object> structuredOutputOf(String artifactJson) throws JsonProcessingException {
        Map<String, Object> all = mapper.readValue(artifactJson, new TypeReference<>() {});
        Object so = all.get("structuredOutput");
        return so instanceof Map<?, ?> m ? castMap(m) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
