package com.example.shortener.scenarios;

import com.example.shortener.support.ChangeRecords;
import static com.example.shortener.orchestrator.RunTestSupport.await;
import static com.example.shortener.orchestrator.RunTestSupport.terminal;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditEvent;
import com.example.shortener.audit.AuditService;
import com.example.shortener.governance.Actor;
import com.example.shortener.governance.ActorType;
import com.example.shortener.governance.ApprovalService;
import com.example.shortener.orchestrator.RunDtos;
import com.example.shortener.orchestrator.RunService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** Drives a scenario end to end like a human reviewer: waits at each gate, records the prompt, approves it. */
abstract class ScenarioSupport {
    static final Actor REVIEWER = new Actor("reviewer", ActorType.HUMAN);

    @Autowired protected RunService service;
    @Autowired protected ApprovalService approvals;
    @Autowired protected ArtifactService artifacts;
    @Autowired protected AuditService audit;

    /** What happened in one run, comparable between repetitions (no ids or timestamps). */
    record Outcome(long runId, String status, List<String> stages, List<String> prompts) {
        List<String> fingerprint() {
            List<String> f = new ArrayList<>(stages);
            f.addAll(prompts);
            f.add(status);
            return f;
        }
    }

    protected Outcome run(String scenario, boolean approveClarification) throws Exception {
        long id = service.create(null, scenario).id(); // the scenario's seeded requirement
        service.start(id);
        List<String> prompts = new ArrayList<>();
        for (int round = 0; round < 8; round++) {
            await(() -> {
                String s = service.detail(id).status();
                return terminal(s) || s.equals("WAITING_FOR_APPROVAL");
            }, Duration.ofSeconds(60), "run to finish or wait");
            if (terminal(service.detail(id).status())) break;
            var pending = approvals.pending().stream().filter(p -> p.runId() == id).toList();
            if (pending.isEmpty()) continue;
            boolean decided = false;
            for (var p : pending) {
                prompts.add(p.kind() + ":" + p.nodeId());
                if (p.kind().equals("CLARIFICATION") && !approveClarification) continue;
                approvals.decide(id, ChangeRecords.req(p.nodeId(), "APPROVE", "reviewed in test", p.artifactVersion()), REVIEWER);
                decided = true;
            }
            if (!decided) break;
        }
        RunDtos.RunDetail d = service.detail(id);
        List<String> stages = d.nodes().stream().map(n -> n.id() + "=" + n.status() + "/" + n.attempts()).toList();
        return new Outcome(id, d.status(), stages, prompts);
    }

    protected List<String> eventTypes(long runId) {
        return com.example.shortener.support.AuditTestSupport.types(audit, runId);
    }
}
