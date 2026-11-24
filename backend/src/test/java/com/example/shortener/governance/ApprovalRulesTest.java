package com.example.shortener.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Requirement("001-FR-012")
@Requirement("001-FR-011")
class ApprovalRulesTest {

    @Test
    void agentApproverRejected() {
        assertThatThrownBy(() -> ApprovalRules.validateApprover(new Actor("agent:reviewer", ActorType.AGENT), "alice"))
                .isInstanceOfSatisfying(ForbiddenApprovalException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.code()).isEqualTo("APPROVAL_FORBIDDEN");
                });
    }

    @Test
    void authorCannotApproveOwnWorkCaseInsensitively() {
        assertThatThrownBy(() -> ApprovalRules.validateApprover(new Actor("Alice", ActorType.HUMAN), "alice"))
                .isInstanceOf(ForbiddenApprovalException.class);
    }

    @Test
    void differentHumanAllowed() {
        assertThatCode(() -> ApprovalRules.validateApprover(new Actor("bob", ActorType.HUMAN), "alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void blankActorIdRejected() {
        assertThatThrownBy(() -> new Actor(" ", ActorType.HUMAN)).isInstanceOf(IllegalArgumentException.class);
    }
}
