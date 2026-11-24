package com.example.shortener.orchestrator;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RunApiIT.Cfg.class)
@Requirement("001-FR-006")
@Requirement("001-FR-007")
class RunApiIT {

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        RecordingStageHandler apiHandler() {
            RecordingStageHandler h = new RecordingStageHandler();
            h.setDefaultDelayMs(1500); // keep runs RUNNING long enough to observe
            return h;
        }
    }

    @Autowired MockMvc mvc;

    private String create(String json) throws Exception {
        return mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }

    private static long idOf(String json) {
        int i = json.indexOf("\"id\":") + 5;
        int j = i;
        while (Character.isDigit(json.charAt(j))) j++;
        return Long.parseLong(json.substring(i, j));
    }

    @Test
    void createReturnsRunWithPendingNodesLevelsAndDependencies() throws Exception {
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"Build a URL shortener API\",\"scenario\":\"greenfield\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.scenario").value("GREENFIELD"))
                .andExpect(jsonPath("$.planVersion").value(1))
                .andExpect(jsonPath("$.nodes", hasSize(8)))
                .andExpect(jsonPath("$.nodes[?(@.id=='REQUIREMENTS_ANALYSIS')].level", hasItem(0)))
                .andExpect(jsonPath("$.nodes[?(@.id=='ARCHITECTURE_DESIGN')].level", hasItem(2)))
                .andExpect(jsonPath("$.nodes[?(@.id=='IMPLEMENTATION_PLAN')].level", hasItem(2)))
                .andExpect(jsonPath("$.nodes[?(@.id=='IMPLEMENTATION')].dependsOn[*]", hasItems("ARCHITECTURE_DESIGN", "IMPLEMENTATION_PLAN")))
                .andExpect(jsonPath("$.nodes[*].status", everyItem(is("PENDING"))));
    }

    @Test
    void listIsPaginatedNewestFirstAndGetReturnsDetail() throws Exception {
        long first = idOf(create("{\"requirement\":\"first list run\"}"));
        long second = idOf(create("{\"requirement\":\"second list run\"}"));
        mvc.perform(get("/api/agent-runs").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value((int) second))
                .andExpect(jsonPath("$.totalItems", greaterThanOrEqualTo(2)));
        mvc.perform(get("/api/agent-runs/" + first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.requirement").value("first list run"));
    }

    @Test
    void invalidInputAndUnknownRunsUseTheStandardErrorBody() throws Exception {
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content("{\"requirement\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUIREMENT"));
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"ok\",\"scenario\":\"nonsense\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_SCENARIO"));
        mvc.perform(get("/api/agent-runs/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RUN_NOT_FOUND"));
        mvc.perform(post("/api/agent-runs/999999/start")).andExpect(status().isNotFound());
    }

    @Test
    void startMovesRunToRunningIsIdempotentAndPauseStopsIt() throws Exception {
        long id = idOf(create("{\"requirement\":\"start pause run\"}"));
        mvc.perform(post("/api/agent-runs/" + id + "/pause")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RUN_NOT_PAUSABLE"));
        mvc.perform(post("/api/agent-runs/" + id + "/start")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING")).andExpect(jsonPath("$.startedAt").exists());
        mvc.perform(post("/api/agent-runs/" + id + "/start")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(post("/api/agent-runs/" + id + "/pause")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(post("/api/agent-runs/" + id + "/pause")).andExpect(status().isOk());
    }
}
