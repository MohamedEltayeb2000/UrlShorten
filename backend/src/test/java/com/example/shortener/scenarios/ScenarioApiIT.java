package com.example.shortener.scenarios;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Requirement("001-FR-018")
class ScenarioApiIT {
    @Autowired MockMvc mvc;

    @Test
    void listsTheThreePresetsWithTheirSeededRequirements() throws Exception {
        mvc.perform(get("/api/scenarios")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains("GREENFIELD", "BROWNFIELD", "AMBIGUOUS")))
                .andExpect(jsonPath("$[2].requirement").value("Make URL links safer and improve analytics."))
                .andExpect(jsonPath("$[0].description", not(emptyString())));
    }

    @Test
    void launchingAScenarioWithoutARequirementUsesTheSeededOne() throws Exception {
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"brownfield\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scenario").value("BROWNFIELD"))
                .andExpect(jsonPath("$.requirement").value("Add idempotent link creation and prevent duplicate links when clients retry requests."));
    }

    @Test
    void aBlankRequirementWithoutAScenarioIsStillRejected() throws Exception {
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content("{\"requirement\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUIREMENT"));
        mvc.perform(post("/api/agent-runs").contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"nope\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_SCENARIO"));
    }
}
