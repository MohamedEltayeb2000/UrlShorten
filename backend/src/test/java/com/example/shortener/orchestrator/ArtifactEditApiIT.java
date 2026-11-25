package com.example.shortener.orchestrator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.support.Requirement;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ArtifactEditApiIT.Cfg.class)
@Requirement("001-FR-032")
class ArtifactEditApiIT {

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        RecordingStageHandler editApiHandler() {
            return new RecordingStageHandler();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired RunService service;
    @Autowired ArtifactService artifacts;

    private MockHttpServletRequestBuilder edit(long id, String user, String body) {
        MockHttpServletRequestBuilder b = put("/api/agent-runs/" + id + "/artifacts").contentType(MediaType.APPLICATION_JSON).content(body);
        return user == null ? b : b.header("X-Demo-User", user);
    }

    private static String body(String node, String type, String content, String comment) {
        return "{\"nodeId\":\"" + node + "\",\"artifactType\":\"" + type + "\",\"content\":\"" + content + "\",\"comment\":\"" + comment + "\"}";
    }

    @Test
    void humanEditReturnsTheNewVersionAndErrorsUseTheStandardBody() throws Exception {
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        artifacts.create(id, "RELEASE_READINESS", "RELEASE_CHECKLIST", "v1", "agent:x", List.of(), List.of());

        mvc.perform(edit(id, "alice", body("RELEASE_READINESS", "RELEASE_CHECKLIST", "edited", "fix")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.createdBy").value("alice"))
                .andExpect(jsonPath("$.content").value("edited"));

        mvc.perform(edit(id, "agent:ImplementerAgent", body("RELEASE_READINESS", "RELEASE_CHECKLIST", "x", "c")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("APPROVAL_FORBIDDEN"));
        mvc.perform(edit(id, "alice", body("RELEASE_READINESS", "RELEASE_CHECKLIST", "x", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMENT_REQUIRED"));
        mvc.perform(edit(id, "alice", body("RELEASE_READINESS", "RELEASE_CHECKLIST", "password = hunter2hunter2", "c")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("POLICY_DENIED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))));
        mvc.perform(edit(id, "alice", body("ARCHITECTURE_DESIGN", "DESIGN", "x", "c")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NO_CURRENT_ARTIFACT"));
        mvc.perform(edit(id, "alice", body("NOPE", "DESIGN", "x", "c")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NODE_NOT_FOUND"));
        mvc.perform(edit(id, "alice", body("RELEASE_READINESS", "RELEASE_CHECKLIST", "a".repeat(100_001), "c")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("CONTENT_TOO_LONG"));
        mvc.perform(edit(999999L, "alice", body("RELEASE_READINESS", "RELEASE_CHECKLIST", "x", "c")))
                .andExpect(status().isNotFound());
    }
}
