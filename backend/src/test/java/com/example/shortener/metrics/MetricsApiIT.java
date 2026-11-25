package com.example.shortener.metrics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.orchestrator.RunService;
import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Requirement("001-FR-020")
class MetricsApiIT {
    @Autowired MockMvc mvc;
    @Autowired RunService runs;

    @Test
    void runMetricsExposeDocumentedFields() throws Exception {
        long id = runs.create("Metrics API run", null).id();
        mvc.perform(get("/api/agent-runs/" + id + "/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(id))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.endToEndMs").doesNotExist()) // null values are omitted from the JSON
                .andExpect(jsonPath("$.mttrMs").doesNotExist())
                .andExpect(jsonPath("$.stagesTotal").isNumber())
                .andExpect(jsonPath("$.stagesSucceeded").isNumber())
                .andExpect(jsonPath("$.retries").isNumber())
                .andExpect(jsonPath("$.rollbacks").isNumber())
                .andExpect(jsonPath("$.fallbacks").isNumber())
                .andExpect(jsonPath("$.failedStages").isNumber())
                .andExpect(jsonPath("$.blockedStages").isNumber())
                .andExpect(jsonPath("$.waitingStages").isNumber())
                .andExpect(jsonPath("$.approvals.approved").isNumber())
                .andExpect(jsonPath("$.approvals.rejected").isNumber())
                .andExpect(jsonPath("$.approvals.pending").isNumber())
                .andExpect(jsonPath("$.stageLatencies").isArray())
                .andExpect(jsonPath("$.stageLatencies[0].nodeId").isString())
                .andExpect(jsonPath("$.stageLatencies[0].name").isString())
                .andExpect(jsonPath("$.stageLatencies[0].attempts").isNumber());
    }

    @Test
    void overallMetricsExposeDocumentedFields() throws Exception {
        runs.create("Metrics API overall", null);
        mvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runsTotal").isNumber())
                .andExpect(jsonPath("$.runsSucceeded").isNumber())
                .andExpect(jsonPath("$.runsFailed").isNumber())
                .andExpect(jsonPath("$.runsInProgress").isNumber())
                .andExpect(jsonPath("$.retries").isNumber())
                .andExpect(jsonPath("$.rollbacks").isNumber())
                .andExpect(jsonPath("$.fallbacks").isNumber())
                .andExpect(jsonPath("$.failedStages").isNumber())
                .andExpect(jsonPath("$.blockedStages").isNumber());
    }

    @Test
    void unknownRunIs404WithStandardErrorBody() throws Exception {
        mvc.perform(get("/api/agent-runs/9999999/metrics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RUN_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isString());
    }
}
