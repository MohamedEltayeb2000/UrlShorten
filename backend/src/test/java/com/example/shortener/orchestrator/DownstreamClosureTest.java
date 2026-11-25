package com.example.shortener.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.shortener.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-017")
class DownstreamClosureTest {
    private final WorkflowDefinition def = DefaultWorkflow.create();

    @Test
    void changingTheRequirementAffectsEveryStage() {
        assertThat(DownstreamClosure.of(def, "REQUIREMENTS_ANALYSIS")).hasSize(8);
    }

    @Test
    void changingTheArchitectureAffectsOnlyItsDependents() {
        assertThat(DownstreamClosure.of(def, "ARCHITECTURE_DESIGN"))
                .containsExactly("ARCHITECTURE_DESIGN", "IMPLEMENTATION", "TESTING", "DOCUMENTATION", "RELEASE_READINESS");
    }

    @Test
    void changingTheImplementationPlanDoesNotTouchArchitectureOrDocumentation() {
        assertThat(DownstreamClosure.of(def, "IMPLEMENTATION_PLAN"))
                .containsExactly("IMPLEMENTATION_PLAN", "IMPLEMENTATION", "TESTING", "RELEASE_READINESS")
                .doesNotContain("ARCHITECTURE_DESIGN", "DOCUMENTATION");
    }

    @Test
    void aLeafStageAffectsOnlyItself() {
        assertThat(DownstreamClosure.of(def, "RELEASE_READINESS")).containsExactly("RELEASE_READINESS");
    }

    @Test
    void unknownStageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DownstreamClosure.of(def, "NOPE"));
    }

    @Test
    void worksOnADiamondGraph() {
        WorkflowDefinition d = new WorkflowDefinition("diamond", List.of(
                new NodeDef("A", "A", List.of(), false), new NodeDef("B", "B", List.of("A"), false),
                new NodeDef("C", "C", List.of("A"), false), new NodeDef("D", "D", List.of("B", "C"), false)));
        assertThat(DownstreamClosure.of(d, "B")).containsExactly("B", "D");
    }
}
