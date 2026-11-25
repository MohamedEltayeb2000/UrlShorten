package com.example.shortener.agents;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.agents.mock.RequirementAnalysis;
import com.example.shortener.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-035")
class RequirementAnalysisTest {

    static final String GREENFIELD = "Build a URL shortener API with link creation, redirects, expiration, and click analytics.";
    static final String BROWNFIELD = "Add idempotent link creation and prevent duplicate links when clients retry requests.";
    static final String AMBIGUOUS = "Make URL links safer and improve analytics.";

    @Test
    void vagueTermsAreDetectedWithTheirTriggers() {
        RequirementAnalysis.Result r = RequirementAnalysis.analyze(AMBIGUOUS);
        assertTrue(r.ambiguous());
        assertEquals(List.of("safer", "improve"), r.vagueTerms());
        assertTrue(r.score() >= RequirementAnalysis.THRESHOLD);
        assertTrue(r.triggers().stream().anyMatch(t -> t.kind().equals("MISSING_ACCEPTANCE_CRITERIA")));
    }

    @Test
    void presetsAreClassifiedAsExpected() {
        assertFalse(RequirementAnalysis.analyze(GREENFIELD).ambiguous());
        assertFalse(RequirementAnalysis.analyze(BROWNFIELD).ambiguous());
        assertTrue(RequirementAnalysis.analyze(AMBIGUOUS).ambiguous());
    }

    @Test
    void measurableRequirementIsNotFlagged() {
        RequirementAnalysis.Result r = RequirementAnalysis.analyze(
                "Make redirects faster: p95 latency must stay under 50 ms for 1000 requests per second.");
        assertTrue(r.vagueTerms().isEmpty(), "a number or limit defines the quality word");
        assertFalse(r.ambiguous());
        assertTrue(r.constraints().stream().anyMatch(c -> c.contains("50 ms")));
    }

    @Test
    void veryShortRequirementIsFlaggedAsTooShort() {
        RequirementAnalysis.Result r = RequirementAnalysis.analyze("Add links");
        assertTrue(r.triggers().stream().anyMatch(t -> t.kind().equals("TOO_SHORT")));
        assertFalse(RequirementAnalysis.analyze("Add a feature flag service").ambiguous(), "short alone is not enough");
    }

    @Test
    void undefinedReferenceAndConflictAreFlagged() {
        RequirementAnalysis.Result it = RequirementAnalysis.analyze("Make it faster");
        assertTrue(it.triggers().stream().anyMatch(t -> t.kind().equals("UNDEFINED_REFERENCE")));
        assertTrue(it.ambiguous());
        RequirementAnalysis.Result conflict = RequirementAnalysis.analyze(
                "Links are public for everyone and also private for each owner on every request.");
        assertTrue(conflict.triggers().stream().anyMatch(t -> t.kind().equals("CONFLICTING_HINTS")));
        assertTrue(RequirementAnalysis.analyze("Build a URL shortener that creates links").triggers().stream()
                .noneMatch(t -> t.kind().equals("UNDEFINED_REFERENCE")), "'that' after a noun is a relative pronoun");
    }

    @Test
    void textIsNormalized() {
        assertEquals("Add links now", RequirementAnalysis.normalize("  Add\t links\u0000 \n now  "));
        assertEquals("", RequirementAnalysis.normalize(null));
        assertEquals("", RequirementAnalysis.normalize("   "));
    }

    @Test
    void goalsAndConstraintsAreExtractedFromVariedRequirements() {
        RequirementAnalysis.Result g = RequirementAnalysis.analyze(GREENFIELD);
        assertEquals(4, g.goals().size());
        assertEquals("Build", g.goals().get(0).action());
        assertTrue(g.goalTexts().contains("Build click analytics"));

        RequirementAnalysis.Result b = RequirementAnalysis.analyze(BROWNFIELD);
        assertEquals(List.of("Add idempotent link creation", "Prevent duplicate links"), b.goalTexts());
        assertTrue(b.constraints().contains("Condition: when clients retry requests"));
        assertTrue(b.actors().contains("clients"));

        RequirementAnalysis.Result c = RequirementAnalysis.analyze(
                "Allow users to create links; links must expire after 30 days and only https targets are accepted.");
        assertTrue(c.goalTexts().stream().anyMatch(t -> t.startsWith("Allow users")));
        assertTrue(c.constraints().stream().anyMatch(t -> t.contains("30 days")));
        assertTrue(c.constraints().stream().anyMatch(t -> t.toLowerCase().contains("only https")));
        assertTrue(c.actors().contains("users"));

        RequirementAnalysis.Result d = RequirementAnalysis.analyze(
                "Track click counts per link, reject targets longer than 2048 characters, and show totals to the owner.");
        assertEquals(3, d.goals().size());
        assertTrue(d.constraints().stream().anyMatch(t -> t.contains("2048")));
        assertTrue(d.actors().contains("owner"));
        assertTrue(d.domainTerms().contains("click"));

        RequirementAnalysis.Result e = RequirementAnalysis.analyze(
                "Customers need an admin report of expired links, at most 100 rows per page.");
        assertTrue(e.actors().containsAll(List.of("customers", "admin")));
        assertTrue(e.constraints().stream().anyMatch(t -> t.toLowerCase().contains("at most 100")));
        assertFalse(e.goals().isEmpty());

        RequirementAnalysis.Result f = RequirementAnalysis.analyze("Improve the system and make it more robust.");
        assertTrue(f.ambiguous());
        assertEquals(List.of("improve", "robust"), f.vagueTerms());
    }

    @Test
    void emptyInputHasNoGoalsAndIsFlagged() {
        RequirementAnalysis.Result r = RequirementAnalysis.analyze(null);
        assertTrue(r.goals().isEmpty());
        assertTrue(r.triggers().stream().anyMatch(t -> t.kind().equals("TOO_SHORT")));
    }

    @Test
    void analysisIsDeterministic() {
        for (String text : List.of(GREENFIELD, BROWNFIELD, AMBIGUOUS, "Make it better")) {
            assertEquals(RequirementAnalysis.analyze(text), RequirementAnalysis.analyze(text), text);
        }
    }
}
