package com.example.shortener.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.*;
import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.orchestrator.DefaultWorkflow;
import com.example.shortener.orchestrator.GraphValidator;
import com.example.shortener.orchestrator.NodeDef;
import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Runs the REAL mock agents through the whole workflow for each scenario and scores every output with the evaluator. */
@Tag("eval")
@Requirement("002-FR-011")
@Requirement("002-FR-012")
class ScenarioPipelineEvalIT {
    private final AgentOutputEvaluator evaluator =
            new AgentOutputEvaluator(EvaluationCriteria.load(RepoFiles.file("eval/criteria.json")));
    private final MockAgentProvider provider = new MockAgentProvider();

    private List<OutputEvaluation> runPipeline(String scenario, String requirement) {
        var def = DefaultWorkflow.create();
        Map<String, Map<String, Object>> outputs = new HashMap<>();
        List<OutputEvaluation> evaluations = new ArrayList<>();
        for (String stage : GraphValidator.topologicalOrder(def)) {
            NodeDef node = def.nodes().stream().filter(n -> n.id().equals(stage)).findFirst().orElseThrow();
            Map<String, Map<String, Object>> upstream = new HashMap<>();
            node.dependsOn().forEach(d -> upstream.put(d, outputs.get(d)));
            AgentInput in = new AgentInput(stage, scenario, requirement, requirement, upstream, List.of("proposals-only"));
            Agent agent = provider.forStage(stage);
            AgentResult r = agent.run(in);
            evaluations.add(evaluator.evaluate(in, r, agent.run(in)));
            outputs.put(stage, r.structuredOutput());
        }
        return evaluations;
    }

    @Test
    void everyStageOfEveryScenarioScoresAtTheThreshold() {
        for (String[] s : new String[][] {
                {"GREENFIELD", "Build a URL shortener API with link creation, redirects, expiration, and click analytics."},
                {"BROWNFIELD", "Add idempotent link creation and prevent duplicate links when clients retry requests."},
                {"AMBIGUOUS", "Make URL links safer and improve analytics."}}) {
            List<OutputEvaluation> evals = runPipeline(s[0], s[1]);
            assertEquals(8, evals.size());
            for (OutputEvaluation e : evals) {
                assertTrue(e.passed(), s[0] + "/" + e.stageId() + " failed " + e.failedCriteria());
                assertEquals(1.0, e.score(), 1e-9);
            }
        }
    }

    @Test
    void aLeakedSecretInRealAgentOutputIsCaught() {
        AgentInput in = new AgentInput("REQUIREMENTS_ANALYSIS", "GREENFIELD", "Build links", "Build links", Map.of(), List.of());
        Agent a = provider.forStage("REQUIREMENTS_ANALYSIS");
        AgentResult real = a.run(in);
        AgentResult leaky = new AgentResult(real.agentName(), real.status(), real.summary() + " password = hunter2hunter2",
                real.structuredOutput(), real.assumptions(), real.risks(), real.openQuestions(), real.recommendedNextActions(),
                real.confidence(), real.confidenceNote(), real.referencesToInputs(), real.simulated());
        OutputEvaluation e = evaluator.evaluate(in, leaky, leaky);
        assertFalse(e.passed());
        assertTrue(e.failedCriteria().contains("no_unsafe_content"));
    }
}
