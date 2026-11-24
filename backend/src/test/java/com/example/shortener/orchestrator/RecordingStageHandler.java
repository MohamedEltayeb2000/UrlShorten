package com.example.shortener.orchestrator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test double: records when every stage ran, can delay, fail, or hold a stage until a latch is released. */
public class RecordingStageHandler implements StageHandler {
    public record Event(String nodeId, long startNs, long endNs) {
        boolean overlaps(Event o) { return startNs < o.endNs && o.startNs < endNs; }
    }

    private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> executions = new ConcurrentHashMap<>();
    private final Map<String, Long> delays = new ConcurrentHashMap<>();
    private final Set<String> failing = ConcurrentHashMap.newKeySet();
    private volatile long defaultDelayMs = 100;
    private volatile String gateNode;
    private volatile CountDownLatch gate;

    public void reset() {
        events.clear();
        executions.clear();
        delays.clear();
        failing.clear();
        defaultDelayMs = 100;
        gateNode = null;
        gate = null;
    }

    public void setDefaultDelayMs(long ms) { defaultDelayMs = ms; }
    public void delay(String nodeId, long ms) { delays.put(nodeId, ms); }
    public void failOn(String nodeId) { failing.add(nodeId); }
    public CountDownLatch holdAt(String nodeId) {
        gate = new CountDownLatch(1);
        gateNode = nodeId;
        return gate;
    }

    public int executions(String nodeId) { return executions.getOrDefault(nodeId, 0); }
    public List<Event> events() { return List.copyOf(events); }
    public Event event(String nodeId) {
        return events.stream().filter(e -> e.nodeId().equals(nodeId)).findFirst().orElseThrow(
                () -> new AssertionError("stage never ran: " + nodeId));
    }

    @Override
    public void execute(StageExecution ex) throws Exception {
        long start = System.nanoTime();
        executions.merge(ex.nodeId(), 1, Integer::sum);
        try {
            if (ex.nodeId().equals(gateNode) && gate != null && !gate.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("gate never released");
            }
            Thread.sleep(delays.getOrDefault(ex.nodeId(), defaultDelayMs));
            if (failing.contains(ex.nodeId())) throw new IllegalStateException("injected failure in " + ex.nodeId());
        } finally {
            events.add(new Event(ex.nodeId(), start, System.nanoTime()));
        }
    }
}
