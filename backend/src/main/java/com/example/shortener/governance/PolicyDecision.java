package com.example.shortener.governance;

public record PolicyDecision(String rule, PolicyOutcome outcome, String reason, String policyVersion) {}
