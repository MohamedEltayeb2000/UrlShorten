package com.example.shortener.summary;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.orchestrator.RunService;
import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Requirement("001-FR-022")
class SummaryApiIT {
    @Autowired MockMvc mvc;
    @Autowired RunService runs;

    @Test
    void summaryIsADownloadableMarkdownFile() throws Exception {
        long id = runs.create("Build a URL shortener API.", "GREENFIELD").id();
        String body = mvc.perform(get("/api/agent-runs/" + id + "/summary"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("text/markdown")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"engineering-summary-run-" + id + ".md\""))
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.startsWith("# Engineering summary"));
    }

    @Test
    void unknownRunUsesTheStandardErrorBody() throws Exception {
        mvc.perform(get("/api/agent-runs/999999/summary")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RUN_NOT_FOUND"));
    }
}
