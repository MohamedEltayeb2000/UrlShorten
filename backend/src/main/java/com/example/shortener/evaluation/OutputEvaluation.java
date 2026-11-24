package com.example.shortener.evaluation;

import java.util.List;

/** All criterion verdicts for one agent output; score = passed criteria / total criteria. */
public record OutputEvaluation(String stageId, String agentName, List<CriterionResult> results, double score, boolean passed) {

    public static OutputEvaluation of(String stageId, String agentName, List<CriterionResult> results) {
        long ok = results.stream().filter(CriterionResult::passed).count();
        double score = results.isEmpty() ? 0.0 : (double) ok / results.size();
        return new OutputEvaluation(stageId, agentName, List.copyOf(results), score, ok == results.size());
    }

    public List<String> failedCriteria() {
        return results.stream().filter(r -> !r.passed()).map(CriterionResult::id).toList();
    }
}
