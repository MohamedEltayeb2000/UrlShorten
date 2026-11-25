package com.example.shortener.support;

import com.example.shortener.governance.ApprovalService.DecisionRequest;

/** Builds decisions that carry a valid change record, so HIGH-risk approvals pass change control in tests. */
public final class ChangeRecords {
    public static final String TICKET = "CHG-1042";
    public static final String JUSTIFICATION = "reviewed in test, low blast radius";

    private ChangeRecords() {}

    public static DecisionRequest req(String nodeId, String decision, String comment, Integer artifactVersion) {
        return new DecisionRequest(nodeId, decision, comment, artifactVersion, TICKET, JUSTIFICATION);
    }
}
