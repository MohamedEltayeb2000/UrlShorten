package com.example.shortener.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-001")
@Requirement("001-FR-002")
class DefaultWorkflowTest {

    private final WorkflowDefinition def = DefaultWorkflow.create();

    private NodeDef node(String id) {
        return def.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void defaultWorkflowIsValid() {
        GraphValidator.validate(DefaultWorkflow.GREENFIELD);
        GraphValidator.validate(def);
    }

    @Test
    void hasEightNodes() {
        assertThat(def.nodes()).hasSize(8);
    }

    @Test
    void hasTwoParallelBranches() {
        Map<String, Integer> levels = GraphValidator.levels(def);
        assertThat(levels.get("ARCHITECTURE_DESIGN")).isEqualTo(levels.get("IMPLEMENTATION_PLAN"));
        assertThat(node("ARCHITECTURE_DESIGN").dependsOn()).containsExactly("TASK_DECOMPOSITION");
        assertThat(node("IMPLEMENTATION_PLAN").dependsOn()).containsExactly("TASK_DECOMPOSITION");
        // documentation runs alongside implementation/testing
        assertThat(node("DOCUMENTATION").dependsOn()).containsExactly("ARCHITECTURE_DESIGN");
        assertThat(levels.get("DOCUMENTATION")).isLessThan(levels.get("RELEASE_READINESS"));
    }

    @Test
    void bothJoinsHaveTwoPrerequisitesAndRequireApproval() {
        assertThat(node("IMPLEMENTATION").dependsOn())
                .containsExactlyInAnyOrder("ARCHITECTURE_DESIGN", "IMPLEMENTATION_PLAN");
        assertThat(node("RELEASE_READINESS").dependsOn()).containsExactlyInAnyOrder("TESTING", "DOCUMENTATION");
        assertThat(node("IMPLEMENTATION").requiresApproval()).isTrue();
        assertThat(node("RELEASE_READINESS").requiresApproval()).isTrue();
    }

    @Test
    void joinsSitOneLevelAfterDeepestPrerequisite() {
        Map<String, Integer> levels = GraphValidator.levels(def);
        assertThat(levels.get("IMPLEMENTATION")).isEqualTo(levels.get("ARCHITECTURE_DESIGN") + 1);
        assertThat(levels.get("RELEASE_READINESS")).isEqualTo(levels.get("TESTING") + 1);
    }

    @Test
    void topologicalOrderRespectsDependencies() {
        List<String> order = GraphValidator.topologicalOrder(def);
        for (NodeDef n : def.nodes()) {
            for (String dep : n.dependsOn()) {
                assertThat(order.indexOf(dep)).isLessThan(order.indexOf(n.id()));
            }
        }
    }
}
