package com.example.shortener.artifacts;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-runs/{runId}/artifacts")
public class ArtifactController {
    private final ArtifactService service;

    public ArtifactController(ArtifactService service) {
        this.service = service;
    }

    @GetMapping
    public List<ArtifactView> list(@PathVariable long runId) {
        return service.list(runId).stream().map(ArtifactView::of).toList();
    }
}
