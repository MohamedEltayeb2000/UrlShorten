package com.example.shortener.security;

import static com.example.shortener.orchestrator.RunTestSupport.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shortener.orchestrator.RunService;
import com.example.shortener.support.Requirement;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Approval cannot be bypassed by ordering, replay, a forged identity, or targeting the wrong stage. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("security")
@Requirement("001-FR-011")
@Requirement("001-FR-012")
@Requirement("001-FR-014")
class GovernanceSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired RunService service;

    private long runWaitingAtPatchApproval() throws Exception {
        long id = service.create("Build a URL shortener API", "GREENFIELD").id();
        service.start(id);
        await(() -> service.detail(id).status().equals("WAITING_FOR_APPROVAL"), Duration.ofSeconds(30), "approval gate");
        return id;
    }

    private static String body(String node, String decision, String comment) {
        return "{\"nodeId\":\"" + node + "\",\"decision\":\"" + decision + "\",\"comment\":\"" + comment
                + "\",\"changeTicket\":\"CHG-1042\",\"riskJustification\":\"reviewed in test, low blast radius\"}";
    }

    @Test
    void anAgentIdentityCannotApproveAndTheStageStaysWaiting() throws Exception {
        long id = runWaitingAtPatchApproval();
        mvc.perform(post("/api/agent-runs/" + id + "/approvals").header("X-Demo-User", "agent:ImplementerAgent")
                        .contentType(MediaType.APPLICATION_JSON).content(body("IMPLEMENTATION", "APPROVE", "self")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("APPROVAL_FORBIDDEN"));
        mvc.perform(get("/api/agent-runs/" + id)).andExpect(jsonPath("$.nodes[?(@.id=='IMPLEMENTATION')].status", hasItem("WAITING_FOR_APPROVAL")));
        mvc.perform(get("/api/agent-runs/" + id)).andExpect(jsonPath("$.nodes[?(@.id=='TESTING')].status", hasItem("PENDING")));
    }

    @Test
    void approvingAStageThatIsNotWaitingOrReplayingADecisionIsRefused() throws Exception {
        long id = runWaitingAtPatchApproval();
        mvc.perform(post("/api/agent-runs/" + id + "/approvals").contentType(MediaType.APPLICATION_JSON)
                        .content(body("RELEASE_READINESS", "APPROVE", "skip ahead")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NO_PENDING_APPROVAL"));
        mvc.perform(post("/api/agent-runs/" + id + "/approvals").contentType(MediaType.APPLICATION_JSON)
                        .content(body("IMPLEMENTATION", "APPROVE", "ok"))).andExpect(status().isOk());
        mvc.perform(post("/api/agent-runs/" + id + "/approvals").contentType(MediaType.APPLICATION_JSON)
                        .content(body("IMPLEMENTATION", "APPROVE", "replay")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NO_PENDING_APPROVAL"));
    }

    @Test
    void malformedIdentitiesAndDecisionsAreRejected() throws Exception {
        long id = runWaitingAtPatchApproval();
        mvc.perform(post("/api/agent-runs/" + id + "/approvals").header("X-Demo-User", "evil user; DROP TABLE")
                        .contentType(MediaType.APPLICATION_JSON).content(body("IMPLEMENTATION", "APPROVE", "x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_ACTOR"));
        mvc.perform(post("/api/agent-runs/" + id + "/approvals").contentType(MediaType.APPLICATION_JSON)
                        .content(body("IMPLEMENTATION", "MAYBE", "x")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_APPROVAL"));
        mvc.perform(post("/api/agent-runs/" + id + "/approvals").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvalOnAnUnknownRunOrStageDoesNothing() throws Exception {
        mvc.perform(post("/api/agent-runs/999999/approvals").contentType(MediaType.APPLICATION_JSON)
                        .content(body("IMPLEMENTATION", "APPROVE", "x")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/agent-runs/999999/rollback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"X\",\"artifactType\":\"DESIGN\",\"version\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/agent-runs/999999/replan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"IMPLEMENTATION\"}"))
                .andExpect(status().isNotFound());
    }
}
