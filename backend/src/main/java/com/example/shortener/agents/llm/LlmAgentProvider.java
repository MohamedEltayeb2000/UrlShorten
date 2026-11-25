package com.example.shortener.agents.llm;

import com.example.shortener.agents.Agent;
import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentProvider;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import com.example.shortener.orchestrator.TransientFailureException;
import com.example.shortener.orchestrator.ValidationFailureException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional external AI provider (001-FR-010), configured only through environment-fed Spring properties
 * ({@code shortener.agents.llm.*}). It never bypasses the scheduler: failures surface as
 * {@link TransientFailureException} (retried, then the simulated fallback is used) or
 * {@link ValidationFailureException} (never retried; the output is never trusted).
 *
 * <p>Provider-agnostic wire contract:
 * <pre>
 * POST &lt;endpoint&gt;
 * Content-Type: application/json
 * Authorization: Bearer &lt;key&gt;        (only when an API key is configured)
 *
 * {"model": "...", "stage": "&lt;stageId&gt;", "input": &lt;AgentInput as JSON&gt;, "contract": "&lt;description&gt;"}
 * </pre>
 * The response body must be a JSON object with the {@link AgentResult} fields: agentName, status
 * (SUCCESS | NEEDS_CLARIFICATION | FAILED | FALLBACK), summary, structuredOutput, assumptions, risks,
 * openQuestions, recommendedNextActions, confidence (0..1), confidenceNote, referencesToInputs.
 * Required: status, summary, structuredOutput, confidence. The result is always marked {@code simulated=false}.
 */
public class LlmAgentProvider implements AgentProvider {

    static final int MAX_SUMMARY = 2000;
    static final int MAX_LIST_ITEMS = 50;
    static final int MAX_ITEM_LENGTH = 2000;

    static final String CONTRACT = "Respond with one JSON object with fields: agentName (string), status (one of "
            + "SUCCESS, NEEDS_CLARIFICATION, FAILED, FALLBACK), summary (string), structuredOutput (object), "
            + "assumptions, risks, openQuestions, recommendedNextActions, referencesToInputs (arrays of strings), "
            + "confidence (number between 0 and 1), confidenceNote (string).";

    private static final Logger log = LoggerFactory.getLogger(LlmAgentProvider.class);
    private static final String[] STAGES = {"REQUIREMENTS_ANALYSIS", "TASK_DECOMPOSITION", "ARCHITECTURE_DESIGN",
            "IMPLEMENTATION_PLAN", "IMPLEMENTATION", "TESTING", "DOCUMENTATION", "RELEASE_READINESS"};

    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmAgentProvider(String endpoint, String apiKey, String model, long timeoutMs) {
        this.endpoint = URI.create(endpoint.trim());
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        this.model = model == null || model.isBlank() ? "default" : model.trim();
        this.timeout = Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 10_000);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    @Override
    public String providerName() {
        return "llm";
    }

    @Override
    public boolean deterministic() {
        return false;
    }

    @Override
    public Agent forStage(String stageId) {
        for (String s : STAGES) {
            if (s.equals(stageId)) {
                return new LlmAgent(stageId);
            }
        }
        throw new IllegalArgumentException("Unknown stage: " + stageId);
    }

    @Override
    public String toString() {
        return "LlmAgentProvider[endpoint=" + endpoint.getHost() + ", model=" + model + "]";
    }

    private final class LlmAgent implements Agent {
        private final String stageId;

        LlmAgent(String stageId) {
            this.stageId = stageId;
        }

        @Override
        public String name() {
            StringBuilder sb = new StringBuilder("Llm");
            for (String part : stageId.toLowerCase(Locale.ROOT).split("_")) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return sb.append("Agent").toString();
        }

        @Override
        public String stageId() {
            return stageId;
        }

        @Override
        public AgentResult run(AgentInput input) {
            String body = call(buildRequestBody(input));
            return parse(body, name());
        }

        @Override
        public String toString() {
            return name();
        }

        private String buildRequestBody(AgentInput input) {
            ObjectNode root = mapper.createObjectNode();
            root.put("model", model);
            root.put("stage", stageId);
            root.set("input", mapper.valueToTree(input));
            root.put("contract", CONTRACT);
            return root.toString();
        }

        private String call(String requestBody) {
            HttpRequest.Builder req = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            if (apiKey != null) {
                req.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> resp;
            try {
                resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransientFailureException("LLM call interrupted for stage " + stageId);
            } catch (IOException e) {
                // Only the exception type is reported: messages of lower layers are not trusted to be secret-free.
                log.warn("LLM call failed for stage {}: {}", stageId, e.getClass().getSimpleName());
                throw new TransientFailureException(
                        "LLM provider unavailable for stage " + stageId + " (" + e.getClass().getSimpleName() + ")");
            }
            int code = resp.statusCode();
            if (code >= 500 || code == 429) {
                throw new TransientFailureException("LLM provider returned HTTP " + code + " for stage " + stageId);
            }
            if (code < 200 || code >= 300) {
                throw new ValidationFailureException("LLM provider rejected the request with HTTP " + code);
            }
            return resp.body();
        }
    }

    private AgentResult parse(String body, String defaultName) {
        JsonNode n;
        try {
            n = mapper.readTree(body);
        } catch (IOException | RuntimeException e) {
            throw new ValidationFailureException("LLM response is not valid JSON");
        }
        if (n == null || !n.isObject()) {
            throw new ValidationFailureException("LLM response is not a JSON object");
        }
        for (String field : List.of("status", "summary", "structuredOutput", "confidence")) {
            if (!n.hasNonNull(field)) {
                throw new ValidationFailureException("LLM response is missing required field '" + field + "'");
            }
        }
        AgentStatus status;
        try {
            status = AgentStatus.valueOf(n.get("status").asText());
        } catch (IllegalArgumentException e) {
            throw new ValidationFailureException("LLM response has an unknown status");
        }
        if (!n.get("confidence").isNumber()) {
            throw new ValidationFailureException("LLM response confidence is not a number");
        }
        double confidence = n.get("confidence").asDouble();
        if (confidence < 0 || confidence > 1) {
            throw new ValidationFailureException("LLM response confidence is outside [0,1]");
        }
        if (!n.get("structuredOutput").isObject()) {
            throw new ValidationFailureException("LLM response structuredOutput is not an object");
        }
        Map<String, Object> structured = mapper.convertValue(n.get("structuredOutput"), new TypeReference<>() {});
        String name = n.hasNonNull("agentName") && !n.get("agentName").asText().isBlank()
                ? cap(n.get("agentName").asText(), 200)
                : defaultName;
        return new AgentResult(name, status, cap(n.get("summary").asText(), MAX_SUMMARY), structured,
                strings(n, "assumptions"), strings(n, "risks"), strings(n, "openQuestions"),
                strings(n, "recommendedNextActions"), confidence,
                n.hasNonNull("confidenceNote") ? cap(n.get("confidenceNote").asText(), MAX_ITEM_LENGTH) : "",
                strings(n, "referencesToInputs"), false);
    }

    private static List<String> strings(JsonNode n, String field) {
        JsonNode arr = n.get(field);
        if (arr == null || arr.isNull()) {
            return List.of();
        }
        if (!arr.isArray()) {
            throw new ValidationFailureException("LLM response field '" + field + "' is not an array");
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : arr) {
            if (out.size() >= MAX_LIST_ITEMS) {
                break;
            }
            out.add(cap(item.asText(), MAX_ITEM_LENGTH));
        }
        return out;
    }

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
