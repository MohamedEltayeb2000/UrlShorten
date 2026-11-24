package com.example.shortener.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-001")
@Requirement("001-FR-002")
class GraphValidatorTest {

    private static NodeDef n(String id, String... deps) {
        return new NodeDef(id, id, List.of(deps), false);
    }

    private static WorkflowDefinition wf(NodeDef... nodes) {
        return new WorkflowDefinition("test", List.of(nodes));
    }

    private static List<String> problems(WorkflowDefinition def) {
        return assertThrows(InvalidWorkflowException.class, () -> GraphValidator.validate(def)).problems();
    }

    @Test
    void validGraphPasses() {
        GraphValidator.validate(wf(n("A"), n("B", "A"), n("C", "A", "B")));
    }

    @Test
    void emptyGraphRejected() {
        assertThat(problems(wf())).hasSize(1).first().asString().contains("no nodes");
    }

    @Test
    void duplicateIdRejected() {
        assertThat(problems(wf(n("A"), n("A")))).anyMatch(p -> p.contains("duplicate") && p.contains("A"));
    }

    @Test
    void blankIdRejected() {
        assertThat(problems(wf(n(" "), n("B")))).anyMatch(p -> p.contains("blank id"));
    }

    @Test
    void unknownDependencyRejected() {
        assertThat(problems(wf(n("A", "Z")))).anyMatch(p -> p.contains("unknown") && p.contains("Z"));
    }

    @Test
    void selfDependencyRejected() {
        assertThat(problems(wf(n("A", "A")))).anyMatch(p -> p.contains("itself") && p.contains("A"));
    }

    @Test
    void twoNodeCycleRejected() {
        assertThat(problems(wf(n("A", "B"), n("B", "A")))).anyMatch(p -> p.startsWith("cycle:"));
    }

    @Test
    void threeNodeCycleReportsPath() {
        List<String> found = problems(wf(n("A", "C"), n("B", "A"), n("C", "B")));
        assertThat(found).anyMatch(p -> p.equals("cycle: A -> C -> B -> A"));
    }

    @Test
    void multipleProblemsReportedTogether() {
        List<String> found = problems(wf(n("A"), n("A"), n("B", "Z"), n("C", "C")));
        assertThat(found).hasSize(3);
    }

    @Test
    void topologicalOrderRespectsDependenciesAndDeclarationOrder() {
        WorkflowDefinition def = wf(n("D", "B", "C"), n("B", "A"), n("C", "A"), n("A"));
        assertThat(GraphValidator.topologicalOrder(def)).containsExactly("A", "B", "C", "D");
    }

    @Test
    void topologicalOrderRejectsInvalidGraph() {
        assertThrows(InvalidWorkflowException.class, () -> GraphValidator.topologicalOrder(wf(n("A", "B"), n("B", "A"))));
    }

    @Test
    void levelsUseLongestPath() {
        WorkflowDefinition def = wf(n("A"), n("B", "A"), n("C", "A"), n("D", "B", "C"), n("E", "A", "D"));
        Map<String, Integer> levels = GraphValidator.levels(def);
        assertThat(levels).containsEntry("A", 0).containsEntry("B", 1).containsEntry("C", 1)
                .containsEntry("D", 2).containsEntry("E", 3);
    }
}
