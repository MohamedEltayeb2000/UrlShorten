package com.example.shortener.agents;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.mock.BrownfieldAnalyzer;
import com.example.shortener.support.Requirement;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Requirement("001-FR-036")
class BrownfieldAnalyzerStructureTest {

    static final String BROWNFIELD = "Add idempotent link creation and prevent duplicate links when clients retry requests.";

    private static Path alt() throws URISyntaxException {
        return Paths.get(BrownfieldAnalyzerStructureTest.class.getResource("/brownfield-alt").toURI());
    }

    @Test
    void seededFixtureStructureIsDetected() {
        BrownfieldAnalyzer.Structure s = new BrownfieldAnalyzer().structure();
        assertEquals("CONTROLLER", s.roles().get("LinkController"));
        assertEquals("SERVICE", s.roles().get("LinkService"));
        assertEquals("REPOSITORY", s.roles().get("LinkRepository"));
        assertTrue(s.dependencies().get("LinkController").contains("LinkService"));
        assertTrue(s.dependencies().get("LinkService").contains("LinkRepository"));
        assertFalse(s.dependencies().get("LinkRepository").contains("LinkController"));
        assertEquals(List.of("POST /links"), s.endpoints());
        assertEquals(List.of("links"), s.tables());
    }

    @Test
    void impactedFilesDataFlowAndRisksAreDerivedFromTheStructure() {
        BrownfieldAnalyzer.Analysis a = new BrownfieldAnalyzer().analyze(BROWNFIELD);
        assertEquals(List.of("src/main/java/demo/LinkController.java", "src/main/java/demo/LinkRepository.java",
                "src/main/java/demo/LinkService.java", "src/main/resources/schema.sql"), a.impactedFiles());
        assertEquals(List.of("POST /links -> LinkController.postLink -> LinkService.createLink"
                + " -> LinkRepository.insert -> table links"), a.dataFlow());
        String risks = String.join("\n", a.compatibilityRisks());
        assertTrue(risks.contains("no uniqueness constraint on column target_url"), risks);
        assertFalse(risks.contains("uniqueness constraint on column code"), "code is UNIQUE");
        assertTrue(risks.contains("without checking for an existing row"), risks);
        assertTrue(risks.contains("retried POST /links"), risks);
        assertTrue(risks.contains("API contract: POST /links"), risks);
        assertTrue(risks.contains("Schema migration: table links"), risks);
    }

    @Test
    void unrelatedRequirementImpactsNothing() {
        BrownfieldAnalyzer.Analysis a = new BrownfieldAnalyzer().analyze("Send weekly newsletters to subscribers");
        assertTrue(a.impactedFiles().isEmpty());
        assertTrue(a.dataFlow().isEmpty());
    }

    @Test
    void risksDoNotDependOnCommentsOrTheWordIdempotent(@TempDir Path tmp) throws IOException {
        copyDir(fixture(), tmp);
        BrownfieldAnalyzer.Analysis before = new BrownfieldAnalyzer(tmp).analyze(BROWNFIELD);
        Path service = tmp.resolve("src/main/java/demo/LinkService.java");
        Files.writeString(service, Files.readString(service) + "\n// idempotent: this service is idempotent\n/* idempotent */\n");
        Path schema = tmp.resolve("src/main/resources/schema.sql");
        Files.writeString(schema, Files.readString(schema) + "\n-- idempotent idempotency\n");
        assertEquals(before, new BrownfieldAnalyzer(tmp).analyze(BROWNFIELD));
    }

    @Test
    void worksOnADifferentCodeBase() throws URISyntaxException {
        BrownfieldAnalyzer analyzer = new BrownfieldAnalyzer(alt());
        BrownfieldAnalyzer.Structure s = analyzer.structure();
        assertEquals("CONTROLLER", s.roles().get("OrderController"));
        assertEquals("SERVICE", s.roles().get("OrderManager"));
        assertEquals("REPOSITORY", s.roles().get("OrderDao"));
        assertEquals(List.of("GET /health", "POST /orders/place"), s.endpoints());
        assertEquals(List.of("purchase_orders"), s.tables());
        assertTrue(s.dependencies().get("OrderManager").contains("OrderDao"));

        BrownfieldAnalyzer.Analysis a = analyzer.analyze("Reject duplicate purchase orders");
        assertEquals(List.of("app/OrderController.java", "app/OrderDao.java", "app/OrderManager.java", "db/init.sql"),
                a.impactedFiles());
        assertEquals(List.of("POST /orders/place -> OrderController.place -> OrderManager.place -> OrderDao.countByRef"
                + " -> table purchase_orders",
                "POST /orders/place -> OrderController.place -> OrderManager.place -> OrderDao.save"
                        + " -> table purchase_orders"), a.dataFlow());
        String risks = String.join("\n", a.compatibilityRisks());
        assertFalse(risks.contains("without checking for an existing row"), "the manager looks the row up first");
        assertTrue(risks.contains("no uniqueness constraint on column total"), risks);
        assertTrue(risks.contains("API contract: POST /orders/place"), risks);
        assertFalse(a.impactedFiles().stream().anyMatch(f -> f.contains("Health")));
    }

    @Test
    void unknownDirectoryFailsWithAClearException() {
        BrownfieldAnalyzer analyzer = new BrownfieldAnalyzer(Path.of("does-not-exist-" + System.nanoTime()));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> analyzer.analyze("anything"));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    void configuredPathIsHonoured() throws URISyntaxException {
        String key = BrownfieldAnalyzer.PATH_PROPERTY;
        String old = System.getProperty(key);
        try {
            System.setProperty(key, alt().toString());
            assertEquals(List.of("GET /health", "POST /orders/place"),
                    BrownfieldAnalyzer.fromConfiguredPath().structure().endpoints());
        } finally {
            if (old == null) System.clearProperty(key);
            else System.setProperty(key, old);
        }
        assertEquals(List.of("POST /links"), BrownfieldAnalyzer.fromConfiguredPath().structure().endpoints());
    }

    @Test
    void analysisIsDeterministic() {
        assertEquals(new BrownfieldAnalyzer().analyze(BROWNFIELD), new BrownfieldAnalyzer().analyze(BROWNFIELD));
    }

    private static Path fixture() {
        Path d = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(d.resolve("fixtures/brownfield"))) d = d.getParent();
        return d.resolve("fixtures/brownfield");
    }

    private static void copyDir(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.sorted(Comparator.naturalOrder()).toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t);
            }
        }
    }
}
