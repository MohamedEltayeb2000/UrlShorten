package com.example.shortener.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic policy checks. Nothing is applied, executed or deployed by default. */
public class PolicyEngine {
    public static final String POLICY_VERSION = "1.0";

    private record TextRule(String rule, Pattern pattern, String reason) {}

    private static final List<TextRule> TEXT_RULES = List.of(
            new TextRule("no-embedded-secrets", Pattern.compile("ghp_[A-Za-z0-9]{36}"), "GitHub token detected"),
            new TextRule("no-embedded-secrets", Pattern.compile("AKIA[0-9A-Z]{16}"), "Cloud access key detected"),
            new TextRule("no-embedded-secrets", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"), "Private key detected"),
            new TextRule("no-embedded-secrets",
                    Pattern.compile("(?i)(api[_-]?key|secret|password|token)[\"']?\\s*[:=]\\s*[\"']?[^\\s\"']{8,}"),
                    "Credential-like assignment detected"),
            new TextRule("no-unsafe-url-schemes", Pattern.compile("(?i)(javascript|vbscript):"), "Unsafe URL scheme detected"),
            new TextRule("no-unsafe-url-schemes", Pattern.compile("(?i)data:text/html"), "Unsafe URL scheme detected"),
            new TextRule("no-unsafe-url-schemes", Pattern.compile("(?i)file://"), "Unsafe URL scheme detected"));

    private static final List<String> DESTRUCTIVE_TOKENS = List.of(
            "rm -rf", "drop table", "drop database", "truncate", "mkfs", "format c:", "git push --force", "del /s");

    public List<PolicyDecision> evaluateText(String text) {
        List<PolicyDecision> decisions = new ArrayList<>();
        if (text != null) {
            for (TextRule r : TEXT_RULES) {
                if (r.pattern().matcher(text).find()) {
                    decisions.add(decision(r.rule(), PolicyOutcome.DENY, r.reason()));
                }
            }
        }
        if (decisions.isEmpty()) {
            decisions.add(decision("text-content-checks", PolicyOutcome.ALLOW, "No policy findings"));
        }
        return decisions;
    }

    public PolicyDecision evaluateAction(ProposedAction a) {
        return switch (a.type()) {
            case APPLY_PATCH -> decision("generated-output-is-proposal", PolicyOutcome.REQUIRE_APPROVAL,
                    "Generated output is a proposal; applying it requires human approval");
            case RUN_COMMAND -> isDestructive(a.payload())
                    ? decision("no-destructive-commands-without-approval", PolicyOutcome.DENY,
                            "Destructive command blocked; an explicit approval flow is required")
                    : decision("commands-require-approval", PolicyOutcome.REQUIRE_APPROVAL,
                            "Running commands requires approval");
            case DESTRUCTIVE_DB -> a.targetsNonTestData()
                    ? decision("destructive-db-on-real-data", PolicyOutcome.REQUIRE_APPROVAL,
                            "Destructive database change on non-test data requires approval")
                    : decision("destructive-db-on-test-data", PolicyOutcome.ALLOW,
                            "Destructive change limited to test data");
            case DEPLOY_PRODUCTION -> decision("no-production-deployment", PolicyOutcome.DENY,
                    "Production deployment is never performed by the system");
            case RELEASE_READINESS -> releaseDecision(a);
            case OTHER -> decision("default-allow", PolicyOutcome.ALLOW, "No policy restrictions apply");
        };
    }

    private PolicyDecision releaseDecision(ProposedAction a) {
        if (a.testsFailed() > 0 && !a.overrideApproved()) {
            return decision("failing-tests-block-release", PolicyOutcome.DENY,
                    a.testsFailed() + " failing test(s) block release readiness");
        }
        if (a.testsFailed() > 0) {
            return decision("failing-tests-block-release", PolicyOutcome.REQUIRE_APPROVAL,
                    "Failing tests overridden; release readiness requires approval");
        }
        return decision("release-readiness-requires-approval", PolicyOutcome.REQUIRE_APPROVAL,
                "Release readiness always requires approval");
    }

    private boolean isDestructive(String command) {
        String c = command == null ? "" : command.toLowerCase(Locale.ROOT);
        return DESTRUCTIVE_TOKENS.stream().anyMatch(c::contains);
    }

    private PolicyDecision decision(String rule, PolicyOutcome outcome, String reason) {
        return new PolicyDecision(rule, outcome, reason, POLICY_VERSION);
    }
}
