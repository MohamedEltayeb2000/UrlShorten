package com.example.shortener.artifacts;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shortener.common.ApiException;
import com.example.shortener.support.Requirement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Requirement("001-FR-005")
@Requirement("001-FR-016")
class ArtifactVersioningTest {
    private static final AtomicLong RUNS = new AtomicLong(9_000);
    @Autowired ArtifactService service;
    @Autowired ArtifactRepository repo;

    private Artifact make(long run, String content) {
        return service.create(run, "node-a", "SPEC", content, "agent:test", List.of(), List.of());
    }

    @Test
    void versionsIncrementAndPreviousBecomesSuperseded() {
        long run = RUNS.incrementAndGet();
        Artifact v1 = make(run, "one");
        Artifact v2 = make(run, "two");
        assertEquals(1, v1.getVersion());
        assertEquals(2, v2.getVersion());
        assertEquals(ArtifactStatus.CURRENT, v2.getStatus());
        assertEquals(ArtifactStatus.SUPERSEDED, repo.findById(v1.getId()).orElseThrow().getStatus());
        assertEquals(v2.getId(), service.current(run, "node-a", "SPEC").orElseThrow().getId());
    }

    @Test
    void lineageAndInputRefsRoundTrip() {
        long run = RUNS.incrementAndGet();
        Artifact parent = make(run, "p");
        Artifact child = service.create(run, "node-b", "PLAN", "c", "agent:test", List.of(parent.getId(), 77L),
                List.of("user:request", "settings:tone"));
        Artifact loaded = repo.findById(child.getId()).orElseThrow();
        ArtifactView view = ArtifactView.of(loaded);
        assertEquals(List.of(parent.getId(), 77L), view.parentIds());
        assertEquals(List.of("user:request", "settings:tone"), view.inputRefs());
        assertEquals(List.of(), ArtifactView.of(parent).parentIds());
    }

    @Test
    void checksumIsSha256OfContentAndChangesWithContent() throws Exception {
        long run = RUNS.incrementAndGet();
        Artifact a = make(run, "hello");
        Artifact b = make(run, "hello!");
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("hello".getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, a.getChecksum());
        assertNotEquals(a.getChecksum(), b.getChecksum());
    }

    @Test
    void contentAndChecksumHaveNoSetters() {
        assertTrue(Arrays.stream(Artifact.class.getMethods()).noneMatch(m -> m.getName().startsWith("set")));
        assertTrue(Arrays.stream(Artifact.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().toLowerCase().contains("content") && !m.getName().equals("getContent")));
    }

    @Test
    void markStaleOnlyTouchesGivenIds() {
        long run = RUNS.incrementAndGet();
        Artifact a = make(run, "a");
        Artifact other = service.create(run, "node-b", "PLAN", "b", "agent:test", List.of(), List.of());
        service.markStale(List.of(a.getId()));
        Artifact reloadedA = repo.findById(a.getId()).orElseThrow();
        assertEquals(ArtifactStatus.STALE, reloadedA.getStatus());
        assertEquals("a", reloadedA.getContent());
        assertEquals(ArtifactStatus.CURRENT, repo.findById(other.getId()).orElseThrow().getStatus());
    }

    @Test
    void restoreMakesOldVersionCurrentWithoutDeletingAnything() {
        long run = RUNS.incrementAndGet();
        Artifact v1 = make(run, "one");
        make(run, "two");
        Artifact restored = service.restore(run, "node-a", "SPEC", 1, "user:alice");
        assertEquals(v1.getId(), restored.getId());
        assertEquals(ArtifactStatus.CURRENT, restored.getStatus());
        List<Artifact> all = service.list(run);
        assertEquals(2, all.size());
        assertEquals(ArtifactStatus.SUPERSEDED, all.get(1).getStatus());
        assertEquals("two", all.get(1).getContent());
        assertEquals("one", all.get(0).getContent());
    }

    @Test
    void restoreOfUnknownVersionIs404AndStaleIs409() {
        long run = RUNS.incrementAndGet();
        Artifact v1 = make(run, "one");
        ApiException ex = assertThrows(ApiException.class, () -> service.restore(run, "node-a", "SPEC", 9, "u"));
        assertEquals(404, ex.status().value());
        service.markStale(List.of(v1.getId()));
        ApiException stale = assertThrows(ApiException.class, () -> service.restore(run, "node-a", "SPEC", 1, "u"));
        assertEquals(409, stale.status().value());
    }
}
