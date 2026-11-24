package com.example.shortener.orchestrator;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunNodeRepository extends JpaRepository<RunNode, Long> {
    List<RunNode> findByRunIdOrderById(Long runId);

    Optional<RunNode> findByRunIdAndNodeId(Long runId, String nodeId);
}
