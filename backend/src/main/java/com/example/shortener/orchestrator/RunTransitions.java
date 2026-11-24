package com.example.shortener.orchestrator;

import com.example.shortener.audit.AuditService;
import java.time.Instant;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The only place node state changes: validates the transition and appends the (insert-only) history row atomically. */
@Component
public class RunTransitions {
    private final RunNodeRepository nodes;
    private final NodeTransitionRepository history;
    private final AuditService audit;

    public RunTransitions(RunNodeRepository nodes, NodeTransitionRepository history, AuditService audit) {
        this.nodes = nodes;
        this.history = history;
        this.audit = audit;
    }

    @Transactional
    public RunNode move(long nodeDbId, NodeStatus to, String reason, Consumer<RunNode> mutator) {
        RunNode node = nodes.findById(nodeDbId).orElseThrow(() -> new IllegalStateException("node " + nodeDbId));
        NodeStatus from = node.getStatus();
        NodeStateMachine.transition(from, to); // throws IllegalTransitionException
        node.setStatus(to);
        if (mutator != null) mutator.accept(node);
        nodes.save(node);
        history.save(new NodeTransition(node.getId(), from, to, Instant.now(), reason));
        audit.append(node.getRunId(), "SYSTEM", "scheduler", "NODE_" + to.name(),
                node.getNodeId() + ": " + from + " -> " + to + (reason == null ? "" : " (" + reason + ")"), null);
        return node;
    }

    @Transactional
    public void recordInitial(RunNode node) {
        history.save(new NodeTransition(node.getId(), null, node.getStatus(), Instant.now(), "created"));
    }
}
