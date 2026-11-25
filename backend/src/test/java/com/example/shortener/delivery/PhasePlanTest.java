package com.example.shortener.delivery;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Requirement("002-FR-001")
@Requirement("002-FR-002")
@Requirement("002-FR-003")
class PhasePlanTest {
    static JsonNode phases;

    @BeforeAll
    static void load() throws Exception {
        phases = new ObjectMapper().readTree(RepoFiles.read("docs/phases.json")).get("phases");
    }

    @Test
    void definesOrderedPhasesP1ToP7WithCapabilitiesRequirementsAndExitCriteria() {
        assertEquals(7, phases.size());
        for (int i = 0; i < 7; i++) {
            JsonNode p = phases.get(i);
            assertEquals("P" + (i + 1), p.get("id").asText());
            assertTrue(p.get("capabilities").size() > 0, p.get("id") + " needs capabilities");
            assertTrue(p.get("requirements").size() > 0, p.get("id") + " needs owned requirements");
            assertTrue(p.get("exitCriteria").size() > 0, p.get("id") + " needs exit criteria");
        }
    }

    @Test
    void everyRequirementOfFeatures001And002HasExactlyOneOwner() {
        Map<String, Integer> owners = new HashMap<>();
        for (JsonNode p : phases) {
            for (JsonNode r : p.get("requirements")) owners.merge(r.asText(), 1, Integer::sum);
        }
        for (int i = 1; i <= 37; i++) assertOwned(owners, String.format("001-FR-%03d", i));
        for (int i = 1; i <= 20; i++) assertOwned(owners, String.format("002-FR-%03d", i));
        assertEquals(57, owners.size(), "no unknown requirement IDs");
    }

    private static void assertOwned(Map<String, Integer> owners, String id) {
        assertEquals(1, owners.getOrDefault(id, 0), id + " must have exactly one owning phase");
    }

    @Test
    void firstPhaseDeliversOnlyShortenerCapabilitiesIndependentOfTheAgentSystem() {
        String caps = phases.get(0).get("capabilities").toString().toLowerCase();
        for (String forbidden : List.of("agent", "orchestrat", "workflow", "approval")) {
            assertFalse(caps.contains(forbidden), "P1 must not include: " + forbidden);
        }
        assertTrue(caps.contains("redirect"));
    }

    @Test
    void shortenerProductCodeNeverDependsOnTheAgentSystem() throws Exception {
        Path base = RepoFiles.file("backend/src/main/java/com/example/shortener");
        for (String product : List.of("shortener", "analytics")) {
            Path dir = base.resolve(product);
            if (!Files.exists(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    String src = Files.readString(f);
                    assertFalse(src.matches("(?s).*import com\\.example\\.shortener\\.(orchestrator|agents|governance|artifacts|audit)\\..*"),
                            f + " must not depend on the agent system");
                }
            }
        }
    }
}
