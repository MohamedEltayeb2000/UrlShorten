package com.example.shortener.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.support.Requirement;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-015")
class RetryPolicyTest {
    private final RetryPolicy policy = RetryPolicy.defaults();

    @Test
    void transientRetriedUntilBound() {
        Throwable t = new TransientFailureException("timeout");
        assertThat(policy.shouldRetry(1, t)).isTrue();
        assertThat(policy.shouldRetry(2, t)).isTrue();
        assertThat(policy.shouldRetry(3, t)).isFalse();
    }

    @Test
    void validationAndPolicyFailuresNeverRetried() {
        assertThat(policy.shouldRetry(1, new ValidationFailureException("bad"))).isFalse();
        assertThat(policy.shouldRetry(1, new PolicyViolationException("denied"))).isFalse();
        assertThat(policy.shouldRetry(1, new IllegalStateException("other"))).isFalse();
        assertThat(policy.shouldRetry(1, null)).isFalse();
    }

    @Test
    void wrappedTransientCauseDetected() {
        Throwable wrapped = new RuntimeException("wrapper", new TransientFailureException("net"));
        assertThat(policy.shouldRetry(1, wrapped)).isTrue();
    }

    @Test
    void backoffGrowsAndIsCapped() {
        assertThat(policy.backoff(1)).isEqualTo(Duration.ofMillis(50));
        assertThat(policy.backoff(2)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.backoff(3)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.backoff(10)).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void invalidArgumentsRejected() {
        Duration d = Duration.ofMillis(10);
        assertThatThrownBy(() -> new RetryPolicy(0, d, 2, d)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofMillis(-1), 2, d)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, d, 0.5, d)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, d, 2, Duration.ofMillis(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.backoff(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
