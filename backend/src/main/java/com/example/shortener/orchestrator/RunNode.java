package com.example.shortener.orchestrator;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "run_node")
public class RunNode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NodeStatus status = NodeStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "depends_on", nullable = false, length = 1000)
    private String dependsOn = "";

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    protected RunNode() {}

    public RunNode(Long runId, String nodeId, String name, List<String> dependsOn, boolean requiresApproval) {
        this.runId = runId;
        this.nodeId = nodeId;
        this.name = name;
        this.dependsOn = dependsOn == null ? "" : String.join(",", dependsOn);
        this.requiresApproval = requiresApproval;
    }

    public List<String> dependsOnIds() {
        if (dependsOn == null || dependsOn.isBlank()) return List.of();
        return Arrays.stream(dependsOn.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getNodeId() { return nodeId; }
    public String getName() { return name; }
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }
}
