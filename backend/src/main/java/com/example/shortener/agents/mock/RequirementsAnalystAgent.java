package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns the raw requirement into goals and constraints; asks for clarification when ambiguous. */
public class RequirementsAnalystAgent extends BaseMockAgent {

    private final BrownfieldAnalyzer analyzer;

    public RequirementsAnalystAgent() {
        this(new BrownfieldAnalyzer());
    }

    public RequirementsAnalystAgent(BrownfieldAnalyzer analyzer) {
        super("RequirementsAnalystAgent", "REQUIREMENTS_ANALYSIS");
        this.analyzer = analyzer;
    }

    private static final List<String> GENERIC_QUESTIONS = List.of(
            "Who may create links: anyone, or only authenticated users?",
            "Should short links expire, and after how long?",
            "What volume of redirects per second must be supported?");

    @Override
    protected AgentResult build(AgentInput in) {
        String raw = notBlank(in.normalizedRequirement()) ? in.normalizedRequirement() : in.rawRequirement();
        RequirementAnalysis.Result analysis = RequirementAnalysis.analyze(raw);
        String topic = analysis.normalized().isEmpty() ? "the requested feature" : analysis.normalized();
        boolean brownfield = isScenario(in, BROWNFIELD);

        List<Object> goals = new ArrayList<>(analysis.goalTexts());
        List<Object> constraints = new ArrayList<>(analysis.constraints());
        for (String c : List.of("Only http and https target URLs are allowed",
                "No secrets or personal data in logs or outputs",
                "Agents propose changes only; a human approves before anything is applied")) {
            constraints.add(c);
        }
        for (String p : in.policyConstraints()) constraints.add(p);

        Map<String, Object> out = map("normalizedRequirement", topic, "goals", goals, "constraints", constraints);
        out.put("actors", new ArrayList<>(analysis.actors()));
        out.put("domainTerms", new ArrayList<>(analysis.domainTerms()));
        out.put("ambiguityScore", analysis.score());
        List<Object> triggers = new ArrayList<>();
        for (RequirementAnalysis.Trigger t : analysis.triggers()) triggers.add(t.kind() + ": " + t.detail());
        out.put("ambiguityTriggers", triggers);

        if (isScenario(in, AMBIGUOUS) || analysis.ambiguous()) {
            out.put("safeAssumptions", new ArrayList<>(SAFE_ASSUMPTIONS));
            List<String> questions = questions(analysis);
            List<String> assumptions = new ArrayList<>(SAFE_ASSUMPTIONS);
            for (String term : analysis.vagueTerms()) {
                assumptions.add("'" + term + "' is read conservatively: no behavior is relaxed, and the term is "
                        + "not treated as satisfied until a measurable criterion is confirmed");
            }
            return result(in, AgentStatus.NEEDS_CLARIFICATION,
                    "The requirement is ambiguous; clarification is needed. Proceeding with safe assumptions: " + topic,
                    out, assumptions, risks(analysis), questions,
                    List.of("Answer the open questions", "Confirm or replace the safe assumptions"),
                    0.45, "Simulated output; confidence is low because key details are missing from the requirement.");
        }

        List<String> risks = new ArrayList<>(List.of("MEDIUM: requirement may omit non-functional needs such as rate limits"));
        List<String> next = List.of("Review the goals", "Proceed to task decomposition");
        if (brownfield) {
            BrownfieldAnalyzer.Analysis a = analyzer.analyze(topic);
            out.put("impactedFiles", a.impactedFiles());
            out.put("dataFlow", a.dataFlow());
            out.put("compatibilityRisks", a.compatibilityRisks());
            risks.addAll(a.compatibilityRisks());
            goals.add("Make retried link creation idempotent without breaking existing clients");
            return ok(in, "Analyzed the existing service and the requirement: " + topic, out,
                    List.of("The fixture code reflects the deployed behavior"), risks, next);
        }
        return ok(in, "Analyzed the requirement: " + topic, out,
                List.of("The requirement describes a new, standalone capability"), risks, next);
    }

    /** Open questions derived from the actual triggers, padded with generic ones to at least three. */
    static List<String> questions(RequirementAnalysis.Result analysis) {
        Set<String> q = new LinkedHashSet<>();
        for (RequirementAnalysis.Trigger t : analysis.triggers()) {
            switch (t.kind()) {
                case RequirementAnalysis.VAGUE -> q.add(vagueQuestion(t.term()));
                case RequirementAnalysis.TOO_SHORT ->
                    q.add("Can you describe the expected behavior, inputs and outputs in more detail?");
                case RequirementAnalysis.UNDEFINED_REF ->
                    q.add("What does '" + t.term() + "' refer to in this requirement?");
                case RequirementAnalysis.CONFLICT ->
                    q.add("Which takes priority when they conflict: " + t.term().replace(" vs ", " or ") + "?");
                default -> { }
            }
        }
        for (String g : GENERIC_QUESTIONS) {
            if (q.size() >= 3) break;
            q.add(g);
        }
        return new ArrayList<>(q);
    }

    private static String vagueQuestion(String term) {
        return switch (term) {
            case "safe", "safer", "secure", "safely" -> "What does '" + term
                    + "' mean: blocking malicious URLs, preventing abuse, or protecting stored data?";
            case "better", "improve", "improved", "improving", "enhance", "enhanced", "optimize", "optimise",
                    "optimized", "efficient", "good", "nice" ->
                "What measurable outcome defines '" + term + "' (which metric, and what target value)?";
            case "fast", "faster", "quick", "quicker" -> "What latency or throughput target defines '" + term
                    + "' (for example p95 milliseconds or requests per second)?";
            case "scalable" -> "What load must it scale to (requests per second, number of stored links)?";
            case "robust", "reliable" -> "Which failures must be tolerated, and what availability target applies to '"
                    + term + "'?";
            default -> "Which users and tasks define '" + term + "', and how will it be judged as met?";
        };
    }

    private static List<String> risks(RequirementAnalysis.Result analysis) {
        List<String> vague = analysis.vagueTerms();
        String high = vague.isEmpty()
                ? "HIGH: unclear access control could expose link creation to abuse"
                : "HIGH: unmeasurable terms (" + String.join(", ", vague)
                        + ") may be implemented differently than intended and cannot be verified";
        return List.of(high,
                "MEDIUM: undefined expiry may cause unbounded storage growth",
                "LOW: expected traffic is unknown, so capacity planning is provisional");
    }
}
