package com.example.shortener.governance;

import com.example.shortener.artifacts.Artifact;
import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.audit.AuditService;
import com.example.shortener.common.ApiException;
import com.example.shortener.orchestrator.AgentStageHandler;
import com.example.shortener.orchestrator.RunControlService;
import com.example.shortener.orchestrator.RunNode;
import com.example.shortener.orchestrator.RunNodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Records human approval decisions with the approver's identity and applies them to the waiting stage. */
@Service
public class ApprovalService {
    public record ApprovalView(Long id, String nodeId, String status, String actorId, String comment, String policyReason,
                               Integer artifactVersion, Instant createdAt, Instant decidedAt, String riskClass,
                               String changeTicket, String riskJustification, String proposedCommands, String proposedMigrations) {}

    public record PendingApproval(Long approvalId, Long runId, String nodeId, String nodeName, String kind, String reason,
                                  String artifactType, Integer artifactVersion, String summary, List<String> openQuestions,
                                  List<String> assumptions, String policyReason, Instant createdAt, String riskClass,
                                  String changeTicket, String riskJustification, String proposedCommands, String proposedMigrations) {}

    public record DecisionRequest(String nodeId, String decision, String comment, Integer artifactVersion,
                                  String changeTicket, String riskJustification) {
        public DecisionRequest(String nodeId, String decision, String comment, Integer artifactVersion) {
            this(nodeId, decision, comment, artifactVersion, null, null);
        }
    }

    static final java.util.regex.Pattern TICKET = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9]*-[0-9]{1,8}");

    private final ApprovalRepository approvals;
    private final RunControlService control;
    private final ArtifactService artifacts;
    private final AuditService audit;
    private final RunNodeRepository nodes;
    private final PolicyDecisionRepository policyDecisions;
    private final ObjectMapper mapper;

    public ApprovalService(ApprovalRepository approvals, RunControlService control, ArtifactService artifacts,
                           AuditService audit, RunNodeRepository nodes, PolicyDecisionRepository policyDecisions, ObjectMapper mapper) {
        this.approvals = approvals;
        this.control = control;
        this.artifacts = artifacts;
        this.audit = audit;
        this.nodes = nodes;
        this.policyDecisions = policyDecisions;
        this.mapper = mapper;
    }

    public ApprovalView decide(long runId, DecisionRequest req, Actor actor) {
        if (req == null || req.nodeId() == null || req.nodeId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL", "nodeId is required");
        }
        boolean approve;
        if ("APPROVE".equalsIgnoreCase(req.decision())) approve = true;
        else if ("REJECT".equalsIgnoreCase(req.decision())) approve = false;
        else throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPROVAL", "decision must be APPROVE or REJECT");
        String comment = req.comment() == null ? "" : req.comment().strip();
        if (!approve && comment.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMENT_REQUIRED", "A comment is required when rejecting");
        }

        Approval pending = approvals.findFirstByRunIdAndNodeIdAndStatusOrderByIdDesc(runId, req.nodeId(), Approval.Status.PENDING)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_PENDING_APPROVAL",
                        "Nothing is waiting for approval on " + req.nodeId()));

        String type = AgentStageHandler.ARTIFACT_TYPES.get(req.nodeId());
        Optional<Artifact> artifact = type == null ? Optional.empty() : artifacts.current(runId, req.nodeId(), type);
        // Agents (and the artifact's own author) may never approve their own work.
        ApprovalRules.validateApprover(actor, artifact.map(Artifact::getCreatedBy).orElse(null));
        if (req.artifactVersion() != null && artifact.isPresent() && artifact.get().getVersion() != req.artifactVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_APPROVAL",
                    "The artifact changed (now v" + artifact.get().getVersion() + "); review it and decide again");
        }

        enrich(pending);
        String ticket = req.changeTicket() == null ? null : req.changeTicket().strip();
        String justification = req.riskJustification() == null ? null : req.riskJustification().strip();
        if (approve && "HIGH".equals(pending.getRiskClass())) {
            if (ticket == null || !TICKET.matcher(ticket).matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CHANGE_RECORD_REQUIRED",
                        "A change ticket such as CHG-1042 is required to approve a HIGH-risk change");
            }
            if (justification == null || justification.length() < 10) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CHANGE_RECORD_REQUIRED",
                        "A risk justification of at least 10 characters is required to approve a HIGH-risk change");
            }
        }
        if (approve && ticket != null && !ticket.isEmpty()) pending.recordChange(ticket, justification);
        pending.decide(approve ? Approval.Status.APPROVED : Approval.Status.REJECTED, actor.id(), comment);
        approvals.save(pending);
        if (pending.getChangeTicket() != null) {
            String j = pending.getRiskJustification() == null ? "" : pending.getRiskJustification();
            audit.append(runId, actor.type().name(), actor.id(), "CHANGE_RECORD", req.nodeId() + ": ticket " + pending.getChangeTicket()
                    + ", risk " + pending.getRiskClass() + ", justification: " + j.substring(0, Math.min(300, j.length())), null);
        }
        audit.append(runId, actor.type().name(), actor.id(), approve ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED",
                req.nodeId() + (pending.getArtifactVersion() == null ? "" : " v" + pending.getArtifactVersion()) + ": " + comment, null);
        if (approve && pending.getPolicyReason() != null && pending.getPolicyReason().startsWith("failing-tests-block-release")) {
            audit.append(runId, actor.type().name(), actor.id(), "RELEASE_OVERRIDE_APPROVED",
                    "release approved despite failing tests: " + comment, null);
            policyDecisions.save(new PolicyDecisionRecord(runId, req.nodeId(), new PolicyDecision("failing-tests-block-release",
                    PolicyOutcome.REQUIRE_APPROVAL, "override approved by " + actor.id() + ": " + comment, PolicyEngine.POLICY_VERSION)));
        }
        control.resolveWaiting(runId, req.nodeId(), approve, comment, actor);
        return view(pending);
    }

    public List<ApprovalView> forRun(long runId) {
        return approvals.findByRunIdOrderByIdAsc(runId).stream().peek(this::enrich).map(ApprovalService::view).toList();
    }

    public List<PendingApproval> pending() {
        List<PendingApproval> out = new ArrayList<>();
        for (Approval a : approvals.findByStatusOrderByIdAsc(Approval.Status.PENDING)) {
            enrich(a);
            String name = nodes.findByRunIdAndNodeId(a.getRunId(), a.getNodeId()).map(RunNode::getName).orElse(a.getNodeId());
            String type = AgentStageHandler.ARTIFACT_TYPES.get(a.getNodeId());
            Optional<Artifact> art = type == null ? Optional.empty() : artifacts.current(a.getRunId(), a.getNodeId(), type);
            String summary = null;
            List<String> questions = List.of();
            List<String> assumptions = List.of();
            if (art.isPresent()) {
                try {
                    JsonNode j = mapper.readTree(art.get().getContent());
                    summary = j.path("summary").asText(null);
                    questions = strings(j.path("openQuestions"));
                    assumptions = strings(j.path("assumptions"));
                } catch (Exception ignored) {
                    // unreadable content: show the approval without the artifact preview
                }
            }
            out.add(new PendingApproval(a.getId(), a.getRunId(), a.getNodeId(), name, a.getKind().name(),
                    a.getPolicyReason() == null ? "decision required" : a.getPolicyReason(), type,
                    art.map(Artifact::getVersion).orElse(null), summary, questions, assumptions, a.getPolicyReason(), a.getCreatedAt(),
                    a.getRiskClass(), a.getChangeTicket(), a.getRiskJustification(), a.getProposedCommands(), a.getProposedMigrations()));
        }
        return out;
    }

    private static List<String> strings(JsonNode arr) {
        List<String> l = new ArrayList<>();
        arr.forEach(n -> l.add(n.asText()));
        return l;
    }

    private static ApprovalView view(Approval a) {
        return new ApprovalView(a.getId(), a.getNodeId(), a.getStatus().name(), a.getActorId(), a.getComment(),
                a.getPolicyReason(), a.getArtifactVersion(), a.getCreatedAt(), a.getDecidedAt(), a.getRiskClass(),
                a.getChangeTicket(), a.getRiskJustification(), a.getProposedCommands(), a.getProposedMigrations());
    }

    /** Copies the stage artifact's proposed commands and migrations onto a pending approval (once) so approvers see them. */
    private void enrich(Approval a) {
        if (a.getStatus() != Approval.Status.PENDING || a.getProposedCommands() != null || a.getProposedMigrations() != null) return;
        String type = AgentStageHandler.ARTIFACT_TYPES.get(a.getNodeId());
        if (type == null) return;
        try {
            Optional<Artifact> art = artifacts.current(a.getRunId(), a.getNodeId(), type);
            if (art.isEmpty()) return;
            JsonNode so = mapper.readTree(art.get().getContent()).path("structuredOutput");
            JsonNode cmds = so.path("proposedCommands");
            JsonNode migs = so.path("proposedMigrations");
            if (!cmds.isArray() && !migs.isArray()) return;
            a.setProposals(cmds.isArray() ? mapper.writeValueAsString(cmds) : null, migs.isArray() ? mapper.writeValueAsString(migs) : null);
            approvals.save(a);
        } catch (Exception ignored) {
            // unreadable artifact: show the approval without the change details
        }
    }
}
