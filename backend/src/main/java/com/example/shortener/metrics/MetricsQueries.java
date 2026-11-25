package com.example.shortener.metrics;

import com.example.shortener.orchestrator.AgentRun;
import com.example.shortener.orchestrator.NodeTransition;
import com.example.shortener.orchestrator.RunNode;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries used by {@link MetricsService}. */
public interface MetricsQueries extends Repository<AgentRun, Long> {
    @Query("select r from AgentRun r")
    List<AgentRun> allRuns();

    @Query("select n from RunNode n")
    List<RunNode> allNodes();

    /** Only the transitions that matter for MTTR, in per-node chronological order. */
    @Query("""
            select t from NodeTransition t
            where t.runNodeId in (select n.id from RunNode n where n.runId = :runId)
              and t.toStatus in (com.example.shortener.orchestrator.NodeStatus.RETRYING,
                                 com.example.shortener.orchestrator.NodeStatus.FAILED,
                                 com.example.shortener.orchestrator.NodeStatus.BLOCKED,
                                 com.example.shortener.orchestrator.NodeStatus.SUCCEEDED)
            order by t.runNodeId, t.id""")
    List<NodeTransition> recoveryTransitionsOfRun(@Param("runId") long runId);

    @Query("""
            select t from NodeTransition t
            where t.toStatus in (com.example.shortener.orchestrator.NodeStatus.RETRYING,
                                 com.example.shortener.orchestrator.NodeStatus.FAILED,
                                 com.example.shortener.orchestrator.NodeStatus.BLOCKED,
                                 com.example.shortener.orchestrator.NodeStatus.SUCCEEDED)
            order by t.runNodeId, t.id""")
    List<NodeTransition> allRecoveryTransitions();
}
