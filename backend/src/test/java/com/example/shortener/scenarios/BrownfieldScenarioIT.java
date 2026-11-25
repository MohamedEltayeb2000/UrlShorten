package com.example.shortener.scenarios;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-018")
class BrownfieldScenarioIT extends ScenarioSupport {

    @Test
    void identifiesImpactedFixtureFilesDataFlowAndProposesIdempotency() throws Exception {
        Outcome o = run("BROWNFIELD", true);
        assertEquals("SUCCEEDED", o.status());

        String analysis = artifacts.current(o.runId(), "REQUIREMENTS_ANALYSIS", "REQUIREMENT").orElseThrow().getContent();
        assertTrue(analysis.contains("LinkService.java"), "impacted files come from the seeded fixture");
        assertTrue(analysis.contains("schema.sql"));
        assertTrue(analysis.contains("impactedFiles") && analysis.contains("dataFlow") && analysis.contains("compatibilityRisks"));

        String design = artifacts.current(o.runId(), "ARCHITECTURE_DESIGN", "DESIGN").orElseThrow().getContent();
        assertTrue(design.contains("dataFlow"));

        String proposal = artifacts.current(o.runId(), "IMPLEMENTATION", "CODE_PROPOSAL").orElseThrow().getContent();
        assertTrue(proposal.contains("Idempotency-Key") && proposal.contains("\"applied\":false"));
        assertTrue(proposal.toLowerCase().contains("unique"), "proposes a unique constraint / fingerprint");
    }
}
