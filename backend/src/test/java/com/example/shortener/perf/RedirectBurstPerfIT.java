package com.example.shortener.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shortener.support.RepoFiles;
import com.example.shortener.support.Requirement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("perf")
@Requirement("002-FR-014")
class RedirectBurstPerfIT {

    @LocalServerPort int port;

    @Test
    void redirectBurstStaysWithinBudgetWithNoErrors() throws Exception {
        JsonNode budgets = new ObjectMapper().readTree(RepoFiles.read("docs/perf/budgets.json"));
        int requests = budgets.get("redirect_requests").asInt();
        long p95Budget = budgets.get("redirect_p95_ms").asLong();

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        String base = "http://localhost:" + port;
        HttpResponse<String> created = client.send(HttpRequest.newBuilder(URI.create(base + "/api/links"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://example.com/perf\",\"alias\":\"perf-burst\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, created.statusCode());

        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Callable<long[]>> jobs = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            jobs.add(() -> {
                long t0 = System.nanoTime();
                HttpResponse<Void> r = client.send(HttpRequest.newBuilder(URI.create(base + "/perf-burst")).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                return new long[] {(System.nanoTime() - t0) / 1_000_000, r.statusCode() == 302 ? 0 : 1};
            });
        }
        List<Long> latencies = new ArrayList<>();
        int errors = 0;
        for (Future<long[]> f : pool.invokeAll(jobs)) {
            long[] r = f.get();
            latencies.add(r[0]);
            errors += (int) r[1];
        }
        pool.shutdown();
        Collections.sort(latencies);
        long p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);

        // The analytics summary must stay fast with the clicks just recorded, and every redirect must have been counted.
        long a0 = System.nanoTime();
        HttpResponse<String> summary = client.send(HttpRequest.newBuilder(URI.create(base + "/api/analytics/summary")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        long summaryMs = (System.nanoTime() - a0) / 1_000_000;
        long summaryBudget = budgets.get("analytics_summary_ms").asLong();
        assertEquals(200, summary.statusCode());
        assertTrue(new ObjectMapper().readTree(summary.body()).get("totalClicks").asLong() >= requests,
                "every redirect must have recorded a click");
        RepoFiles.recordPerf("analytics summary ms (after " + requests + " clicks)", summaryBudget, summaryMs, summaryMs <= summaryBudget);
        assertTrue(summaryMs <= summaryBudget, "analytics summary " + summaryMs + " ms exceeds " + summaryBudget + " ms");

        boolean ok = p95 <= p95Budget && errors == 0;
        RepoFiles.recordPerf("redirect p95 ms (" + requests + " requests, 16 threads)", p95Budget, p95, p95 <= p95Budget);
        RepoFiles.recordPerf("redirect errors", budgets.get("redirect_errors").asInt(), errors, errors == 0);
        assertEquals(0, errors, "redirect errors");
        assertTrue(ok, "p95 " + p95 + " ms exceeds budget " + p95Budget + " ms");
    }
}
