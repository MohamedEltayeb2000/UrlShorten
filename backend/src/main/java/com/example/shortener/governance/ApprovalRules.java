package com.example.shortener.governance;

/** Approval invariants: agents never approve, and nobody approves their own work. */
public final class ApprovalRules {
    private ApprovalRules() {}

    public static void validateApprover(Actor approver, String artifactAuthor) {
        if (approver.type() == ActorType.AGENT) {
            throw new ForbiddenApprovalException("Agents cannot approve artifacts");
        }
        if (artifactAuthor != null && approver.id().equalsIgnoreCase(artifactAuthor)) {
            throw new ForbiddenApprovalException("An author cannot approve their own work");
        }
    }
}
