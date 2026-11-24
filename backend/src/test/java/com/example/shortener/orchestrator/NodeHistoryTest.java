package com.example.shortener.orchestrator;

import static com.example.shortener.orchestrator.NodeStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.shortener.support.Requirement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@Requirement("001-FR-003")
class NodeHistoryTest {

    @Test
    void startsWithInitialRecord() {
        NodeHistory history = new NodeHistory(PENDING);

        assertThat(history.current()).isEqualTo(PENDING);
        assertThat(history.records()).hasSize(1);
        TransitionRecord initial = history.records().get(0);
        assertThat(initial.from()).isNull();
        assertThat(initial.to()).isEqualTo(PENDING);
        assertThat(initial.at()).isNotNull();
    }

    @Test
    void keepsOrderReasonsAndTimestamps() {
        Instant before = Instant.now();
        NodeHistory history = new NodeHistory(PENDING);

        assertThat(history.apply(READY, "deps done")).isEqualTo(READY);
        history.apply(RUNNING, "picked up");
        history.apply(SUCCEEDED, "ok");

        List<TransitionRecord> records = history.records();
        assertThat(records).extracting(TransitionRecord::to).containsExactly(PENDING, READY, RUNNING, SUCCEEDED);
        assertThat(records).extracting(TransitionRecord::from).containsExactly(null, PENDING, READY, RUNNING);
        assertThat(records.subList(1, 4)).extracting(TransitionRecord::reason)
                .containsExactly("deps done", "picked up", "ok");
        Instant previous = before;
        for (TransitionRecord r : records) {
            assertThat(r.at()).isAfterOrEqualTo(previous);
            previous = r.at();
        }
        assertThat(history.current()).isEqualTo(SUCCEEDED);
    }

    @Test
    void illegalMoveIsRejectedWithoutChangingState() {
        NodeHistory history = new NodeHistory(PENDING);

        assertThatThrownBy(() -> history.apply(SUCCEEDED, "skip ahead"))
                .isInstanceOf(IllegalTransitionException.class);

        assertThat(history.current()).isEqualTo(PENDING);
        assertThat(history.records()).hasSize(1);
    }

    @Test
    void recordsReturnsUnmodifiableCopy() {
        NodeHistory history = new NodeHistory(PENDING);
        List<TransitionRecord> snapshot = history.records();

        history.apply(READY, "r");

        assertThat(snapshot).hasSize(1);
        assertThatThrownBy(() -> snapshot.add(snapshot.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void concurrentSameMoveSucceedsExactlyOnce() throws Exception {
        int threads = 8;
        NodeHistory history = new NodeHistory(READY);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        history.apply(RUNNING, "claim");
                        successes.incrementAndGet();
                    } catch (IllegalTransitionException e) {
                        rejections.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(threads - 1);
        assertThat(history.current()).isEqualTo(RUNNING);
        assertThat(history.records()).hasSize(2);
    }
}
