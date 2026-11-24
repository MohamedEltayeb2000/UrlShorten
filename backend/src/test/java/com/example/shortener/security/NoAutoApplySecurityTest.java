package com.example.shortener.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shortener.support.Requirement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** The application must not expose any way to execute code, mutate the repository, or deploy. */
@SpringBootTest
@ActiveProfiles("test")
@Tag("security")
@Requirement("001-FR-014")
@Requirement("001-FR-011")
class NoAutoApplySecurityTest {

    private static final List<String> FORBIDDEN =
            List.of("exec", "shell", "deploy", "apply", "run-code", "command", "git");
    // Governance endpoints that legitimately contain these words arrive in P5 behind approval.
    private static final Set<String> ALLOWED_PREFIXES = Set.of("/api/agent-runs");

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void noEndpointExecutesCodeDeploysOrWritesTheRepository() {
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            var patterns = info.getPathPatternsCondition().getPatternValues();
            for (String p : patterns) {
                if (ALLOWED_PREFIXES.stream().anyMatch(p::startsWith)) continue;
                String lower = p.toLowerCase();
                for (String bad : FORBIDDEN) {
                    assertTrue(!lower.contains(bad), "Forbidden capability exposed at " + p);
                }
            }
        }
    }
}
