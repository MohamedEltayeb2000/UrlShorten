package com.example.shortener.orchestrator;

import com.example.shortener.governance.Approval;

/** Thrown by a stage handler when a human decision is needed before the stage may complete (clarification or approval). */
public class AwaitingDecisionException extends RuntimeException {
    private final Approval.Kind kind;
    private final String policyReason;
    private final Integer artifactVersion;

    public AwaitingDecisionException(String message) {
        this(Approval.Kind.CLARIFICATION, message, null, message);
    }

    public AwaitingDecisionException(Approval.Kind kind, String policyReason, Integer artifactVersion, String message) {
        super(message);
        this.kind = kind;
        this.policyReason = policyReason;
        this.artifactVersion = artifactVersion;
    }

    public Approval.Kind kind() { return kind; }
    public String policyReason() { return policyReason; }
    public Integer artifactVersion() { return artifactVersion; }
}
