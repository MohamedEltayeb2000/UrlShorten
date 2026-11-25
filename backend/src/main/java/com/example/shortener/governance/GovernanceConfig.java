package com.example.shortener.governance;

import com.example.shortener.orchestrator.RetryPolicy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GovernanceConfig {

    @Bean
    PolicyEngine policyEngine() {
        return new PolicyEngine();
    }

    @Bean
    RetryPolicy retryPolicy(@Value("${shortener.orchestrator.retry.max-attempts:3}") int maxAttempts,
                            @Value("${shortener.orchestrator.retry.base-ms:50}") long baseMs,
                            @Value("${shortener.orchestrator.retry.multiplier:2.0}") double multiplier,
                            @Value("${shortener.orchestrator.retry.max-ms:500}") long maxMs) {
        return new RetryPolicy(maxAttempts, Duration.ofMillis(baseMs), multiplier, Duration.ofMillis(maxMs));
    }
}
