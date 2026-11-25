package com.example.shortener.governance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyDecisionRepository extends JpaRepository<PolicyDecisionRecord, Long> {
    List<PolicyDecisionRecord> findByRunIdOrderByIdAsc(Long runId);
}
