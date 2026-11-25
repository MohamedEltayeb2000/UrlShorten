package com.example.shortener.orchestrator;

import com.example.shortener.governance.Approval;

/** Thrown by a stage handler when a human decision is needed before the stage may complete (clarification or approval). */
public class AwaitingDecisionException extends RuntimeException {
    private final Approval.Kind kind;
    private final String policyReason;
    private final Integer artifactVersion;
    private final String riskClass;

    public AwaitingDecisionException(String message) {
        this(Approval.Kind.CLARIFICATION, message, null, message);
    }

    public AwaitingDecisionException(Approval.Kind kind, String policyReason, Integer artifactVersion, String message) {
        this(kind, policyReason, artifactVersion, message, null);
    }

    public AwaitingDecisionException(Approval.Kind kind, String policyReason, Integer artifactVersion, String message, String riskClass) {
        super(message);
        this.kind = kind;
        this.policyReason = policyReason;
        this.artifactVersion = artifactVersion;
        this.riskClass = riskClass;
    }

    public Approval.Kind kind() { return kind; }
    public String policyReason() { return policyReason; }
    public Integer artifactVersion() { return artifactVersion; }
    /** Optional LOW/MEDIUM/HIGH; when null the approval derives it from the kind. */
    public String riskClass() { return riskClass; }
}
