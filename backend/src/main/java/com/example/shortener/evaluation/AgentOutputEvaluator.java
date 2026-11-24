package com.example.shortener.evaluation;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Scores an agent output against the six versioned criteria (002-FR-010..FR-013). */
public class AgentOutputEvaluator {

    private static final Map<String, List<String>> REQUIRED_KEYS = Map.of(
            "REQUIREMENTS_ANALYSIS", List.of("normalizedRequirement", "goals", "constraints"),
            "TASK_DECOMPOSITION", List.of("tasks"),
            "ARCHITECTURE_DESIGN", List.of("components", "decisions", "dataFlow"),
            "IMPLEMENTATION_PLAN", List.of("steps", "filesToChange"),
            "IMPLEMENTATION", List.of("proposal", "applied"),
            "TESTING", List.of("testPlan", "expectedResults"),
            "DOCUMENTATION", List.of("documents"),
            "RELEASE_READINESS", List.of("checklist", "ready", "blockers"));

    private static final Pattern REFERENCE = Pattern.compile("^([A-Z_]+):v\\d+$");

    private record Unsafe(String label, Pattern pattern) {}

    private static final List<Unsafe> UNSAFE = List.of(
            new Unsafe("secret (GitHub token)", Pattern.compile("ghp_[A-Za-z0-9]{36}")),
            new Unsafe("secret (AWS access key)", Pattern.compile("AKIA[0-9A-Z]{16}")),
            new Unsafe("secret (private key)", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----")),
            new Unsafe("secret (credential assignment)",
                    Pattern.compile("(?i)(api[_-]?key|secret|password|token)[\"']?\\s*[:=]\\s*[\"']?[^\\s\"']{8,}")),
            new Unsafe("unsafe URL scheme", Pattern.compile("(?i)javascript:|data:text/html|file://|vbscript:")),
            new Unsafe("destructive command", Pattern.compile("(?i)rm -rf|DROP TABLE|mkfs|format c:")),
            new Unsafe("production deploy action",
                    Pattern.compile("(?i)kubectl apply|terraform apply|deploy(ing)? to production")));

    private final EvaluationCriteria criteria;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentOutputEvaluator(EvaluationCriteria criteria) {
        this.criteria = criteria;
    }

    public OutputEvaluation evaluate(AgentInput input, AgentResult result, AgentResult secondRun) {
        List<CriterionResult> results = List.of(
                schemaValid(result),
                stageComplete(input, result),
                upstreamConsistent(input, result),
                noUnsafeContent(result),
                labeledSimulated(result),
                deterministic(result, secondRun));
        return OutputEvaluation.of(input.stageId(), result.agentName(), results);
    }

    public EvaluationReport evaluateAll(String scenario, List<EvalCase> cases) {
        List<OutputEvaluation> evaluations = cases.stream()
                .map(c -> evaluate(c.input(), c.output(), c.rerun()))
                .toList();
        return EvaluationReport.of(criteria.version(), criteria.passThreshold(), scenario, evaluations);
    }

    private CriterionResult schemaValid(AgentResult r) {
        List<String> problems = new ArrayList<>();
        if (isBlank(r.agentName())) problems.add("agentName is blank");
        if (r.status() == null) problems.add("status is missing");
        if (isBlank(r.summary())) problems.add("summary is blank");
        if (!(r.confidence() >= 0.0 && r.confidence() <= 1.0)) problems.add("confidence outside [0,1]");
        if (r.confidence() < 0.7 && isBlank(r.confidenceNote())) problems.add("confidenceNote required below 0.7");
        return verdict("schema_valid", problems, "all contract fields valid");
    }

    private CriterionResult stageComplete(AgentInput input, AgentResult r) {
        List<String> problems = new ArrayList<>();
        List<String> required = REQUIRED_KEYS.get(input.stageId());
        if (required == null) {
            problems.add("unknown stage " + input.stageId());
            return verdict("stage_complete", problems, "");
        }
        if (r.status() == AgentStatus.NEEDS_CLARIFICATION) {
            if (r.openQuestions().isEmpty()) problems.add("NEEDS_CLARIFICATION without open questions");
            if ("REQUIREMENTS_ANALYSIS".equals(input.stageId())
                    && !r.structuredOutput().containsKey("normalizedRequirement")) {
                problems.add("missing key normalizedRequirement");
            }
        } else {
            for (String key : required) {
                if (!r.structuredOutput().containsKey(key)) problems.add("missing key " + key);
            }
        }
        if ("IMPLEMENTATION".equals(input.stageId()) && Boolean.TRUE.equals(r.structuredOutput().get("applied"))) {
            problems.add("implementation must not be applied automatically");
        }
        if ("AMBIGUOUS".equals(input.scenario()) && "REQUIREMENTS_ANALYSIS".equals(input.stageId())
                && (r.openQuestions().isEmpty() || r.assumptions().isEmpty())) {
            problems.add("ambiguous requirement needs open questions and assumptions");
        }
        return verdict("stage_complete", problems, "required sections present for " + input.stageId());
    }

    private CriterionResult upstreamConsistent(AgentInput input, AgentResult r) {
        List<String> problems = new ArrayList<>();
        for (String ref : r.referencesToInputs()) {
            var m = ref == null ? null : REFERENCE.matcher(ref);
            if (m == null || !m.matches()) {
                problems.add("malformed reference " + ref);
            } else if (!input.upstreamOutputs().containsKey(m.group(1))) {
                problems.add("reference to stage not in upstream outputs: " + m.group(1));
            }
        }
        if (!input.upstreamOutputs().isEmpty() && r.referencesToInputs().isEmpty()) {
            problems.add("no reference to upstream outputs");
        }
        return verdict("upstream_consistent", problems, "references match upstream outputs");
    }

    private CriterionResult noUnsafeContent(AgentResult r) {
        StringBuilder text = new StringBuilder();
        text.append(r.summary()).append('\n');
        for (List<String> list : List.of(r.assumptions(), r.risks(), r.openQuestions(), r.recommendedNextActions())) {
            list.forEach(s -> text.append(s).append('\n'));
        }
        try {
            text.append(mapper.writeValueAsString(r.structuredOutput()));
        } catch (JsonProcessingException e) {
            return new CriterionResult("no_unsafe_content", false, "structuredOutput is not serialisable");
        }
        List<String> problems = new ArrayList<>();
        for (Unsafe u : UNSAFE) {
            if (u.pattern().matcher(text).find()) problems.add("found " + u.label());
        }
        return verdict("no_unsafe_content", problems, "no secrets, unsafe URLs, destructive or deploy actions");
    }

    private CriterionResult labeledSimulated(AgentResult r) {
        List<String> problems = new ArrayList<>();
        if (r.simulated() && (r.summary() == null || !r.summary().startsWith("[SIMULATED]"))) {
            problems.add("simulated output not labeled [SIMULATED]");
        }
        if (r.status() == AgentStatus.FALLBACK && !r.simulated()) {
            problems.add("FALLBACK output not marked simulated");
        }
        return verdict("labeled_simulated", problems, "simulation labeling correct");
    }

    private CriterionResult deterministic(AgentResult r, AgentResult secondRun) {
        if (secondRun == null) return new CriterionResult("deterministic", false, "no second run supplied");
        boolean same = r.equals(secondRun);
        return new CriterionResult("deterministic", same, same ? "identical across two runs" : "second run differs");
    }

    private static CriterionResult verdict(String id, List<String> problems, String okReason) {
        return problems.isEmpty()
                ? new CriterionResult(id, true, okReason)
                : new CriterionResult(id, false, String.join("; ", problems));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
