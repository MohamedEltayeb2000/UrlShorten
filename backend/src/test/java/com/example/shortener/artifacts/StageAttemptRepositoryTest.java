package com.example.shortener.artifacts;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.orchestrator.StageAttempt;
import com.example.shortener.orchestrator.StageAttemptRepository;
import com.example.shortener.support.Requirement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-020")
class StageAttemptRepositoryTest {
    @Autowired StageAttemptRepository repo;

    @Test
    void roundTripOrderedByAttempt() {
        long node = 6_001;
        StageAttempt second = new StageAttempt(node, 2, "RUNNING", Instant.now());
        repo.save(second);
        StageAttempt first = new StageAttempt(node, 1, "RUNNING", Instant.now());
        first.finish("FAILED", Instant.now(), "primary", false, "timeout");
        repo.save(first);
        second.finish("SUCCEEDED", Instant.now(), "fallback", true, null);
        repo.save(second);

        List<StageAttempt> found = repo.findByRunNodeIdOrderByAttemptAsc(node);
        assertEquals(2, found.size());
        assertEquals(1, found.get(0).getAttempt());
        assertEquals("timeout", found.get(0).getError());
        assertFalse(found.get(0).isFallbackUsed());
        assertEquals("SUCCEEDED", found.get(1).getStatus());
        assertTrue(found.get(1).isFallbackUsed());
        assertEquals("fallback", found.get(1).getProviderUsed());
        assertNotNull(found.get(1).getEndedAt());
    }
}
