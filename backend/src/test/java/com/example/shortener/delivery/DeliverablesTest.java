package com.example.shortener.delivery;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 001-FR-030: documented run steps, tests, architecture notes, risk/trade-off notes and a demo script are delivered. */
@Requirement("001-FR-030")
class DeliverablesTest {

    private static String lower(String relative) {
        return RepoFiles.read(relative).toLowerCase(Locale.ROOT);
    }

    private static String doc(String relative) {
        Path p = RepoFiles.file(relative);
        assertTrue(Files.isRegularFile(p), relative + " must exist");
        String text = RepoFiles.read(relative);
        assertFalse(text.isBlank(), relative + " must not be empty");
        return text.toLowerCase(Locale.ROOT);
    }

    private static void assertContainsAll(String relative, String text, String... keywords) {
        for (String k : keywords) {
            assertTrue(text.contains(k.toLowerCase(Locale.ROOT)), relative + " must mention '" + k + "'");
        }
    }

    @Test
    void readmeHasPrerequisitesStartCommandsAndNoApiKeyDemoMode() {
        String readme = lower("README.md");
        assertContainsAll("README.md", readme, "prerequisites", "jdk", "node", "docker compose up",
                "mvn spring-boot:run", "npm run dev", "no ai api key");
    }

    @Test
    void envExampleAndComposeFileExistAndEnvExampleHoldsNoSecrets() throws IOException {
        assertTrue(Files.isRegularFile(RepoFiles.file("docker-compose.yml")), "docker-compose.yml must exist");
        Path env = RepoFiles.file(".env.example");
        assertTrue(Files.isRegularFile(env), ".env.example must exist");
        for (String line : Files.readAllLines(env)) {
            String t = line.strip();
            if (t.startsWith("#")) continue;
            for (String key : new String[] {"LLM_API_KEY=", "NVD_API_KEY="}) {
                if (t.startsWith(key)) {
                    assertEquals("", t.substring(key.length()).strip(), key + " must have no value in .env.example");
                }
            }
        }
        assertTrue(RepoFiles.read(".env.example").contains("LLM_API_KEY="), ".env.example documents LLM_API_KEY");
    }

    @Test
    void architectureDocumentCoversCoreMechanisms() {
        String text = doc("docs/architecture.md");
        assertContainsAll("docs/architecture.md", text, "state machine", "scheduler", "audit", "artifact", "approval",
                "policy", "mermaid");
    }

    @Test
    void scenariosDocumentCoversAllThreeScenarios() {
        String text = doc("docs/scenarios.md");
        assertContainsAll("docs/scenarios.md", text, "greenfield", "brownfield", "ambiguous");
    }

    @Test
    void risksDocumentHasLimitationsAndTradeOffs() {
        String text = doc("docs/risks-and-tradeoffs.md");
        assertContainsAll("docs/risks-and-tradeoffs.md", text, "limitations", "trade-off", "risk");
        assertTrue(text.contains("## 4. known limitations") || text.contains("# known limitations")
                || text.contains("## known limitations"), "a Known limitations section is required");
    }

    @Test
    void demoScriptCoversEveryRequiredStep() {
        String text = doc("docs/demo-script.md");
        assertContainsAll("docs/demo-script.md", text, "retry", "fallback", "pause", "re-plan", "approval", "rollback",
                "metrics", "analytics", "Brownfield", "Ambiguous");
    }

    @Test
    void apiReferenceCoversRunAndLinkEndpoints() {
        String text = doc("docs/api.md");
        assertContainsAll("docs/api.md", text, "/api/agent-runs", "/api/links", "/v3/api-docs", "swagger");
    }

    @Test
    void backendAndFrontendTestDirectoriesContainTests() throws IOException {
        Path backendTests = RepoFiles.file("backend/src/test/java");
        Path frontendTests = RepoFiles.file("frontend/tests");
        assertTrue(Files.isDirectory(backendTests), "backend test directory must exist");
        assertTrue(Files.isDirectory(frontendTests), "frontend test directory must exist");
        try (Stream<Path> files = Files.walk(backendTests)) {
            assertTrue(files.anyMatch(f -> f.getFileName().toString().endsWith("Test.java")),
                    "backend must have *Test.java files");
        }
        try (Stream<Path> files = Files.walk(frontendTests)) {
            assertTrue(files.anyMatch(f -> f.getFileName().toString().matches(".*\\.test\\.tsx?")),
                    "frontend must have *.test.ts(x) files");
        }
    }
}
