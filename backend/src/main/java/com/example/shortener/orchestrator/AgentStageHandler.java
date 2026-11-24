package com.example.shortener.orchestrator;

import com.example.shortener.agents.Agent;
import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentProvider;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditService;
import com.example.shortener.evaluation.AgentOutputEvaluator;
import com.example.shortener.evaluation.OutputEvaluation;
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
 * the output against the versioned criteria, and stores it as a new artifact version with lineage. Output that fails
 * evaluation is held (never stored as CURRENT, never consumed downstream); a clarification request waits for a human.
 */
@Component
@ConditionalOnProperty(name = "shortener.orchestrator.handler", havingValue = "agents", matchIfMissing = true)
public class AgentStageHandler implements StageHandler {
    static final Map<String, String> ARTIFACT_TYPES = Map.of(
            "REQUIREMENTS_ANALYSIS", "REQUIREMENT",
            "TASK_DECOMPOSITION", "TASK_PLAN",
            "ARCHITECTURE_DESIGN", "DESIGN",
            "IMPLEMENTATION_PLAN", "IMPLEMENTATION_PLAN",
            "IMPLEMENTATION", "CODE_PROPOSAL",
            "TESTING", "TEST_REPORT",
            "DOCUMENTATION", "DOCUMENTATION",
            "RELEASE_READINESS", "RELEASE_CHECKLIST");

    private final AgentProvider provider;
    private final AgentOutputEvaluator evaluator;
    private final AgentRunRepository runs;
    private final RunNodeRepository nodes;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public AgentStageHandler(AgentProvider provider, AgentOutputEvaluator evaluator, AgentRunRepository runs,
                             RunNodeRepository nodes, ArtifactService artifacts, AuditService audit, ObjectMapper mapper) {
        this.provider = provider;
        this.evaluator = evaluator;
        this.runs = runs;
        this.nodes = nodes;
        this.artifacts = artifacts;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Override
    public String providerName() {
        return provider.providerName();
    }

    @Override
    public void execute(StageExecution ex) throws Exception {
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

        Agent agent = provider.forStage(ex.nodeId());
        AgentResult result = agent.run(input);
        AgentResult second = agent.run(input); // determinism check input
        OutputEvaluation evaluation = evaluator.evaluate(input, result, second);

        if (!evaluation.passed()) {
            audit.append(ex.runId(), "AGENT", agent.name(), "OUTPUT_FLAGGED",
                    "Stage " + ex.nodeId() + " output failed evaluation: " + evaluation.failedCriteria(), null);
            throw new HeldForReviewException("Output of " + ex.nodeId() + " failed evaluation: " + evaluation.failedCriteria());
        }
        if (result.status() == AgentStatus.FAILED) throw new IllegalStateException("Agent reported failure: " + result.summary());

        String content = mapper.writeValueAsString(result);
        Artifact stored = artifacts.create(ex.runId(), ex.nodeId(), type, content, agent.name(), parentIds, result.referencesToInputs());
        audit.append(ex.runId(), "AGENT", agent.name(), "ARTIFACT_CREATED",
                type + " v" + stored.getVersion() + " (score " + String.format("%.2f", evaluation.score()) + ", simulated=" + result.simulated() + ")", null);

        if (result.status() == AgentStatus.NEEDS_CLARIFICATION) {
            audit.append(ex.runId(), "AGENT", agent.name(), "CLARIFICATION_NEEDED",
                    result.openQuestions().size() + " open question(s); proceeding needs a human decision", null);
            throw new AwaitingDecisionException("Clarification needed: " + result.openQuestions().size() + " open question(s)");
        }
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
