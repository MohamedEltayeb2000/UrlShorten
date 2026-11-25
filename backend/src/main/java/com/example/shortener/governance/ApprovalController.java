package com.example.shortener.governance;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApprovalController {
    private final ApprovalService service;
    private final ActorResolver actors;

    public ApprovalController(ApprovalService service, ActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping("/approvals/pending")
    public List<ApprovalService.PendingApproval> pending() {
        return service.pending();
    }

    @GetMapping("/agent-runs/{runId}/approvals")
    public List<ApprovalService.ApprovalView> forRun(@PathVariable long runId) {
        return service.forRun(runId);
    }

    @PostMapping("/agent-runs/{runId}/approvals")
    public ApprovalService.ApprovalView decide(@PathVariable long runId, @RequestBody ApprovalService.DecisionRequest req,
                                               HttpServletRequest request) {
        return service.decide(runId, req, actors.resolve(request));
    }
}
