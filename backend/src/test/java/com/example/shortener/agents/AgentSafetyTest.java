package com.example.shortener.agents;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-008")
@Requirement("001-FR-009")
class AgentSafetyTest {

    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("ghp_[A-Za-z0-9]{36}"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("data:text/html", Pattern.CASE_INSENSITIVE),
            Pattern.compile("file://", Pattern.CASE_INSENSITIVE),
            Pattern.compile("rm -rf"),
            Pattern.compile("DROP TABLE", Pattern.CASE_INSENSITIVE),
            Pattern.compile("kubectl apply", Pattern.CASE_INSENSITIVE),
            Pattern.compile("terraform apply", Pattern.CASE_INSENSITIVE),
            Pattern.compile("deploy(ing)? to production", Pattern.CASE_INSENSITIVE));

    @Test
    void noOutputContainsSecretsUnsafeSchemesOrDestructiveCommands() {
        for (String scenario : new String[] {"GREENFIELD", "BROWNFIELD", "AMBIGUOUS", null}) {
            for (String raw : new String[] {AgentTestSupport.LINKS_REQUIREMENT, "Add a feature flag service"}) {
                AgentTestSupport.pipeline(scenario, raw).forEach((stage, result) -> {
                    String text = result.toString();
                    for (Pattern p : FORBIDDEN) {
                        assertFalse(p.matcher(text).find(), scenario + "/" + stage + " matches " + p);
                    }
                });
            }
        }
    }
}
