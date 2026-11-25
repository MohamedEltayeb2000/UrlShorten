package com.example.shortener.orchestrator;

import java.time.Instant;
import java.util.List;

/** API shapes for runs (see contracts/api-by-phase.md). */
public final class RunDtos {
    private RunDtos() {}

    public record CreateRunRequest(String requirement, String scenario) {}

    public record RunSummary(long id, String scenario, String requirement, String status, Instant createdAt) {}

    public record NodeView(String id, String name, String status, List<String> dependsOn, int level, int attempts,
                           Instant startedAt, Instant completedAt, String errorSummary,
                           StageGates.Gates gates) {}

    public record RunDetail(long id, String scenario, String requirement, String status, Instant createdAt,
                            int planVersion, Instant startedAt, Instant completedAt, List<NodeView> nodes) {}
}
