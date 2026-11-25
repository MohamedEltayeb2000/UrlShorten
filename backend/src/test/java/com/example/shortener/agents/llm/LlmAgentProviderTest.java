package com.example.shortener.agents.llm;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.shortener.agents.Agent;
import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import com.example.shortener.agents.mock.MockAgentProvider;
import com.example.shortener.orchestrator.TransientFailureException;
import com.example.shortener.orchestrator.ValidationFailureException;
import com.example.shortener.support.Requirement;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@Requirement("001-FR-010")
class LlmAgentProviderTest {

    private static final String KEY = "sk-super-secret-key-12345";
    private static final String VALID = """
            {"agentName":"","status":"SUCCESS","summary":"ok","structuredOutput":{"goals":["a"]},
             "assumptions":["x"],"risks":[],"openQuestions":[],"recommendedNextActions":["go"],
             "confidence":0.8,"confidenceNote":"n","referencesToInputs":["REQUIREMENTS_ANALYSIS:v1"],
             "simulated":true}""";

    private HttpServer server;
    private int status;
    private String responseBody;
    private long delayMs;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        status = 200;
        responseBody = VALID;
        delayMs = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            requestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            contentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            try {
                ex.sendResponseHeaders(status, bytes.length);
                ex.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // client gave up (timeout test)
            } finally {
                ex.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/agent";
    }

    private LlmAgentProvider provider(String key, long timeoutMs) {
        return new LlmAgentProvider(url(), key, "test-model", timeoutMs);
    }

    private static AgentInput input() {
        return new AgentInput("REQUIREMENTS_ANALYSIS", "GREENFIELD", "raw text", "normalized text",
                Map.of(), List.of("no-secrets"));
    }

    private AgentResult run(LlmAgentProvider p) {
        return p.forStage("REQUIREMENTS_ANALYSIS").run(input());
    }

    @Test
    void validResponseBecomesNonSimulatedResult() {
        AgentResult r = run(provider(KEY, 5000));
        assertFalse(r.simulated());
        assertEquals(AgentStatus.SUCCESS, r.status());
        assertEquals("LlmRequirementsAnalysisAgent", r.agentName());
        assertEquals("ok", r.summary());
        assertEquals(0.8, r.confidence());
        assertEquals(List.of("REQUIREMENTS_ANALYSIS:v1"), r.referencesToInputs());
        assertEquals(List.of("a"), r.structuredOutput().get("goals"));
    }

    @Test
    void requestCarriesStageInputModelAndBearerOnlyWithKey() {
        run(provider(KEY, 5000));
        assertEquals("Bearer " + KEY, authHeader.get());
        assertEquals("application/json", contentType.get());
        String body = requestBody.get();
        assertTrue(body.contains("\"stage\":\"REQUIREMENTS_ANALYSIS\""));
        assertTrue(body.contains("\"model\":\"test-model\""));
        assertTrue(body.contains("normalized text"));
        assertTrue(body.contains("\"contract\""));

        run(provider("", 5000));
        assertNull(authHeader.get());
    }

    @Test
    void serverErrorsAndRateLimitsAreTransient() {
        status = 500;
        assertThrows(TransientFailureException.class, () -> run(provider(KEY, 5000)));
        status = 429;
        assertThrows(TransientFailureException.class, () -> run(provider(KEY, 5000)));
    }

    @Test
    void connectionRefusedIsTransient() {
        int port = server.getAddress().getPort();
        server.stop(0);
        LlmAgentProvider p = new LlmAgentProvider("http://127.0.0.1:" + port + "/x", KEY, "m", 2000);
        assertThrows(TransientFailureException.class, () -> run(p));
    }

    @Test
    void timeoutIsTransient() {
        delayMs = 1500;
        assertThrows(TransientFailureException.class, () -> run(provider(KEY, 300)));
    }

    @Test
    void clientErrorIsValidationFailure() {
        status = 400;
        assertThrows(ValidationFailureException.class, () -> run(provider(KEY, 5000)));
    }

    @Test
    void invalidBodiesAreValidationFailures() {
        for (String bad : List.of(
                "this is not json",
                "[1,2]",
                "{\"status\":\"SUCCESS\",\"summary\":\"s\",\"confidence\":0.5}",
                VALID.replace("0.8", "1.7"),
                VALID.replace("0.8", "-0.1"),
                VALID.replace("\"SUCCESS\"", "\"AMAZING\""))) {
            responseBody = bad;
            assertThrows(ValidationFailureException.class, () -> run(provider(KEY, 5000)), bad);
        }
    }

    @Test
    void oversizedContentIsCapped() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            items.append(i == 0 ? "" : ",").append("\"r").append(i).append("\"");
        }
        responseBody = VALID.replace("\"summary\":\"ok\"", "\"summary\":\"" + "x".repeat(50_000) + "\"")
                .replace("\"risks\":[]", "\"risks\":[" + items + "]");
        AgentResult r = run(provider(KEY, 5000));
        assertEquals(LlmAgentProvider.MAX_SUMMARY, r.summary().length());
        assertEquals(LlmAgentProvider.MAX_LIST_ITEMS, r.risks().size());
    }

    @Test
    void apiKeyNeverLeaks() {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmAgentProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            LlmAgentProvider ok = provider(KEY, 5000);
            Agent agent = ok.forStage("REQUIREMENTS_ANALYSIS");
            StringBuilder seen = new StringBuilder(ok.toString()).append(agent);
            seen.append(agent.run(input()));

            for (int code : new int[] {500, 400}) {
                status = code;
                try {
                    agent.run(input());
                } catch (RuntimeException e) {
                    seen.append(e.getMessage());
                }
            }
            status = 200;
            responseBody = "garbage";
            try {
                agent.run(input());
            } catch (RuntimeException e) {
                seen.append(e.getMessage());
            }
            int port = server.getAddress().getPort();
            server.stop(0);
            try {
                new LlmAgentProvider("http://127.0.0.1:" + port, KEY, "m", 1000)
                        .forStage("TESTING").run(input());
            } catch (RuntimeException e) {
                seen.append(e.getMessage());
            }
            appender.list.forEach(ev -> seen.append(ev.getFormattedMessage()));
            assertFalse(seen.toString().contains(KEY));
            assertFalse(seen.toString().isEmpty());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void determinismFlags() {
        assertFalse(provider(KEY, 1000).deterministic());
        assertEquals("llm", provider(KEY, 1000).providerName());
        assertTrue(new MockAgentProvider().deterministic());
    }
}
