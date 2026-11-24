package com.example.shortener.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shortener.agents.AgentInput;
import com.example.shortener.agents.AgentResult;
import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("eval")
@Requirement("002-FR-011")
@Requirement("002-FR-013")
class ScenarioEvalSetTest {

    private static final List<String> PASSING_SETS = List.of("greenfield", "brownfield", "ambiguous");

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final EvaluationCriteria criteria = EvaluationCriteria.load(RepoFiles.file("eval/criteria.json"));
    private final AgentOutputEvaluator evaluator = new AgentOutputEvaluator(criteria);

    private record LoadedSet(String scenario, List<EvalCase> cases, List<List<String>> expectedFailures,
                             List<String> names) {}

    private LoadedSet load(String file) throws IOException {
        JsonNode root = mapper.readTree(RepoFiles.read("eval/" + file + ".json"));
        List<EvalCase> cases = new ArrayList<>();
        List<List<String>> expected = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            AgentInput input = mapper.treeToValue(c.get("input"), AgentInput.class);
            AgentResult output = mapper.treeToValue(c.get("output"), AgentResult.class);
            AgentResult rerun = c.has("rerun") ? mapper.treeToValue(c.get("rerun"), AgentResult.class) : output;
            cases.add(new EvalCase(input, output, rerun, c.get("expectPass").asBoolean()));
            List<String> failed = new ArrayList<>();
            c.path("expectFailedCriteria").forEach(n -> failed.add(n.asText()));
            expected.add(failed);
            names.add(c.get("name").asText());
        }
        return new LoadedSet(root.get("scenario").asText(), cases, expected, names);
    }

    @Test
    void goldenSetsCoverEveryStageAndAllPass() throws IOException {
        for (String file : List.of("greenfield", "brownfield")) {
            LoadedSet set = load(file);
            assertThat(set.cases()).extracting(c -> c.input().stageId()).hasSize(8).doesNotHaveDuplicates();
        }
        for (String file : PASSING_SETS) {
            LoadedSet set = load(file);
            EvaluationReport report = evaluator.evaluateAll(set.scenario(), set.cases());
            for (int i = 0; i < report.evaluations().size(); i++) {
                OutputEvaluation e = report.evaluations().get(i);
                assertThat(e.passed()).as("%s / %s: %s", file, set.names().get(i), e.results()).isTrue();
                assertThat(set.cases().get(i).expectPass()).isTrue();
            }
            assertThat(report.overallScore()).isGreaterThanOrEqualTo(report.passThreshold());
            assertThat(report.passed()).as(file).isTrue();
        }
    }

    @Test
    void ambiguousSetAsksQuestionsAndStatesAssumptions() throws IOException {
        LoadedSet set = load("ambiguous");
        AgentResult analysis = set.cases().get(0).output();
        assertThat(analysis.status().name()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(analysis.openQuestions().size()).isGreaterThanOrEqualTo(3);
        assertThat(analysis.assumptions().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void everyDegradedCaseFailsExactlyTheExpectedCriteria() throws IOException {
        LoadedSet set = load("degraded");
        assertThat(set.cases().size()).isGreaterThanOrEqualTo(10);
        EvaluationReport report = evaluator.evaluateAll(set.scenario(), set.cases());
        for (int i = 0; i < report.evaluations().size(); i++) {
            OutputEvaluation e = report.evaluations().get(i);
            String name = set.names().get(i);
            assertThat(set.cases().get(i).expectPass()).as(name).isFalse();
            assertThat(e.failedCriteria()).as(name).containsExactlyInAnyOrderElementsOf(set.expectedFailures().get(i));
            assertThat(e.passed()).as(name).isFalse();
            assertThat(e.score()).as("degraded output must score below threshold: " + name)
                    .isLessThan(criteria.passThreshold());
        }
        assertThat(report.passed()).isFalse();
    }

    @Test
    void scoringIsDeterministic() throws IOException {
        for (String file : List.of("greenfield", "brownfield", "ambiguous", "degraded")) {
            LoadedSet first = load(file);
            LoadedSet second = load(file);
            EvaluationReport a = evaluator.evaluateAll(first.scenario(), first.cases());
            EvaluationReport b = evaluator.evaluateAll(second.scenario(), second.cases());
            assertThat(a).isEqualTo(b);
            assertThat(mapper.writeValueAsString(a)).isEqualTo(mapper.writeValueAsString(b));
        }
    }

    @Test
    void reportRecordsCriteriaVersionAndWritesLatestJson() throws IOException {
        JsonNode criteriaJson = mapper.readTree(RepoFiles.read("eval/criteria.json"));
        assertThat(criteria.version()).isEqualTo(criteriaJson.get("criteriaVersion").asText());

        List<EvaluationReport> reports = new ArrayList<>();
        for (String file : PASSING_SETS) {
            LoadedSet set = load(file);
            EvaluationReport report = evaluator.evaluateAll(set.scenario(), set.cases());
            assertThat(report.criteriaVersion()).isEqualTo(criteria.version());
            assertThat(report.passThreshold()).isEqualTo(criteria.passThreshold());
            reports.add(report);
        }

        LoadedSet degraded = load("degraded");
        boolean caughtAll = evaluator.evaluateAll(degraded.scenario(), degraded.cases()).evaluations().stream()
                .noneMatch(OutputEvaluation::passed);
        assertThat(caughtAll).isTrue();

        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("criteriaVersion", criteria.version());
        latest.put("passThreshold", criteria.passThreshold());
        latest.put("scenarios", reports);
        latest.put("degradedCaughtAll", caughtAll);

        Path out = RepoFiles.root().resolve("docs/evals/latest.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, mapper.writeValueAsString(latest), StandardCharsets.UTF_8);

        JsonNode written = mapper.readTree(Files.readString(out, StandardCharsets.UTF_8));
        assertThat(written.get("criteriaVersion").asText()).isEqualTo(criteria.version());
        assertThat(written.get("scenarios")).hasSize(3);
        assertThat(written.get("degradedCaughtAll").asBoolean()).isTrue();
    }
}
