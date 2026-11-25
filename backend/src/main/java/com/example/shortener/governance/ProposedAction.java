package com.example.shortener.governance;

public record ProposedAction(ActionType type, String payload, boolean targetsNonTestData,
                             int testsFailed, boolean overrideApproved) {}
