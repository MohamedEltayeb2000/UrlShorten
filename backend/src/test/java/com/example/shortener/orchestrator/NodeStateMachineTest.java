package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.NodeStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.support.Requirement;
import java.util.Set;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-003")
class NodeStateMachineTest {

    /** Expected legal moves, written independently of the production table as "FROM>TO" strings. */
    private static final Set<String> EXPECTED = Set.of(
            "PENDING>READY", "PENDING>BLOCKED", "PENDING>SKIPPED", "PENDING>CANCELLED",
            "READY>RUNNING", "READY>BLOCKED", "READY>SKIPPED", "READY>CANCELLED",
            "RUNNING>SUCCEEDED", "RUNNING>FAILED", "RUNNING>RETRYING", "RUNNING>WAITING_FOR_APPROVAL",
            "RUNNING>BLOCKED", "RUNNING>CANCELLED",
            "RETRYING>RUNNING", "RETRYING>FAILED", "RETRYING>CANCELLED",
            "WAITING_FOR_APPROVAL>SUCCEEDED", "WAITING_FOR_APPROVAL>BLOCKED", "WAITING_FOR_APPROVAL>PENDING", "WAITING_FOR_APPROVAL>CANCELLED",
            "BLOCKED>READY", "BLOCKED>SKIPPED", "BLOCKED>PENDING", "BLOCKED>CANCELLED",
            "FAILED>RETRYING", "FAILED>PENDING", "FAILED>CANCELLED",
            "SUCCEEDED>PENDING", "SKIPPED>PENDING");

    @Test
    void allHundredPairsMatchExpectedTable() {
        int checked = 0;
        for (NodeStatus from : NodeStatus.values()) {
            for (NodeStatus to : NodeStatus.values()) {
                boolean expected = EXPECTED.contains(from + ">" + to);
                assertThat(NodeStateMachine.canTransition(from, to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(expected);
                checked++;
            }
        }
        assertThat(checked).isEqualTo(100);
    }

    @Test
    void legalTransitionReturnsTarget() {
        assertThat(NodeStateMachine.transition(READY, RUNNING)).isEqualTo(RUNNING);
    }

    @Test
    void illegalTransitionThrowsWithHelpfulMessage() {
        assertThatThrownBy(() -> NodeStateMachine.transition(PENDING, SUCCEEDED))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("SUCCEEDED");
    }

    @Test
    void sameStateTransitionIsIllegal() {
        for (NodeStatus status : NodeStatus.values()) {
            assertThat(NodeStateMachine.canTransition(status, status)).as(status.name()).isFalse();
            assertThatThrownBy(() -> NodeStateMachine.transition(status, status))
                    .isInstanceOf(IllegalTransitionException.class);
        }
    }

    @Test
    void cancelledIsTerminalAndSkippedCanOnlyBeRequeued() {
        assertThat(NodeStateMachine.allowedFrom(CANCELLED)).isEmpty();
        assertThat(NodeStateMachine.allowedFrom(SKIPPED)).containsExactly(PENDING);
    }

    @Test
    void allowedFromIsUnmodifiable() {
        Set<NodeStatus> allowed = NodeStateMachine.allowedFrom(PENDING);
        assertThatThrownBy(() -> allowed.add(SUCCEEDED)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> allowed.remove(READY)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(NodeStateMachine.allowedFrom(PENDING)).containsExactlyInAnyOrder(READY, BLOCKED, SKIPPED, CANCELLED);
    }
}
