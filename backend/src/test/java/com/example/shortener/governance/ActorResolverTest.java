package com.example.shortener.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.common.ApiException;
import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

@Requirement("001-FR-012")
class ActorResolverTest {
    private final ActorResolver resolver = new ActorResolver();

    private MockHttpServletRequest withHeader(String value) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-Demo-User", value);
        return r;
    }

    @Test
    void defaultsToDemoUser() {
        Actor a = resolver.resolve(new MockHttpServletRequest());
        assertThat(a).isEqualTo(new Actor("demo-user", ActorType.HUMAN));
        assertThat(resolver.resolve(withHeader("  "))).isEqualTo(a);
    }

    @Test
    void headerGivesHumanActor() {
        assertThat(resolver.resolve(withHeader("alice@example.com")))
                .isEqualTo(new Actor("alice@example.com", ActorType.HUMAN));
    }

    @Test
    void agentPrefixGivesAgentActor() {
        assertThat(resolver.resolve(withHeader("agent:planner")))
                .isEqualTo(new Actor("agent:planner", ActorType.AGENT));
    }

    @Test
    void invalidIdRejected() {
        for (String bad : new String[] {"bad user", "x<script>", "a".repeat(65)}) {
            assertThatThrownBy(() -> resolver.resolve(withHeader(bad)))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.code()).isEqualTo("INVALID_ACTOR");
                    });
        }
    }
}
