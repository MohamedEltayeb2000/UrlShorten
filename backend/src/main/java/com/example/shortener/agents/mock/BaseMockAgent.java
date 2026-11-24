package com.example.shortener.agents.mock;

import com.example.shortener.agents.Agent;
import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Shared plumbing for the deterministic mock agents: builds the contract fields consistently and offers
 * helpers for reading upstream outputs. No clocks, randomness or unordered iteration.
 */
abstract class BaseMockAgent implements Agent {

    static final String AMBIGUOUS = "AMBIGUOUS";
    static final String BROWNFIELD = "BROWNFIELD";

    /** Assumptions the pipeline proceeds with when the requirement is ambiguous. */
    static final List<String> SAFE_ASSUMPTIONS = List.of(
            "Short links are public and anyone with the link may follow it",
            "Only http and https target URLs are accepted",
            "Links do not expire unless an expiry is explicitly set");

    private final String name;
    private final String stageId;

    BaseMockAgent(String name, String stageId) {
        this.name = name;
        this.stageId = stageId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String stageId() {
        return stageId;
    }

    @Override
    public final AgentResult run(AgentInput input) {
        return build(input);
    }

    protected abstract AgentResult build(AgentInput input);

    // ---- result helpers -------------------------------------------------------------------------------

    /** SUCCESS result; scenario-specific assumptions are carried forward automatically. */
    AgentResult ok(AgentInput in, String summary, Map<String, Object> output, List<String> ownAssumptions,
            List<String> risks, List<String> nextActions) {
        boolean ambiguous = isScenario(in, AMBIGUOUS);
        double confidence = ambiguous ? 0.7 : 0.85;
        String note = ambiguous
                ? "Simulated output; confidence is reduced because the requirement is ambiguous and safe assumptions were carried forward."
                : "Simulated output from deterministic rules, not a model; confidence is a fixed heuristic.";
        return result(in, AgentStatus.SUCCESS, summary, output, ownAssumptions, risks, List.of(), nextActions,
                confidence, note);
    }

    AgentResult result(AgentInput in, AgentStatus status, String summary, Map<String, Object> output,
            List<String> ownAssumptions, List<String> risks, List<String> questions, List<String> nextActions,
            double confidence, String note) {
        List<String> assumptions = new ArrayList<>(ownAssumptions);
        if (isScenario(in, AMBIGUOUS)) {
            for (String a : SAFE_ASSUMPTIONS) {
                if (!assumptions.contains(a)) assumptions.add(a);
            }
        }
        return new AgentResult(name, status, "[SIMULATED] " + summary, output, assumptions, risks, questions,
                nextActions, confidence, note, references(in), true);
    }

    static List<String> references(AgentInput in) {
        List<String> refs = new ArrayList<>();
        for (String stage : new TreeSet<>(in.upstreamOutputs().keySet())) {
            refs.add(stage + ":v1");
        }
        return refs;
    }

    // ---- input helpers --------------------------------------------------------------------------------

    static boolean isScenario(AgentInput in, String scenario) {
        return scenario.equalsIgnoreCase(in.scenario());
    }

    /** The best available requirement text, trimmed and shortened. */
    static String topic(AgentInput in) {
        String text = notBlank(in.normalizedRequirement()) ? in.normalizedRequirement() : in.rawRequirement();
        if (!notBlank(text)) return "the requested feature";
        text = text.trim().replaceAll("\\s+", " ");
        return text.length() > 140 ? text.substring(0, 140) : text;
    }

    static boolean isLinks(AgentInput in) {
        String t = ((in.rawRequirement() == null ? "" : in.rawRequirement()) + " "
                + (in.normalizedRequirement() == null ? "" : in.normalizedRequirement())).toLowerCase();
        return t.contains("link") || t.contains("url") || t.contains("shorten");
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Reads a list from an upstream stage's output; empty when absent. */
    static List<Object> upstreamList(AgentInput in, String stage, String key) {
        Map<String, Object> out = in.upstreamOutputs().get(stage);
        if (out != null && out.get(key) instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        return List.of();
    }

    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    /** Text of a list element: map elements use the given field, others toString. */
    static String label(Object o, String field) {
        if (o instanceof Map<?, ?> m && m.get(field) != null) return String.valueOf(m.get(field));
        return String.valueOf(o);
    }
}
