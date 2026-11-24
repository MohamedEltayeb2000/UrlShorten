package com.example.shortener.evaluation;

/** Verdict for one criterion on one agent output. */
public record CriterionResult(String id, boolean passed, String reason) {}
