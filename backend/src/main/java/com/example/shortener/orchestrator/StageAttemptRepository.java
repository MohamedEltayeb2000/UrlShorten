package com.example.shortener.orchestrator;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageAttemptRepository extends JpaRepository<StageAttempt, Long> {
    List<StageAttempt> findByRunNodeIdOrderByAttemptAsc(Long runNodeId);
}
