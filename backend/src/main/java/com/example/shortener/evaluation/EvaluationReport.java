package com.example.shortener.evaluation;

import java.util.List;

/** Result of evaluating one scenario's evaluation set against a criteria version. */
public record EvaluationReport(
        String criteriaVersion,
        double passThreshold,
        String scenario,
        List<OutputEvaluation> evaluations,
        double overallScore,
        boolean passed) {

    /** overallScore = mean of output scores; passed = overallScore >= threshold and every output passed. */
    public static EvaluationReport of(String criteriaVersion, double passThreshold, String scenario,
                                      List<OutputEvaluation> evaluations) {
        double overall = evaluations.stream().mapToDouble(OutputEvaluation::score).average().orElse(0.0);
        boolean allPassed = evaluations.stream().allMatch(OutputEvaluation::passed);
        return new EvaluationReport(criteriaVersion, passThreshold, scenario, List.copyOf(evaluations),
                overall, overall >= passThreshold && allPassed);
    }
}
