package com.example.shortener.agents.mock;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Override
    protected AgentResult build(AgentInput in) {
        String topic = topic(in);
        boolean links = isLinks(in);
        List<Object> goals = new ArrayList<>(links
                ? List.of("Create a short link for a long URL",
                        "Redirect a short code to its original URL",
                        "Reject unsafe or malformed target URLs",
                        "Count visits to each short link")
                : List.of("Deliver the core capability: " + topic,
                        "Validate inputs and report clear errors",
                        "Make the behavior observable and testable"));
        List<Object> constraints = new ArrayList<>(List.of(
                "Only http and https target URLs are allowed",
                "No secrets or personal data in logs or outputs",
                "Agents propose changes only; a human approves before anything is applied"));
        for (String p : in.policyConstraints()) constraints.add(p);

        Map<String, Object> out = map("normalizedRequirement", topic, "goals", goals, "constraints", constraints);

        if (isScenario(in, AMBIGUOUS)) {
            out.put("safeAssumptions", new ArrayList<>(SAFE_ASSUMPTIONS));
            List<String> questions = List.of(
                    "Who may create links: anyone, or only authenticated users?",
                    "Should short links expire, and after how long?",
                    "What volume of redirects per second must be supported?");
            List<String> risks = List.of(
                    "HIGH: unclear access control could expose link creation to abuse",
                    "MEDIUM: undefined expiry may cause unbounded storage growth",
                    "LOW: expected traffic is unknown, so capacity planning is provisional");
            return result(in, AgentStatus.NEEDS_CLARIFICATION,
                    "The requirement is ambiguous; clarification is needed. Proceeding with safe assumptions: " + topic,
                    out, List.of("Requirement text is incomplete and open to several readings"), risks, questions,
                    List.of("Answer the open questions", "Confirm or replace the safe assumptions"),
                    0.45, "Simulated output; confidence is low because key details are missing from the requirement.");
        }

        List<String> risks = new ArrayList<>(List.of("MEDIUM: requirement may omit non-functional needs such as rate limits"));
        List<String> next = List.of("Review the goals", "Proceed to task decomposition");
        if (isScenario(in, BROWNFIELD)) {
            BrownfieldAnalyzer.Analysis a = analyzer.analyze();
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
}
