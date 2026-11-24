package com.example.shortener.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.agents.AgentStatus;
import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("eval")
@Requirement("002-FR-010")
@Requirement("002-FR-013")
class AgentOutputEvaluatorTest {

    private final EvaluationCriteria criteria = EvaluationCriteria.load(RepoFiles.file("eval/criteria.json"));
    private final AgentOutputEvaluator evaluator = new AgentOutputEvaluator(criteria);

    private static final AgentInput INPUT = new AgentInput("TASK_DECOMPOSITION", "GREENFIELD", "raw", "norm",
            Map.of("REQUIREMENTS_ANALYSIS", Map.of("goals", List.of("g"))), List.of());

    private static AgentResult good() {
        return new AgentResult("TaskDecomposer", AgentStatus.SUCCESS, "[SIMULATED] Split into two tasks.",
                Map.of("tasks", List.of("T1", "T2")), List.of("a"), List.of("r"), List.of(), List.of("next"),
                0.9, "note", List.of("REQUIREMENTS_ANALYSIS:v1"), true);
    }

    private OutputEvaluation run(AgentInput input, AgentResult result) {
        return evaluator.evaluate(input, result, result);
    }

    private static AgentResult with(UnaryOperator<Builder> change) {
        return change.apply(new Builder(good())).build();
    }

    private void assertOnlyFails(OutputEvaluation e, String criterionId) {
        assertThat(e.failedCriteria()).containsExactly(criterionId);
        assertThat(e.passed()).isFalse();
        assertThat(e.score()).isLessThan(1.0);
    }

    @Test
    void goodOutputPassesAllSixCriteria() {
        OutputEvaluation e = run(INPUT, good());
        assertThat(e.results()).extracting(CriterionResult::id).containsExactlyElementsOf(criteria.ids());
        assertThat(e.passed()).isTrue();
        assertThat(e.score()).isEqualTo(1.0);
    }

    @Test
    void schemaValidFailsOnBadConfidenceBlankSummaryAndMissingNote() {
        assertOnlyFails(run(INPUT, with(b -> b.confidence(1.7))), "schema_valid");
        assertOnlyFails(run(INPUT, with(b -> b.summary(" ").simulated(false))), "schema_valid");
        assertOnlyFails(run(INPUT, with(b -> b.confidence(0.5).note(""))), "schema_valid");
        assertThat(run(INPUT, with(b -> b.confidence(0.5))).passed()).isTrue();
    }

    @Test
    void stageCompleteRequiresStageKeysAndClarificationQuestions() {
        assertOnlyFails(run(INPUT, with(b -> b.structured(Map.of("other", "x")))), "stage_complete");
        AgentResult clarify = with(b -> b.status(AgentStatus.NEEDS_CLARIFICATION));
        assertOnlyFails(run(INPUT, clarify), "stage_complete");
        assertThat(run(INPUT, with(b -> b.status(AgentStatus.NEEDS_CLARIFICATION).questions(List.of("why?")))).passed())
                .isTrue();
    }

    @Test
    void stageCompleteForbidsAppliedImplementationAndSilentAmbiguity() {
        AgentInput impl = new AgentInput("IMPLEMENTATION", "GREENFIELD", "raw", "norm",
                Map.of("IMPLEMENTATION_PLAN", Map.of()), List.of());
        AgentResult applied = with(b -> b.structured(Map.of("proposal", "p", "applied", true))
                .refs(List.of("IMPLEMENTATION_PLAN:v1")));
        assertOnlyFails(run(impl, applied), "stage_complete");

        AgentInput ra = new AgentInput("REQUIREMENTS_ANALYSIS", "AMBIGUOUS", "raw", null, Map.of(), List.of());
        AgentResult silent = with(b -> b.structured(Map.of("normalizedRequirement", "n", "goals", List.of(),
                "constraints", List.of())).refs(List.of()));
        assertOnlyFails(run(ra, silent), "stage_complete");
    }

    @Test
    void upstreamConsistentChecksReferenceFormatAndStage() {
        assertOnlyFails(run(INPUT, with(b -> b.refs(List.of("ARCHITECTURE_DESIGN:v1")))), "upstream_consistent");
        assertOnlyFails(run(INPUT, with(b -> b.refs(List.of("REQUIREMENTS_ANALYSIS")))), "upstream_consistent");
        assertOnlyFails(run(INPUT, with(b -> b.refs(List.of()))), "upstream_consistent");
    }

    @Test
    void noUnsafeContentDetectsEachPatternClassWithoutEchoingSecrets() {
        String secret = "ghp_" + "a".repeat(36);
        OutputEvaluation e = run(INPUT, with(b -> b.summary("[SIMULATED] token " + secret)));
        assertOnlyFails(e, "no_unsafe_content");
        assertThat(e.results().toString()).doesNotContain(secret);

        List<String> unsafe = List.of(
                "AKIA" + "ABCDEFGHIJKLMNOP", "-----BEGIN RSA PRIVATE KEY-----", "api_key = abcdefgh1234",
                "javascript:alert(1)", "data:text/html,x", "file:///etc/passwd", "vbscript:x",
                "rm -rf /", "DROP TABLE links", "mkfs.ext4 /dev/sda", "format c:",
                "kubectl apply -f x.yaml", "terraform apply", "Deploying to production now");
        for (String text : unsafe) {
            OutputEvaluation each = run(INPUT, with(b -> b.structured(Map.of("tasks", List.of(text)))));
            assertThat(each.failedCriteria()).as(text).containsExactly("no_unsafe_content");
        }
        assertOnlyFails(run(INPUT, with(b -> b.risks(List.of("avoid rm -rf")))), "no_unsafe_content");
    }

    @Test
    void labeledSimulatedRequiresPrefixAndFallbackFlag() {
        assertOnlyFails(run(INPUT, with(b -> b.summary("Split into two tasks."))), "labeled_simulated");
        assertOnlyFails(run(INPUT, with(b -> b.status(AgentStatus.FALLBACK).simulated(false)
                .summary("Fallback"))), "labeled_simulated");
        assertThat(run(INPUT, with(b -> b.simulated(false).summary("Real output"))).passed()).isTrue();
    }

    @Test
    void deterministicRequiresIdenticalSecondRun() {
        AgentResult different = with(b -> b.summary("[SIMULATED] Something else."));
        assertOnlyFails(evaluator.evaluate(INPUT, good(), different), "deterministic");
        assertOnlyFails(evaluator.evaluate(INPUT, good(), null), "deterministic");
    }

    @Test
    void scoresAreFractionOfPassedCriteria() {
        OutputEvaluation e = run(INPUT, with(b -> b.confidence(2.0)));
        assertThat(e.score()).isEqualTo(5.0 / 6.0);
        EvaluationReport report = EvaluationReport.of("1.0", 1.0, "X", List.of(run(INPUT, good()), e));
        assertThat(report.overallScore()).isEqualTo((1.0 + 5.0 / 6.0) / 2);
        assertThat(report.passed()).isFalse();
        EvaluationReport lenient = EvaluationReport.of("1.0", 0.5, "X", List.of(run(INPUT, good()), e));
        assertThat(lenient.passed()).as("an output that failed a criterion never passes").isFalse();
    }

    /** Small mutable copy helper for the immutable AgentResult record. */
    private static final class Builder {
        AgentResult r;

        Builder(AgentResult r) {
            this.r = r;
        }

        Builder summary(String v) {
            r = new AgentResult(r.agentName(), r.status(), v, r.structuredOutput(), r.assumptions(), r.risks(),
                    r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                    r.referencesToInputs(), r.simulated());
            return this;
        }

        Builder status(AgentStatus v) {
            r = new AgentResult(r.agentName(), v, r.summary(), r.structuredOutput(), r.assumptions(), r.risks(),
                    r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                    r.referencesToInputs(), r.simulated());
            return this;
        }

        Builder structured(Map<String, Object> v) {
            r = new AgentResult(r.agentName(), r.status(), r.summary(), v, r.assumptions(), r.risks(),
                    r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                    r.referencesToInputs(), r.simulated());
            return this;
        }

        Builder risks(List<String> v) {
            r = new AgentResult(r.agentName(), r.status(), r.summary(), r.structuredOutput(), r.assumptions(), v,
                    r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                    r.referencesToInputs(), r.simulated());
            return this;
        }

        Builder questions(List<String> v) {
            r = new AgentResult(r.agentName(), r.status(), r.summary(), r.structuredOutput(), r.assumptions(),
                    r.risks(), v, r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                    r.referencesToInputs(), r.simulated());
            return this;
        }

        Builder confidence(double v) {
            r = new AgentResult(r.agentName(), r.status(), r.summary(), r.structuredOutput(), r.assumptions(),
                    r.risks(), r.openQuestions(), r.recommendedNextActions(), v, r.confidenceNote(),
                    r.referencesToInputs(), r.simulated());
            return this;
        }

        Builder note(String v) {
            r = new AgentResult(r.agentName(), r.status(), r.summary(), r.structuredOutput(), r.assumptions(),
                    r.risks(), r.openQuestions(), r.recommendedNextActions(), r.confidence(), v,
                    r.referencesToInputs(), r.simulated());
            return this;
        }

        Builder refs(List<String> v) {
            r = new AgentResult(r.agentName(), r.status(), r.summary(), r.structuredOutput(), r.assumptions(),
                    r.risks(), r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                    v, r.simulated());
            return this;
        }

        Builder simulated(boolean v) {
            r = new AgentResult(r.agentName(), r.status(), r.summary(), r.structuredOutput(), r.assumptions(),
                    r.risks(), r.openQuestions(), r.recommendedNextActions(), r.confidence(), r.confidenceNote(),
                    r.referencesToInputs(), v);
            return this;
        }

        AgentResult build() {
            return r;
        }
    }
}
