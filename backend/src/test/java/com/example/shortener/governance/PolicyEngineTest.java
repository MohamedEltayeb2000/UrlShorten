package com.example.shortener.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shortener.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-013")
@Requirement("001-FR-014")
class PolicyEngineTest {
    private final PolicyEngine engine = new PolicyEngine();

    private static ProposedAction action(ActionType type, String payload, boolean nonTest, int failed, boolean override) {
        return new ProposedAction(type, payload, nonTest, failed, override);
    }

    private PolicyDecision eval(ActionType type) {
        return engine.evaluateAction(action(type, "", false, 0, false));
    }

    @Test
    void cleanTextIsAllowedWithSingleDecision() {
        List<PolicyDecision> d = engine.evaluateText("Shorten links with a simple redirect.");
        assertThat(d).hasSize(1);
        assertThat(d.get(0).outcome()).isEqualTo(PolicyOutcome.ALLOW);
        assertThat(d.get(0).policyVersion()).isEqualTo("1.0");
    }

    @Test
    void nullTextIsAllowed() {
        assertThat(engine.evaluateText(null)).extracting(PolicyDecision::outcome).containsExactly(PolicyOutcome.ALLOW);
    }

    @Test
    void githubTokenDenied() {
        String secret = "ghp_" + "a".repeat(36);
        List<PolicyDecision> d = engine.evaluateText("token here " + secret);
        assertThat(d).anyMatch(x -> x.outcome() == PolicyOutcome.DENY);
        assertThat(d).noneMatch(x -> x.reason().contains(secret));
        assertThat(engine.evaluateText("ghp_short")).extracting(PolicyDecision::outcome)
                .containsExactly(PolicyOutcome.ALLOW);
    }

    @Test
    void awsKeyDenied() {
        String secret = "AKIA" + "ABCDEFGHIJKLMNOP";
        List<PolicyDecision> d = engine.evaluateText(secret);
        assertThat(d).extracting(PolicyDecision::outcome).containsExactly(PolicyOutcome.DENY);
        assertThat(d.get(0).reason()).doesNotContain(secret);
        assertThat(engine.evaluateText("AKIA123")).extracting(PolicyDecision::outcome)
                .containsExactly(PolicyOutcome.ALLOW);
    }

    @Test
    void privateKeyDenied() {
        assertThat(engine.evaluateText("-----BEGIN RSA PRIVATE KEY-----\nabc"))
                .extracting(PolicyDecision::outcome).contains(PolicyOutcome.DENY);
        assertThat(engine.evaluateText("-----BEGIN PUBLIC KEY-----"))
                .extracting(PolicyDecision::outcome).containsExactly(PolicyOutcome.ALLOW);
    }

    @Test
    void credentialAssignmentDenied() {
        String secret = "hunter2hunter2";
        List<PolicyDecision> d = engine.evaluateText("password = " + secret);
        assertThat(d).extracting(PolicyDecision::outcome).containsExactly(PolicyOutcome.DENY);
        assertThat(d.get(0).reason()).doesNotContain(secret);
        assertThat(engine.evaluateText("API_KEY: abcdefghijkl")).extracting(PolicyDecision::outcome)
                .containsExactly(PolicyOutcome.DENY);
        assertThat(engine.evaluateText("password = short")).extracting(PolicyDecision::outcome)
                .containsExactly(PolicyOutcome.ALLOW);
        assertThat(engine.evaluateText("the password must be rotated")).extracting(PolicyDecision::outcome)
                .containsExactly(PolicyOutcome.ALLOW);
    }

    @Test
    void unsafeUrlSchemesDenied() {
        for (String s : List.of("javascript:alert(1)", "VBScript:x", "data:text/html,<b>", "file:///etc/passwd")) {
            assertThat(engine.evaluateText("link " + s)).as(s)
                    .extracting(PolicyDecision::outcome).contains(PolicyOutcome.DENY);
        }
        assertThat(engine.evaluateText("https://example.com/page")).extracting(PolicyDecision::outcome)
                .containsExactly(PolicyOutcome.ALLOW);
    }

    @Test
    void applyPatchRequiresApproval() {
        PolicyDecision d = eval(ActionType.APPLY_PATCH);
        assertThat(d.outcome()).isEqualTo(PolicyOutcome.REQUIRE_APPROVAL);
        assertThat(d.rule()).isEqualTo("generated-output-is-proposal");
    }

    @Test
    void destructiveCommandsDenied() {
        for (String c : List.of("rm -rf /", "psql -c 'DROP TABLE x'", "DROP DATABASE prod", "truncate t",
                "mkfs.ext4 /dev/sda", "format c:", "git push --force", "del /s *.*")) {
            PolicyDecision d = engine.evaluateAction(action(ActionType.RUN_COMMAND, c, false, 0, false));
            assertThat(d.outcome()).as(c).isEqualTo(PolicyOutcome.DENY);
            assertThat(d.rule()).isEqualTo("no-destructive-commands-without-approval");
            assertThat(d.reason()).contains("approval");
        }
    }

    @Test
    void otherCommandsRequireApproval() {
        PolicyDecision d = engine.evaluateAction(action(ActionType.RUN_COMMAND, "mvn test", false, 0, false));
        assertThat(d.outcome()).isEqualTo(PolicyOutcome.REQUIRE_APPROVAL);
    }

    @Test
    void destructiveDbDependsOnTarget() {
        assertThat(engine.evaluateAction(action(ActionType.DESTRUCTIVE_DB, "", true, 0, false)).outcome())
                .isEqualTo(PolicyOutcome.REQUIRE_APPROVAL);
        assertThat(engine.evaluateAction(action(ActionType.DESTRUCTIVE_DB, "", false, 0, false)).outcome())
                .isEqualTo(PolicyOutcome.ALLOW);
    }

    @Test
    void productionDeployAlwaysDenied() {
        PolicyDecision d = engine.evaluateAction(action(ActionType.DEPLOY_PRODUCTION, "", false, 0, true));
        assertThat(d.outcome()).isEqualTo(PolicyOutcome.DENY);
        assertThat(d.rule()).isEqualTo("no-production-deployment");
    }

    @Test
    void releaseReadinessRules() {
        PolicyDecision blocked = engine.evaluateAction(action(ActionType.RELEASE_READINESS, "", false, 2, false));
        assertThat(blocked.outcome()).isEqualTo(PolicyOutcome.DENY);
        assertThat(blocked.rule()).isEqualTo("failing-tests-block-release");

        PolicyDecision overridden = engine.evaluateAction(action(ActionType.RELEASE_READINESS, "", false, 2, true));
        assertThat(overridden.outcome()).isEqualTo(PolicyOutcome.REQUIRE_APPROVAL);

        PolicyDecision green = engine.evaluateAction(action(ActionType.RELEASE_READINESS, "", false, 0, false));
        assertThat(green.outcome()).isEqualTo(PolicyOutcome.REQUIRE_APPROVAL);
    }

    @Test
    void otherActionsAllowedAndEveryDecisionCarriesVersion() {
        assertThat(eval(ActionType.OTHER).outcome()).isEqualTo(PolicyOutcome.ALLOW);
        for (ActionType t : ActionType.values()) {
            assertThat(eval(t).policyVersion()).isEqualTo(PolicyEngine.POLICY_VERSION);
        }
    }
}
