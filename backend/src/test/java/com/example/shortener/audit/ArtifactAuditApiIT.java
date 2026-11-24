package com.example.shortener.audit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.artifacts.ArtifactService;
import com.example.shortener.support.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Requirement("001-FR-005")
@Requirement("001-FR-020")
class ArtifactAuditApiIT {
    @Autowired MockMvc mvc;
    @Autowired ArtifactService artifacts;
    @Autowired AuditService audit;

    @Test
    void artifactsEndpointReturnsDocumentedShape() throws Exception {
        long run = 7_001;
        var parent = artifacts.create(run, "n1", "SPEC", "first", "agent:a", List.of(), List.of());
        artifacts.create(run, "n2", "PLAN", "second", "agent:b", List.of(parent.getId()), List.of("user:req"));
        mvc.perform(get("/api/agent-runs/{id}/artifacts", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nodeId").value("n1"))
                .andExpect(jsonPath("$[0].type").value("SPEC"))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].status").value("CURRENT"))
                .andExpect(jsonPath("$[0].checksum", hasLength(64)))
                .andExpect(jsonPath("$[0].content").value("first"))
                .andExpect(jsonPath("$[1].parentIds[0]").value(parent.getId().intValue()))
                .andExpect(jsonPath("$[1].inputRefs[0]").value("user:req"))
                .andExpect(jsonPath("$[1].createdBy").value("agent:b"))
                .andExpect(jsonPath("$[1].createdAt").exists());
    }

    @Test
    void eventsEndpointPagesAndCapsSize() throws Exception {
        long run = 7_002;
        for (int i = 0; i < 3; i++) audit.append(run, "USER", "alice", "E" + i, "d" + i, "corr-1");
        mvc.perform(get("/api/agent-runs/{id}/events", run).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].eventType").value("E0"))
                .andExpect(jsonPath("$.items[0].actorType").value("USER"))
                .andExpect(jsonPath("$.items[0].actorId").value("alice"))
                .andExpect(jsonPath("$.items[0].details").value("d0"))
                .andExpect(jsonPath("$.items[0].correlationId").value("corr-1"))
                .andExpect(jsonPath("$.items[0].occurredAt").exists());
        mvc.perform(get("/api/agent-runs/{id}/events", run).param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void unknownRunReturnsEmptyResults() throws Exception {
        mvc.perform(get("/api/agent-runs/{id}/artifacts", 999_999))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/agent-runs/{id}/events", 999_999))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }
}
