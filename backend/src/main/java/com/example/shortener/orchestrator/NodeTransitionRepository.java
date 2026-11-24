package com.example.shortener.orchestrator;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeTransitionRepository extends JpaRepository<NodeTransition, Long> {
    List<NodeTransition> findByRunNodeIdOrderByIdAsc(Long runNodeId);
}
