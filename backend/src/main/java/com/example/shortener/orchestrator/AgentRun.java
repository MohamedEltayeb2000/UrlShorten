package com.example.shortener.orchestrator;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_run")
public class AgentRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_name", length = 64)
    private String workflowName;

    @Column(name = "scenario_type", length = 32)
    private String scenarioType;

    @Column(name = "raw_requirement", nullable = false, length = 4000)
    private String rawRequirement;

    @Column(name = "normalized_requirement", length = 4000)
    private String normalizedRequirement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status = RunStatus.CREATED;

    @Column(name = "plan_version", nullable = false)
    private int planVersion = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AgentRun() {}

    public AgentRun(String workflowName, String scenarioType, String rawRequirement, String normalizedRequirement) {
        this.workflowName = workflowName;
        this.scenarioType = scenarioType;
        this.rawRequirement = rawRequirement;
        this.normalizedRequirement = normalizedRequirement;
    }

    public Long getId() { return id; }
    public String getWorkflowName() { return workflowName; }
    public String getScenarioType() { return scenarioType; }
    public String getRawRequirement() { return rawRequirement; }
    public String getNormalizedRequirement() { return normalizedRequirement; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public int getPlanVersion() { return planVersion; }
    public void setPlanVersion(int planVersion) { this.planVersion = planVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
