package com.example.shortener.orchestrator;

import com.example.shortener.shortener.dto.PageResponse;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-runs")
public class RunController {
    private final RunService service;

    public RunController(RunService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RunDtos.RunDetail> create(@RequestBody RunDtos.CreateRunRequest req) {
        RunDtos.RunDetail d = service.create(req.requirement(), req.scenario());
        return ResponseEntity.created(URI.create("/api/agent-runs/" + d.id())).body(d);
    }

    @GetMapping
    public PageResponse<RunDtos.RunSummary> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public RunDtos.RunDetail get(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/start")
    public RunDtos.RunDetail start(@PathVariable long id) {
        return service.start(id);
    }

    @PostMapping("/{id}/pause")
    public RunDtos.RunDetail pause(@PathVariable long id) {
        return service.pause(id);
    }
}
