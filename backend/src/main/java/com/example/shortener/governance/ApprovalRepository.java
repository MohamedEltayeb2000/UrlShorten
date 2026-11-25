package com.example.shortener.governance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByRunIdOrderByIdAsc(Long runId);

    List<Approval> findByStatusOrderByIdAsc(Approval.Status status);

    Optional<Approval> findFirstByRunIdAndNodeIdAndStatusOrderByIdDesc(Long runId, String nodeId, Approval.Status status);
}
