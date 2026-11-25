package com.example.shortener.orchestrator;

import com.example.shortener.artifacts.ArtifactView;
import com.example.shortener.governance.ActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-runs/{runId}")
public class RunControlController {
    public record ReplanRequest(String nodeId, String requirement) {}

    public record RollbackRequest(String nodeId, String artifactType, int version) {}

    private final RunControlService control;
    private final ActorResolver actors;

    private final ChangeDetectionService changes;

    public RunControlController(RunControlService control, ActorResolver actors, ChangeDetectionService changes) {
        this.control = control;
        this.changes = changes;
        this.actors = actors;
    }

    @PostMapping("/nodes/{nodeId}/retry")
    public RunDtos.RunDetail retry(@PathVariable long runId, @PathVariable String nodeId, HttpServletRequest request) {
        return control.retry(runId, nodeId, actors.resolve(request));
    }

    @PostMapping("/replan")
    public RunDtos.RunDetail replan(@PathVariable long runId, @RequestBody ReplanRequest req, HttpServletRequest request) {
        return control.replan(runId, req.nodeId(), req.requirement(), actors.resolve(request));
    }

    @PutMapping("/artifacts")
    public ArtifactView edit(@PathVariable long runId, @RequestBody ChangeDetectionService.EditRequest req, HttpServletRequest request) {
        return ArtifactView.of(changes.edit(runId, req, actors.resolve(request)));
    }

    @PostMapping("/rollback")
    public ArtifactView rollback(@PathVariable long runId, @RequestBody RollbackRequest req, HttpServletRequest request) {
        return ArtifactView.of(control.rollback(runId, req.nodeId(), req.artifactType(), req.version(), actors.resolve(request)));
    }
}
