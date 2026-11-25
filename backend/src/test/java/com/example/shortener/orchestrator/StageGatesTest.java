package com.example.shortener.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.support.Requirement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-031")
class StageGatesTest {

    private static AuditEvent event(String type, String details) {
        return new AuditEvent(1L, "SYSTEM", "gates", type, Instant.now(), details, null);
    }

    private static StageGates gatesWith(List<AuditEvent> events) {
        AuditService audit = mock(AuditService.class);
        when(audit.eventsOfTypes(anyLong(), anyCollection())).thenReturn(events);
        return new StageGates(audit, null, null, null, null, null);
    }

    @Test
    void allChecksPassingPassesTheGate() {
        StageGates.GateResult r = StageGates.evaluate(List.of("a", "b"), n -> true);
        assertThat(r.passed()).isTrue();
        assertThat(r.checks()).containsExactly("a: passed", "b: passed");
        assertThat(r.firstFailure()).isNull();
    }

    @Test
    void oneFailingCheckFailsTheGateAndIsNamed() {
        StageGates.GateResult r = StageGates.evaluate(List.of("a", "b", "c"), n -> !n.equals("b"));
        assertThat(r.passed()).isFalse();
        assertThat(r.checks()).containsExactly("a: passed", "b: failed", "c: passed");
        assertThat(r.firstFailure()).isEqualTo("b");
    }

    @Test
    void nodeDefDefaultsToEmptyChecksAndKeepsTheFourArgConstructor() {
        NodeDef d = new NodeDef("A", "A", List.of(), false);
        assertThat(d.entryChecks()).isEmpty();
        assertThat(d.exitChecks()).isEmpty();
        assertThat(new NodeDef("A", "A", null, false, null, null).entryChecks()).isEmpty();
    }

    @Test
    void everyDefaultWorkflowNodeDeclaresEntryAndExitChecks() {
        for (NodeDef n : DefaultWorkflow.create().nodes()) {
            assertThat(n.entryChecks()).as(n.id() + " entry").isNotEmpty();
            assertThat(n.exitChecks()).as(n.id() + " exit").isNotEmpty();
        }
    }

    @Test
    void viewIsNotEvaluatedWithoutEventsAndShowsDeclaredChecks() {
        WorkflowDefinition def = DefaultWorkflow.create();
        Map<String, StageGates.Gates> v = gatesWith(List.of()).views(1, def);
        assertThat(v).hasSize(8);
        StageGates.Gates g = v.get("TESTING");
        assertThat(g.entry().passed()).isNull();
        assertThat(g.exit().passed()).isNull();
        assertThat(g.entry().checks()).isNotEmpty();
    }

    @Test
    void viewIsDerivedFromEventsAndANewEntryResetsTheExit() {
        WorkflowDefinition def = DefaultWorkflow.create();
        StageGates gates = gatesWith(List.of(
                event("GATE_ENTRY_PASSED", "TESTING :: all prerequisites succeeded: passed"),
                event("GATE_EXIT_FAILED", "TESTING :: output stored as an artifact: failed | output passed policy: passed"),
                event("GATE_ENTRY_PASSED", "DOCUMENTATION :: all prerequisites succeeded: passed"),
                event("GATE_EXIT_PASSED", "DOCUMENTATION :: output stored as an artifact: passed"),
                event("GATE_ENTRY_FAILED", "TESTING :: all prerequisites succeeded: failed")));
        Map<String, StageGates.Gates> v = gates.views(1, def);
        assertThat(v.get("DOCUMENTATION").entry().passed()).isTrue();
        assertThat(v.get("DOCUMENTATION").exit().passed()).isTrue();
        assertThat(v.get("DOCUMENTATION").exit().checks()).containsExactly("output stored as an artifact: passed");
        assertThat(v.get("TESTING").entry().passed()).isFalse();
        assertThat(v.get("TESTING").exit().passed()).as("re-entered: exit not evaluated yet").isNull();
    }
}
