package com.example.shortener.orchestrator;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {
    List<AgentRun> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
