package com.example.shortener.delivery;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Verifies the gate machinery exists, follows contracts/gates.md, and fails when it should. */
@Requirement("002-FR-004")
@Requirement("002-FR-005")
@Requirement("002-FR-006")
@Requirement("002-FR-007")
@Requirement("002-FR-008")
@Requirement("002-FR-009")
@Requirement("002-FR-019")
@Requirement("002-FR-020")
class GateContractTest {

    @Test
    void allGateScriptsExist() {
        for (String s : List.of("lib", "traceability", "gate-tests", "gate-security", "gate-review", "gate-perf",
                "gate-eval", "gate-all", "publish-phase")) {
            assertTrue(Files.exists(RepoFiles.file("scripts/" + s + ".ps1")), "missing scripts/" + s + ".ps1");
        }
    }

    @Test
    void securityGateCoversSecretsDependenciesAndHostileInputAndBlocksOnNotRun() {
        String s = RepoFiles.read("scripts/gate-security.ps1");
        assertTrue(s.contains("gitleaks") || s.contains("Invoke-SecretScan"));
        assertTrue(s.contains("dependency-check"));
        assertTrue(s.contains("npm audit"));
        assertTrue(s.contains("-Dgroups=security"));
        assertTrue(s.contains("NOT_RUN"), "a tool that could not run must be reported as NOT_RUN");
    }

    @Test
    void testsGateBlocksOnRequirementsWithoutPassingTests() {
        String s = RepoFiles.read("scripts/gate-tests.ps1");
        assertTrue(s.contains("traceability.ps1"));
        assertTrue(s.contains("Blocking"));
        String t = RepoFiles.read("scripts/traceability.ps1");
        assertTrue(t.contains("MISSING"));
    }

    @Test
    void reviewGateRequiresReviewAndWalkthroughRecords() {
        String s = RepoFiles.read("scripts/gate-review.ps1");
        assertTrue(s.contains("Test-ReviewDocument") && s.contains("Test-WalkthroughDocument"));
    }

    @Test
    void publishRequiresGreenGatesConfirmedRemoteAndSecretScan() {
        String s = RepoFiles.read("scripts/publish-phase.ps1");
        assertTrue(s.contains("DryRun"));
        assertTrue(s.contains("PUBLISH_PENDING"), "without a confirmed remote the phase is committed locally only");
        assertTrue(s.contains("confirmedByOwner"), "owner must confirm repo and branch before a push");
        assertTrue(s.contains("Invoke-SecretScan"), "secret scan before commit");
        assertTrue(s.contains("docs/publish-log.md") || s.contains("publish-log.md"));
    }

    @Test
    void allowListCoversOnlyBuildOutputAndMarkedFakeFixtures() {
        String toml = RepoFiles.read(".gitleaks.toml");
        assertTrue(toml.contains("fake-secrets"));
        assertFalse(toml.contains("src/main"), "production source must never be allow-listed");
    }

    @Test
    void gateSelfTestProvesGatesFailOnBadInput() throws Exception {
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                RepoFiles.file("scripts/tests/gate-selftest.ps1").toString())
                .directory(RepoFiles.root().toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "self-test timed out");
        assertEquals(0, p.exitValue(), "gate self-test failed:\n" + out);
        JsonNode report = new ObjectMapper().readTree(RepoFiles.read("docs/gates/selftest.json"));
        assertTrue(report.get("passed").asBoolean());
        for (String check : List.of("plantedSecretDetected", "openBlockerRejected", "cleanReviewAccepted",
                "incompleteWaiverRejected", "slowWalkthroughRejected", "publishRefusedWithoutGates")) {
            assertTrue(report.get("checks").get(check).asBoolean(), check);
        }
    }

    @Test
    void recordedGateResultsFollowTheContractShape() throws Exception {
        try (var files = Files.list(RepoFiles.file("docs/gates"))) {
            for (var f : (Iterable<java.nio.file.Path>) files.filter(x -> x.getFileName().toString().matches("phase-\\d+\\.json"))::iterator) {
                JsonNode n = new ObjectMapper().readTree(Files.readString(f));
                assertTrue(n.has("phase"));
                n.get("gates").fields().forEachRemaining(e -> {
                    assertTrue(List.of("TESTS", "SECURITY", "REVIEW", "PERF", "EVAL").contains(e.getKey()));
                    assertTrue(List.of("PASS", "FAIL", "NOT_RUN", "SKIPPED").contains(e.getValue().get("outcome").asText()));
                    assertTrue(e.getValue().has("timestamp"));
                });
            }
        }
    }
}
